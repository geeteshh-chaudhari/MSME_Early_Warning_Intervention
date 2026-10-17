from datetime import date, datetime
from decimal import Decimal
from types import SimpleNamespace
import sys
import unittest
from unittest.mock import patch

from recommendation.agent import RecommendationAgent, validate_recommendation
from recommendation.database import OracleRepository
from recommendation.metrics import MetricTools, shift_month


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
    def __init__(self, selections):
        self.selections = iter(selections)

    def generate(self, messages, schema):
        if "tool" in schema["properties"]:
            return {"tool": next(self.selections)}
        return {"summary": "Collections need RM review.", "actions": [{
            "title": "Review overdue invoices", "interpretation": "Some receivables are overdue.",
            "suggested_action": "Agree a collection schedule with the customer.",
            "evidence_metric_ids": ["liquidity_metrics.overdue_ratio_pct"]}]}


class AgentTests(unittest.TestCase):
    def test_model_selects_tools_and_evidence_is_returned(self):
        agent = RecommendationAgent(MetricTools(dataset(), date(2026, 9, 1)),
            FakeLLM(["liquidity_metrics", "business_context", "revenue_metrics", "finish"]))
        result = agent.run()
        self.assertEqual(result["status"], "generated")
        self.assertEqual(result["tool_trace"][0]["tool"], "liquidity_metrics")
        self.assertIn("liquidity_metrics", result["evidence"])

    def test_early_finish_or_arbitrary_execution_rejected(self):
        for name in ("finish", "execute_sql", "__class__"):
            agent = RecommendationAgent(MetricTools(dataset(), date(2026, 9, 1)), FakeLLM([name]))
            with self.subTest(name=name), self.assertRaises(ValueError):
                agent.run()

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

    def test_numeric_claim_must_match_cited_evidence(self):
        recommendation = FakeLLM([]).generate([], {"properties": {}})
        recommendation["actions"][0]["interpretation"] = "The overdue ratio is 40%."
        available = {"liquidity_metrics.overdue_ratio_pct"}
        validate_recommendation(recommendation, available, {"liquidity_metrics.overdue_ratio_pct": 40})
        with self.assertRaises(ValueError):
            validate_recommendation(recommendation, available, {"liquidity_metrics.overdue_ratio_pct": 30})

    def test_no_history_returns_insufficient_data(self):
        agent = RecommendationAgent(MetricTools(dataset(count=0), date(2026, 9, 1)),
            FakeLLM(["business_context", "revenue_metrics", "liquidity_metrics", "finish"]))
        self.assertEqual(agent.run()["status"], "insufficient_data")


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
