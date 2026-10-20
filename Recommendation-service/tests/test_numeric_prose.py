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

    def test_supported_exact_and_rounded_values_pass(self):
        for text in ("Annual growth is 4.529%.","Annual growth is 4.53%.","Annual growth is 4.5%."):
            self.assertEqual(numeric_errors("summary",text,{GROWTH},{GROWTH:4.529},{GROWTH:"percent"}),[])
        self.assertTrue(numeric_errors("summary","Annual growth is 4.6%.",{GROWTH},{GROWTH:4.529}))

    def test_explicit_decline_magnitude_matches_negative_growth_only(self):
        for text in ("Annual revenue declined by 14.7%.","Annual revenue shows a decline of 14.7%.",
                     "Revenue shows a 14.7% annual decline.","Annual growth is -14.7%."):
            self.assertEqual(numeric_errors("summary",text,{GROWTH},{GROWTH:-14.733},{GROWTH:"percent"}),[])
        for text in ("Annual revenue growth is 14.7%.","Annual revenue grew by 14.7%."):
            self.assertTrue(numeric_errors("summary",text,{GROWTH},{GROWTH:-14.733}))

    def test_decline_wording_does_not_flip_negative_non_growth_metric(self):
        key="liquidity_metrics.credit_headroom_pct"
        self.assertTrue(numeric_errors("summary","Headroom decline of 5%.",{key},{key:-5}))

    def test_wrong_action_citations_still_fail_despite_value_elsewhere(self):
        result=FakeLLM().generate([], {})
        result["actions"][0]["interpretation"]="Annual growth is 4.8%."
        result["actions"][0]["evidence_metric_ids"]=[CREDIT]
        with self.assertRaisesRegex(ValueError,r"actions\[0\].interpretation.*4.8"):
            validate_recommendation(result,{CREDIT,GROWTH},{CREDIT:45,GROWTH:4.8})

    def test_summary_can_use_any_available_metric(self):
        result=FakeLLM().generate([], {})
        result["summary"]="Annual growth is 4.8%; credit utilisation is 45%."
        result["actions"][0]["evidence_metric_ids"]=[CREDIT]
        validate_recommendation(result,{CREDIT,GROWTH},{CREDIT:45,GROWTH:4.8})

    def test_unavailable_or_missing_values_cannot_support_numbers(self):
        self.assertTrue(numeric_errors("summary","Credit utilisation is 45%.",{CREDIT},{}))
        self.assertTrue(numeric_errors("summary","Credit utilisation is 45%.",{CREDIT},{CREDIT:float('nan')}))

    def test_percentage_points_are_not_interchangeable_with_percent(self):
        key="liquidity_metrics.credit_utilisation_change_pp"
        values={key:3.1};units={key:"percentage_points"}
        self.assertEqual(numeric_errors("summary","Utilisation rose 3.1 percentage points.",{key},values,units),[])
        self.assertTrue(numeric_errors("summary","Utilisation rose 3.1%.",{key},values,units))

    def test_saved_month_count_requires_month_metric_not_percent_metric(self):
        key="breach_persistence.credit_utilisation_consecutive_breach_months"
        self.assertEqual(numeric_errors("summary","Breaches persisted for 3 months.",{key},{key:3},{key:"months"}),[])
        self.assertTrue(numeric_errors("summary","Breaches persisted for 3 months.",{CREDIT},{CREDIT:3},{CREDIT:"percent"}))

    def test_dates_and_invented_deadlines_are_not_accepted_as_metric_values(self):
        for text in ("Review in 30 days.","Review within 12 months.","Reporting date 2026-09-01."):
            self.assertTrue(numeric_errors("summary",text,{CREDIT},{CREDIT:30},{CREDIT:"percent"}))

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
