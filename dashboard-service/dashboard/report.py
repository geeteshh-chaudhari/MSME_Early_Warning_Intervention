"""Read-only chart payloads, using the exact tools used by recommendations."""
from recommendation.metrics import MetricTools, TOOL_DESCRIPTIONS, month, shift_month


def build_report(data, as_of):
    tools = MetricTools(data, as_of)
    evidence = {name: tools.execute(name) for name in TOOL_DESCRIPTIONS}
    if as_of not in tools.snapshots:
        raise LookupError("No financial snapshot exists for the requested month.")
    series = []
    # Include gaps explicitly: a missing observation must never become zero.
    for offset in range(-47, 1):
        period = shift_month(as_of, offset)
        subset = dict(data,
            snapshots=[r for r in data["snapshots"] if month(r["reporting_period"]) <= period],
            assessments=[r for r in data["assessments"] if month(r["reporting_period"]) <= period],
            indicators=[r for r in data["indicators"] if month(r["reporting_period"]) <= period])
        historical = MetricTools(subset, period)
        metrics = {group + "." + key: value
                   for group in TOOL_DESCRIPTIONS if group != "business_context"
                   for key, value in historical.execute(group).items()}
        snapshot = tools.snapshots.get(period)
        series.append({"month": period.isoformat(), "available": snapshot is not None,
                       "snapshot": {k: snapshot[k] for k in ("revenue", "total_receivables",
                                    "overdue_receivables", "credit_used")} if snapshot else None,
                       "metrics": metrics})
    return {"msme_id": int(data["profile"]["msme_id"]), "as_of": as_of.isoformat(),
            "formula_version": "1.0", "context": evidence["business_context"],
            "credit_limit": tools.limit, "evidence": evidence, "series": series,
            "saved_indicators": data["indicators"],
            "notes": [
                "Amounts retain the database units; no currency is assumed.",
                "Gaps indicate unavailable observations, not zero.",
                "Historical credit utilisation uses the current sanctioned credit limit.",
                "QoQ uses the most recently completed calendar quarter; see metric comparison dates.",
                "Historical seasonal deviation is exploratory, not adjusted for trend or proof of seasonality.",
                "Saved breaches use historical assessment thresholds, not thresholds for the new metrics.",
                "History is recalculated from records available at the selected reporting cutoff; it is not an audit of what was known at each earlier date.",
                "No LLM is called and no database records are changed."]}
