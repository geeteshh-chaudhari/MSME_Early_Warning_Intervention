from datetime import date
from decimal import Decimal
import unittest
from recommendation.metrics import metric, MetricTools, TOOL_DESCRIPTIONS
from recommendation.agent import compact_tool_results, validate_recommendation
from test_agent import dataset


class PrecisionTests(unittest.TestCase):
    def test_half_up_and_signed_values(self):
        for raw, expected in [("18.4054", 18.41), ("18.5886", 18.59),
                              ("1.005", 1.01), ("-1.005", -1.01), ("45", 45.0)]:
            self.assertEqual(metric(Decimal(raw))["value"], expected)
        self.assertIsNone(metric()["value"])

    def test_gap_is_rounded_after_subtracting_raw_growth(self):
        data = dataset()
        data["snapshots"][-1]["revenue"] = Decimal("101.004")
        data["snapshots"][-1]["total_receivables"] = Decimal("50.5025")
        result = MetricTools(data, date(2026, 9, 1)).liquidity_metrics()
        self.assertEqual(result["receivables_yoy_growth_pct"]["value"], 1.01)
        self.assertEqual(result["receivables_vs_revenue_growth_gap_pp"]["value"], 0.0)

    def test_model_payload_and_evidence_share_two_decimal_metrics(self):
        data = dataset()
        data["snapshots"][-1]["revenue"] = Decimal("118.4054")
        tools = MetricTools(data, date(2026, 9, 1))
        evidence = {name: tools.execute(name) for name in TOOL_DESCRIPTIONS}
        payload = compact_tool_results(evidence)
        key = "revenue_yoy_growth_pct"
        self.assertEqual(evidence["revenue_metrics"][key]["value"], 18.41)
        self.assertEqual(payload["revenue_metrics"][key]["value"], 18.41)
        self.assertEqual(data["snapshots"][-1]["revenue"], Decimal("118.4054"))
        for name, metrics in evidence.items():
            if name == "business_context":
                continue
            for item in metrics.values():
                if item["value"] is not None:
                    value = Decimal(str(item["value"]))
                    self.assertEqual(value, value.quantize(Decimal("0.01")))
        metric_id = "revenue_metrics." + key
        rec = {"summary": "Revenue grew 18.41%.", "actions": [{
            "title": "Review revenue", "interpretation": "Revenue grew 18.41%.",
            "suggested_action": "Review the trend.", "evidence_metric_ids": [metric_id]}]}
        validate_recommendation(rec, {metric_id}, {metric_id: 18.41}, {metric_id: "percent"})
        rec["summary"] = "Revenue grew 18.50%."
        with self.assertRaises(ValueError):
            validate_recommendation(rec, {metric_id}, {metric_id: 18.41}, {metric_id: "percent"})
