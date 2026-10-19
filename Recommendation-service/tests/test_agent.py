from datetime import date, datetime
from decimal import Decimal
from types import SimpleNamespace
from copy import deepcopy
import io
import json
import sys
import unittest
from unittest.mock import patch

from recommendation.agent import (RecommendationAgent, OllamaClient, compact_dumps,
                                  compact_tool_results, dumps, recommendation_schema, validate_recommendation)
from recommendation.database import OracleRepository
from recommendation.metrics import MetricTools, TOOL_DESCRIPTIONS, shift_month


def dataset(count=48, end=date(2026, 9, 1)):
    snapshots = []
    for index in range(count):
        snapshots.append({"snapshot_id": index+1,
            "reporting_period": shift_month(end, index-count+1),
            "revenue": Decimal("100"), "total_receivables": Decimal("50"),
            "overdue_receivables": Decimal("20"), "credit_used": Decimal("80")})
    return {"profile": {"msme_id": 1, "business_name": "Example MSME",
        "sector": "Manufacturing", "is_active": "Y", "sanctioned_credit_limit": Decimal("100")},
        "snapshots": snapshots, "assessments": [], "indicators": []}


class MetricTests(unittest.TestCase):
    def test_flat_complete_history(self):
        tools = MetricTools(dataset(), date(2026, 9, 1))
        for result in tools.revenue_metrics().values():
            self.assertEqual(result["value"], 0)
        liquid = tools.liquidity_metrics()
        self.assertEqual(liquid["overdue_ratio_pct"]["value"], 40)
        self.assertEqual(liquid["credit_utilisation_pct"]["value"], 80)
        self.assertEqual(liquid["credit_headroom_pct"]["value"], 20)
        self.assertEqual(tools.seasonality_metrics()["revenue_volatility_12m_pct"]["value"], 0)

    def test_signed_growth_and_quarter_sum(self):
        data = dataset()
        data["snapshots"][-1]["revenue"] = Decimal("50")
        result = MetricTools(data, date(2026, 9, 1)).revenue_metrics()
        self.assertEqual(result["revenue_mom_growth_pct"]["value"], -50)
        self.assertEqual(result["revenue_yoy_growth_pct"]["value"], -50)
        self.assertAlmostEqual(result["revenue_qoq_growth_pct"]["value"], -16.6667)

    def test_tools_supply_zero_metric_meanings(self):
        tools = MetricTools(dataset(), date(2026, 9, 1))
        self.assertIn("equal YoY", tools.execute("liquidity_metrics")["receivables_vs_revenue_growth_gap_pp"]["meaning"])
        self.assertIn("No variation", tools.execute("seasonality_metrics")["revenue_volatility_12m_pct"]["meaning"])

    def test_midquarter_uses_previous_complete_quarter(self):
        result = MetricTools(dataset(end=date(2026, 8, 1)), date(2026, 8, 1)).revenue_metrics()
        self.assertEqual(result["revenue_qoq_growth_pct"]["current_end"], "2026-06-01")

    def test_missing_month_does_not_compare_adjacent_rows(self):
        data = dataset()
        data["snapshots"].pop(-2)
        result = MetricTools(data, date(2026, 9, 1)).revenue_metrics()
        self.assertIsNone(result["revenue_mom_growth_pct"]["value"])
        self.assertIsNone(result["revenue_qoq_growth_pct"]["value"])
        self.assertEqual(result["revenue_yoy_growth_pct"]["value"], 0)

    def test_zero_denominators_and_zero_current_revenue(self):
        data = dataset()
        data["snapshots"][-1]["total_receivables"] = 0
        data["snapshots"][-1]["overdue_receivables"] = 0
        data["snapshots"][-2]["revenue"] = 0
        tools = MetricTools(data, date(2026, 9, 1))
        self.assertIsNone(tools.liquidity_metrics()["overdue_ratio_pct"]["value"])
        self.assertIsNone(tools.revenue_metrics()["revenue_mom_growth_pct"]["value"])
        data["snapshots"][-2]["revenue"] = 100
        data["snapshots"][-1]["revenue"] = 0
        self.assertEqual(MetricTools(data, date(2026, 9, 1)).revenue_metrics()["revenue_mom_growth_pct"]["value"], -100)

    def test_insufficient_seasonal_history(self):
        tools = MetricTools(dataset(count=13), date(2026, 9, 1))
        self.assertIsNone(tools.seasonality_metrics()["same_month_historical_deviation_pct"]["value"])
        self.assertIsNone(tools.revenue_metrics()["revenue_ttm_growth_pct"]["value"])

    def test_duplicate_and_invalid_snapshot_fail(self):
        for mutation in ("duplicate", "negative", "invalid_overdue", "future", "nan"):
            data = dataset()
            if mutation == "duplicate":
                data["snapshots"].append(data["snapshots"][-1])
            elif mutation == "negative":
                data["snapshots"][-1]["revenue"] = -1
            elif mutation == "invalid_overdue":
                data["snapshots"][-1]["overdue_receivables"] = 100
            elif mutation == "future":
                data["snapshots"][-1]["reporting_period"] = date(2027, 1, 1)
            else:
                data["snapshots"][-1]["revenue"] = Decimal("NaN")
            with self.subTest(mutation=mutation), self.assertRaises(ValueError):
                MetricTools(data, date(2026, 9, 1))

    def test_persistence_gap_breaks_streak_and_counts_available_months(self):
        data = dataset()
        for offset in (0, -1, -3):
            data["indicators"].append({"indicator_code": "OVERDUE_RECEIVABLES",
                "reporting_period": shift_month(date(2026, 9, 1), offset),
                "calculated_value": 40, "breached_flag": "Y"})
        result = MetricTools(data, date(2026, 9, 1)).breach_persistence()
        self.assertEqual(result["overdue_receivables_consecutive_breach_months"]["value"], 2)
        self.assertEqual(result["overdue_receivables_breach_frequency_12m_pct"]["observed_months"], 3)
        self.assertIsNone(result["credit_utilisation_consecutive_breach_months"]["value"])


