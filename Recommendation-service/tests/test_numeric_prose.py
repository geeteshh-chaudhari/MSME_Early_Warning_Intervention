from copy import deepcopy
from datetime import date
import unittest

from recommendation.agent import RecommendationAgent, recommendation_schema, validate_recommendation
from recommendation.metrics import MetricTools
from recommendation.numeric_prose import normalize_text, normalize_recommendation, numeric_errors
from test_agent import dataset, FakeLLM

CREDIT = "liquidity_metrics.credit_utilisation_pct"
GROWTH = "revenue_metrics.revenue_yoy_growth_pct"


class NumericProseTests(unittest.TestCase):
    def test_normalizes_mathematical_subscript_fullwidth_and_arabic_digits(self):
        for text in ("𝟑.₁％", "３.１％", "٣٫١٪", "۳٫۱٪"):
            self.assertEqual(normalize_text(text), "3.1%")
        self.assertEqual(normalize_text("−１４.７％"), "-14.7%")

    def test_normalization_changes_only_prose_and_does_not_mutate_input(self):
        result=FakeLLM().generate([], {})
        result["summary"]="Credit utilisation is 𝟒𝟓%."
        result["actions"][0]["interpretation"]="Utilisation is ４５％."
        result["actions"][0]["evidence_metric_ids"]=[CREDIT]
        before=deepcopy(result)
        normalized=normalize_recommendation(result)
        self.assertEqual(result,before)
        self.assertEqual(normalized["summary"],"Credit utilisation is 45%.")
        self.assertEqual(normalized["actions"][0]["interpretation"],"Utilisation is 45%.")
        self.assertEqual(normalized["actions"][0]["evidence_metric_ids"],[CREDIT])
        validate_recommendation(normalized,{CREDIT},{CREDIT:45},{CREDIT:"percent"})

    def test_prose_schema_permits_numbers_and_retains_response_fields(self):
        schema=recommendation_schema([CREDIT])
        fields=[schema["properties"]["summary"]]+[
            schema["properties"]["actions"]["items"]["properties"][key]
            for key in ("title","interpretation","suggested_action")]
        for field in fields:self.assertNotIn("pattern",field)
        self.assertEqual(schema["properties"]["actions"]["maxItems"],6)

    def test_only_equal_numeric_values_pass_regardless_of_trailing_zeros(self):
        for text in ("Growth is 4.53%.", "Growth is 4.530%.", "Growth is -4.53%.", "Growth is ４.５３％."):
            self.assertEqual(numeric_errors("summary", text, {GROWTH}, {GROWTH: 4.53}), [])
        for text in ("Growth is 4.5%.", "Growth is 4.529%."):
            self.assertTrue(numeric_errors("summary", text, {GROWTH}, {GROWTH: 4.53}))

    def test_sign_is_ignored_when_direction_is_expressed_in_words(self):
        self.assertEqual(numeric_errors("summary", "Growth is -14.70%.", {GROWTH}, {GROWTH: -14.7}), [])
        self.assertEqual(numeric_errors("summary", "Revenue declined by 14.70%.", {GROWTH}, {GROWTH: -14.7}), [])

    def test_sign_is_ignored_for_non_growth_metrics_too(self):
        key="liquidity_metrics.credit_headroom_pct"
        self.assertEqual(numeric_errors("summary","Headroom decline of 5%.",{key},{key:-5}), [])

    def test_sign_variants_match_but_changed_magnitude_or_decimal_does_not(self):
        for tool_value in (-14.43, 14.43):
            for prose in ("Growth is 14.43%.", "Growth is -14.43%.",
                          "Growth is +14.43%.", "Growth is −１４.４３％."):
                self.assertEqual(numeric_errors("summary", prose, {GROWTH}, {GROWTH: tool_value}), [])
        for prose in ("Growth is 14.4%.", "Growth is -14.44%."):
            self.assertTrue(numeric_errors("summary", prose, {GROWTH}, {GROWTH: -14.43}))
        self.assertTrue(numeric_errors("summary", "Growth is 87%.", {GROWTH}, {GROWTH: 0.87}))
        self.assertEqual(numeric_errors("summary", "Growth is -0.00%.", {GROWTH}, {GROWTH: 0}), [])

    def test_all_fields_compare_magnitudes_without_mutating_tool_evidence(self):
        result = FakeLLM().generate([], {})
        result["summary"] = "Revenue declined by 14.43%."
        for field in ("title", "interpretation", "suggested_action"):
            result["actions"][0][field] = "Review the 14.43% decline."
        evidence = {GROWTH: -14.43}
        before = deepcopy(evidence)
        validate_recommendation(result, {GROWTH}, evidence)
        self.assertEqual(evidence, before)

    def test_leading_dot_decimals_match_their_actual_magnitude(self):
        for text in ("Growth is .87%.", ".87% growth", "Growth is (.87%).",
                     "Growth is -.87%.", "Growth is +.87%.", "Growth is .870%.",
                     "Growth is −．８７％.", "Growth is .٨٧%."):
            self.assertEqual(numeric_errors("summary", text, {GROWTH}, {GROWTH: 0.87}), [])
        self.assertEqual(numeric_errors("summary", "Growth is .0%.", {GROWTH}, {GROWTH: 0}), [])

    def test_leading_dot_parsing_does_not_scale_integers_or_hide_bad_decimals(self):
        for text in ("Growth is 87%.", "Growth is .88%.", "Growth is ..87%.",
                     "Growth is 0.8.7%.", "Growth is .87e2%."):
            self.assertTrue(numeric_errors("summary", text, {GROWTH}, {GROWTH: 0.87}))
        self.assertTrue(numeric_errors("summary", "Growth is .87%.", {GROWTH}, {GROWTH: 87}))

    def test_agent_accepts_leading_dot_decimal_without_repair_or_evidence_changes(self):
        from decimal import Decimal
        data = dataset()
        data["snapshots"][-1]["revenue"] = Decimal("100.87")
        output = FakeLLM().generate([], {})
        output["summary"] = "Revenue grew .87%."
        output["actions"][0]["interpretation"] = "Monthly growth is .87%."
        class LeadingDotLLM:
            def __init__(self): self.calls = 0
            def generate(self, messages, schema):
                self.calls += 1
                return deepcopy(output)
        llm = LeadingDotLLM()
        result = RecommendationAgent(MetricTools(data, date(2026, 9, 1)), llm).run()
        self.assertEqual(llm.calls, 1)
        self.assertEqual(result["recommendation"]["summary"], "Revenue grew .87%.")
        self.assertEqual(result["evidence"]["revenue_metrics"]["revenue_mom_growth_pct"]["value"], 0.87)

    def test_action_numbers_match_any_available_tool_independent_of_citations(self):
        result = FakeLLM().generate([], {})
        result["actions"][0]["interpretation"] = "Annual growth is 4.80%."
        for citations in ([CREDIT], ["unknown"], []):
            result["actions"][0]["evidence_metric_ids"] = citations
            validate_recommendation(result, {CREDIT, GROWTH}, {CREDIT: 45, GROWTH: 4.8})

    def test_summary_can_use_any_available_metric(self):
        result=FakeLLM().generate([], {})
        result["summary"]="Annual growth is 4.8%; credit utilisation is 45%."
        result["actions"][0]["evidence_metric_ids"]=[CREDIT]
        validate_recommendation(result,{CREDIT,GROWTH},{CREDIT:45,GROWTH:4.8})

    def test_unavailable_or_missing_values_cannot_support_numbers(self):
        self.assertTrue(numeric_errors("summary","Credit utilisation is 45%.",{CREDIT},{}))
        self.assertTrue(numeric_errors("summary","Credit utilisation is 45%.",{CREDIT},{CREDIT:float('nan')}))

    def test_units_do_not_change_numeric_equality(self):
        key = "liquidity_metrics.credit_utilisation_change_pp"
        for text in ("Change is 3.10%.", "Change is 3.10 percentage points.", "Change is 3.10 months."):
            self.assertEqual(numeric_errors("summary", text, {key}, {key: 3.1}, {key: "percentage_points"}), [])

    def test_month_wording_does_not_require_month_metric(self):
        self.assertEqual(numeric_errors("summary", "Breaches persisted for 3 months.",
                                       {CREDIT}, {CREDIT: 3}, {CREDIT: "percent"}), [])

    def test_reporting_years_are_exempt_but_unmatched_financial_values_fail(self):
        self.assertEqual(numeric_errors("summary", "Review in 30 days.", {CREDIT}, {CREDIT: 30}), [])
        self.assertEqual(numeric_errors("summary", "Reporting year 2026.", {CREDIT}, {CREDIT: 30}), [])
        self.assertTrue(numeric_errors("summary", "Utilisation is below 100%.", {CREDIT}, {CREDIT: 82.9}))

    def test_years_calendar_months_and_durations_are_not_financial_values(self):
        for text in ("Revenue in September 2026.", "Revenue in Sep 2026.",
                     "Compare 2025 and 2026.", "As of 2026-09.",
                     "As of 2026-09-01.", "As of 09/2026.",
                     "Review the last 12 months.", "Review a 12-month window.",
                     "Compare the last 3 years.", "Review a 1-year window.",
                     "September ２０２６ with the last １２ months."):
            self.assertEqual(numeric_errors("summary", text, set(), {}), [], text)

    def test_year_like_percentages_and_decimal_financial_values_still_fail(self):
        for text in ("Revenue grew 2026%.", "Growth is 2026 percent.",
                     "Gap is 2026 percentage points.", "Growth is 2026.5%.",
                     "Revenue is $2026.", "Revenue grew 12.41% in September 2026."):
            self.assertTrue(numeric_errors("summary", text, set(), {}), text)

    def test_temporal_exemptions_do_not_skip_nearby_incorrect_financial_values(self):
        values = {CREDIT: 58.0}
        self.assertEqual(numeric_errors("summary", "September 2026: utilisation is 58.0% over 12 months.",
                                       {CREDIT}, values), [])
        errors = numeric_errors("summary", "September 2026: utilisation is 59.0% over 12 months.",
                                {CREDIT}, values)
        self.assertEqual(len(errors), 1)
        self.assertIn("59.0", errors[0])

    def test_reported_year_and_month_window_pass_all_response_fields(self):
        result = FakeLLM().generate([], {})
        result["summary"] = "Review September 2026 with overdue receivables at 18.0%."
        result["actions"][0]["title"] = "Review September 2026"
        result["actions"][0]["interpretation"] = "Utilisation is 58.0% in September 2026."
        result["actions"][0]["suggested_action"] = "Review the last 12 months."
        values = {CREDIT: 58.0, "liquidity_metrics.overdue_ratio_pct": 18.0}
        validate_recommendation(result, set(values), values)

    def test_thousands_separators_and_zero_supported(self):
        self.assertEqual(numeric_errors("summary","The value is 1,000.",{CREDIT},{CREDIT:1000}),[])
        self.assertEqual(numeric_errors("summary","The change is -0.0 pp.",{CREDIT},{CREDIT:0},{CREDIT:"percentage_points"}),[])
        self.assertTrue(numeric_errors("summary","The value is 3,10.",{CREDIT},{CREDIT:3}))

    def test_fraction_roman_and_scientific_notation_not_silently_reinterpreted(self):
        for text in ("Growth is ½%.","Growth is Ⅳ%.","Growth is 1e2%."):
            self.assertTrue(numeric_errors("summary",text,{CREDIT},{CREDIT:100}))

    def test_agent_returns_normal_digits_and_full_evidence_in_one_call(self):
        output=FakeLLM().generate([], {})
        output["summary"]="The overdue ratio is 𝟒𝟎%."
        output["actions"][0]["interpretation"]="The overdue ratio is ４０％."
        class NumericLLM:
            def __init__(self):self.calls=0
            def generate(self,messages,schema):self.calls+=1;return deepcopy(output)
        llm=NumericLLM();result=RecommendationAgent(MetricTools(dataset(),date(2026,9,1)),llm).run()
        self.assertEqual(llm.calls,1)
        self.assertEqual(result["recommendation"]["summary"],"The overdue ratio is 40%.")
        self.assertEqual(result["recommendation"]["actions"][0]["interpretation"],"The overdue ratio is 40%.")
        self.assertEqual(result["evidence"]["liquidity_metrics"]["overdue_ratio_pct"]["value"],40)
        self.assertIn("formula",result["evidence"]["liquidity_metrics"]["overdue_ratio_pct"])


if __name__=="__main__":unittest.main()