class FakeLLM:
    def __init__(self, selections=()):
        self.calls = []

    def generate(self, messages, schema):
        self.calls.append((deepcopy(messages), deepcopy(schema)))
        return {"summary": "Collections need RM review.", "actions": [{
            "title": "Review overdue invoices", "interpretation": "Some receivables are overdue.",
            "suggested_action": "Agree a collection schedule with the customer.",
            "evidence_metric_ids": ["liquidity_metrics.overdue_ratio_pct"]}]}


class AgentTests(unittest.TestCase):
    def test_all_tools_run_with_one_model_call_and_full_evidence_returned(self):
        tools = MetricTools(dataset(), date(2026, 9, 1))
        llm = FakeLLM()
        result = RecommendationAgent(tools, llm).run()
        self.assertEqual(result["status"], "generated")
        self.assertEqual(len(llm.calls), 1)
        self.assertEqual(result["tool_trace"], [{"step": i+1, "tool": name}
                                               for i, name in enumerate(TOOL_DESCRIPTIONS)])
        self.assertEqual(result["evidence"], {name: tools.execute(name) for name in TOOL_DESCRIPTIONS})
        self.assertIn("formula", result["evidence"]["revenue_metrics"]["revenue_yoy_growth_pct"])
        self.assertEqual(set(result), {"status", "model", "tool_trace", "evidence", "recommendation"})
        self.assertEqual(set(result["recommendation"]), {"summary", "actions", "limitations"})

    def test_compact_payload_preserves_interpretation_metadata_without_mutation(self):
        tools = MetricTools(dataset(), date(2026, 9, 1))
        full = {name: tools.execute(name) for name in TOOL_DESCRIPTIONS}
        before = deepcopy(full)
        compact = compact_tool_results(full)
        self.assertEqual(full, before)
        self.assertEqual(compact["business_context"], full["business_context"])
        for group, items in full.items():
            if group == "business_context": continue
            for key, item in items.items():
                self.assertEqual(compact[group][key], {k:v for k,v in item.items()
                                                     if k != "formula" and v is not None})
        self.assertLess(len(compact_dumps(compact)), len(dumps(full)))
        self.assertIn("comparison_start", compact["revenue_metrics"]["revenue_yoy_growth_pct"])
        self.assertIn("assumption", compact["liquidity_metrics"]["credit_utilisation_change_pp"])
        self.assertIn("reason", compact["breach_persistence"]["credit_utilisation_consecutive_breach_months"])

    def test_schema_not_duplicated_in_prompt_and_available_citations_preserved(self):
        llm = FakeLLM()
        result = RecommendationAgent(MetricTools(dataset(), date(2026, 9, 1)), llm).run()
        messages, schema = llm.calls[0]
        payload = json.loads(messages[1]["content"])
        self.assertEqual(set(payload), {"phase", "question", "tool_results"})
        self.assertEqual(payload["phase"], "recommend")
        citations = schema["properties"]["actions"]["items"]["properties"]["evidence_metric_ids"]["items"]["enum"]
        expected = sorted(f"{group}.{key}" for group, values in result["evidence"].items()
                          if group != "business_context" for key,item in values.items()
                          if item["status"] == "available")
        self.assertEqual(citations, expected)
        self.assertEqual(schema["properties"]["actions"]["maxItems"], 6)

    def test_repair_retained_and_invalid_second_output_still_fails(self):
        good = FakeLLM().generate([], {})
        bad = deepcopy(good)
        bad["actions"][0]["evidence_metric_ids"] = ["execute_sql"]
        for responses, fails in (([bad,good],False),([bad,bad],True)):
            class RepairLLM:
                def __init__(self): self.calls = 0
                def generate(self, messages, schema):
                    output = deepcopy(responses[self.calls]); self.calls += 1; return output
            llm = RepairLLM()
            agent = RecommendationAgent(MetricTools(dataset(), date(2026,9,1)),llm)
            if fails:
                with self.assertRaises(ValueError): agent.run()
            else:
                self.assertEqual(agent.run()["recommendation"]["actions"],good["actions"])
            self.assertEqual(llm.calls,2)

    def test_full_length_six_action_output_not_trimmed(self):
        output=FakeLLM().generate([], {})
        output["summary"] = "Review collections and financial trends. " * 50
        output["actions"] = [deepcopy(output["actions"][0]) for _ in range(6)]
        class LongLLM:
            def generate(self,messages,schema): return deepcopy(output)
        result=RecommendationAgent(MetricTools(dataset(),date(2026,9,1)),LongLLM()).run()
        self.assertEqual(result["recommendation"]["summary"],output["summary"])
        self.assertEqual(result["recommendation"]["actions"],output["actions"])

    def test_ollama_output_budget_context_and_options_unchanged(self):
        sample=FakeLLM().generate([], {})
        captured=[]
        class Opener:
            def open(self, request, timeout):
                captured.append(json.loads(request.data))
                return io.BytesIO(json.dumps({"message":{"content":json.dumps(sample)}}).encode())
        client=OllamaClient(SimpleNamespace(ollama_url="http://localhost:11434",model="qwen3:1.7b",ollama_timeout=180))
        client.opener=Opener()
        self.assertEqual(client.generate([], {"type":"object"}),sample)
        self.assertEqual(captured[0]["options"],{"temperature":0,"num_ctx":16384,"num_predict":1800})
        self.assertFalse(captured[0]["think"])

    def test_unknown_evidence_rejected(self):
        recommendation = FakeLLM([]).generate([], {"properties": {}})
        with self.assertRaises(ValueError):
            validate_recommendation(recommendation, {"revenue_metrics.revenue_yoy_growth_pct"})

    def test_invented_risk_label_or_numeric_prose_rejected(self):
        for summary in ("The business has moderate financial stress.", "Credit usage is 80%."):
            recommendation = FakeLLM([]).generate([], {"properties": {}})
            recommendation["summary"] = summary
            with self.subTest(summary=summary), self.assertRaises(ValueError):
                validate_recommendation(recommendation, {"liquidity_metrics.overdue_ratio_pct"})

    def test_numeric_prose_rejected_even_when_value_matches_evidence(self):
        recommendation = FakeLLM([]).generate([], {"properties": {}})
        recommendation["actions"][0]["interpretation"] = "The overdue ratio is 40%."
        available = {"liquidity_metrics.overdue_ratio_pct"}
        for number in (40, 30):
            with self.assertRaisesRegex(ValueError, r"actions\[0\].interpretation.*40"):
                validate_recommendation(recommendation, available, {"liquidity_metrics.overdue_ratio_pct": number})

    def test_schema_restricts_each_prose_field_without_restricting_citation_digits(self):
        import re
        citation = "breach_persistence.credit_utilisation_breach_frequency_12m_pct"
        schema = recommendation_schema([citation])
        fields = [schema["properties"]["summary"]] + [
            schema["properties"]["actions"]["items"]["properties"][name]
            for name in ("title", "interpretation", "suggested_action")]
        for field in fields:
            self.assertEqual(field["minLength"], 1)
            self.assertIsNotNone(re.fullmatch(field["pattern"], "Review the collection trend."))
            self.assertIsNone(re.fullmatch(field["pattern"], "Review in 30 days."))
        result = FakeLLM().generate([], {})
        result["actions"][0]["evidence_metric_ids"] = [citation]
        validate_recommendation(result, {citation}, {citation: 0})

    def test_reported_failure_lists_all_offending_fields_and_date(self):
        result = FakeLLM().generate([], {})
        result["summary"] = "Revenue grew 3.0% in September 2026; annual revenue declined 14.7%."
        result["actions"][0]["interpretation"] = "Utilisation is 82.9%, leaving 17.1% headroom."
        result["actions"][0]["suggested_action"] = "Review in 30 days."
        with self.assertRaises(ValueError) as error:
            validate_recommendation(result, {"liquidity_metrics.overdue_ratio_pct"})
        for text in ("summary", "2026", "14.7", "actions[0].interpretation", "82.9",
                     "17.1", "actions[0].suggested_action", "30"):
            self.assertIn(text, str(error.exception))

    def test_numeric_failure_repair_receives_precise_feedback_and_preserves_output(self):
        good = FakeLLM().generate([], {})
        bad = deepcopy(good)
        bad["actions"][0]["interpretation"] = "Receivables grew 2.4224% in 2026."
        class RepairLLM:
            def __init__(self): self.calls = []
            def generate(self, messages, schema):
                self.calls.append(deepcopy(messages))
                return deepcopy(bad if len(self.calls) == 1 else good)
        llm = RepairLLM()
        result = RecommendationAgent(MetricTools(dataset(), date(2026,9,1)), llm).run()
        self.assertEqual(len(llm.calls), 2)
        feedback = llm.calls[1][-1]["content"]
        for text in ("actions[0].interpretation", "2.4224", "2026", "respective growth metrics"):
            self.assertIn(text, feedback)
        self.assertEqual(result["recommendation"]["actions"], good["actions"])
        self.assertIn("formula", result["evidence"]["revenue_metrics"]["revenue_yoy_growth_pct"])

    def test_alternative_unicode_numerals_are_rejected_in_all_prose_fields(self):
        examples = ("Growth is ３.０％.", "Growth is ٣.٠٪.", "Growth is ۳.۰٪.",
                    "Review in ① month.", "Review in Ⅳ months.")
        for text in examples:
            for field in ("summary", "title", "interpretation", "suggested_action"):
                result = FakeLLM().generate([], {})
                if field == "summary": result[field] = text
                else: result["actions"][0][field] = text
                original = deepcopy(result)
                with self.subTest(text=text, field=field), self.assertRaisesRegex(ValueError, field):
                    validate_recommendation(result, {"liquidity_metrics.overdue_ratio_pct"})
                self.assertEqual(result, original)

    def test_reported_shorthand_citations_and_fullwidth_prose_reported_together(self):
        result = FakeLLM().generate([], {})
        result["summary"] = "Revenue grew ３.０％ and annual revenue declined １４.７％."
        result["actions"][0]["evidence_metric_ids"] = ["revenue_mom_growth_pct", "revenue_yoy_growth_pct"]
        available = {"revenue_metrics.revenue_mom_growth_pct", "revenue_metrics.revenue_yoy_growth_pct"}
        with self.assertRaises(ValueError) as error:
            validate_recommendation(result, available)
        for part in ("actions[0].evidence_metric_ids", "revenue_metrics.revenue_mom_growth_pct",
                     "revenue_metrics.revenue_yoy_growth_pct", "summary", "3.0", "14.7"):
            self.assertIn(part, str(error.exception))

    def test_schema_excludes_fullwidth_and_arabic_digits(self):
        import re
        pattern = recommendation_schema([])["properties"]["summary"]["pattern"]
        for text in ("Revenue grew ３％.", "Revenue grew ٣٪.", "Revenue grew ۳٪.", "Revenue grew 3%."):
            self.assertIsNone(re.fullmatch(pattern, text))
        self.assertIsNotNone(re.fullmatch(pattern, "Revenue declined; review collections."))

    def test_combined_failure_is_repaired_in_existing_single_retry(self):
        good = FakeLLM().generate([], {})
        good["actions"][0]["evidence_metric_ids"] = ["revenue_metrics.revenue_yoy_growth_pct"]
        bad = deepcopy(good)
        bad["summary"] = "Annual revenue declined １４.７％."
        bad["actions"][0]["evidence_metric_ids"] = ["revenue_yoy_growth_pct"]
        class RepairLLM:
            def __init__(self): self.calls=[]
            def generate(self,messages,schema):
                self.calls.append(deepcopy(messages))
                return deepcopy(bad if len(self.calls)==1 else good)
        llm=RepairLLM()
        result=RecommendationAgent(MetricTools(dataset(),date(2026,9,1)),llm).run()
        self.assertEqual(len(llm.calls),2)
        self.assertIn("revenue_metrics.revenue_yoy_growth_pct",llm.calls[1][-1]["content"])
        self.assertIn("14.7",llm.calls[1][-1]["content"])
        self.assertEqual(result["recommendation"]["actions"],good["actions"])

    def test_no_history_returns_insufficient_data(self):
        llm = FakeLLM()
        agent = RecommendationAgent(MetricTools(dataset(count=0), date(2026, 9, 1)),
            llm)
        self.assertEqual(agent.run()["status"], "insufficient_data")
        self.assertEqual(llm.calls, [])


class DatabaseTests(unittest.TestCase):
    def test_bind_variables_read_only_transaction_and_cross_owner_queries(self):
        calls = []
        class Cursor:
            description = []
            def __enter__(self): return self
            def __exit__(self, *args): pass
            def execute(self, sql, params=None):
                calls.append((sql, params))
                self.description = [("MSME_ID",)] if "business_name" in sql else []
            def fetchall(self): return [(1,)] if self.description else []
        class Connection:
            rolled_back = False
            def __enter__(self): return self
            def __exit__(self, *args): pass
            def cursor(self): return Cursor()
            def rollback(self): self.rolled_back = True
        connection = Connection()
        settings = SimpleNamespace(user="test", password="test", dsn="test",
            profile_schema="PROFILE_OWNER", assessment_schema="ASSESSMENT_OWNER", call_timeout_ms=1000)
        fake = SimpleNamespace(connect=lambda **kwargs: connection)
        with patch.dict(sys.modules, {"oracledb": fake}):
            OracleRepository(settings).load(7, date(2026, 9, 1))
        self.assertEqual(calls[0][0], "SET TRANSACTION READ ONLY")
        self.assertTrue(connection.rolled_back)
        self.assertIn("PROFILE_OWNER.MSME", calls[1][0])
        self.assertIn("ASSESSMENT_OWNER.RISK_ASSESSMENT", calls[3][0])
        self.assertEqual(calls[2][1]["msme_id"], 7)
        self.assertEqual(calls[2][1]["end_at"], datetime(2026, 10, 1))
        self.assertTrue(all(sql.lstrip().startswith("SELECT") for sql, _ in calls[1:]))


if __name__ == "__main__":
    unittest.main()
