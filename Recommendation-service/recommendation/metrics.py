"""Deterministic metrics. LLMs never calculate financial values."""
from datetime import date, datetime
from decimal import Decimal, InvalidOperation


def shift_month(value, offset):
    index = value.year * 12 + value.month - 1 + offset
    year, month = divmod(index, 12)
    return date(year, month + 1, 1)


def month(value):
    if isinstance(value, str):
        value = date.fromisoformat(value[:10])
    if isinstance(value, datetime):
        value = value.date()
    if not isinstance(value, date) or value.day != 1:
        raise ValueError("Reporting periods must be first-of-month dates.")
    return value


def number(value):
    try:
        result = Decimal(str(value))
    except (InvalidOperation, TypeError, ValueError) as ex:
        raise ValueError("Invalid numeric financial input.") from ex
    if not result.is_finite():
        raise ValueError("Financial inputs must be finite.")
    return result


def metric(value=None, unit="percent", reason=None, **details):
    return {"value": float(round(value, 4)) if value is not None else None,
            "unit": unit, "status": "unavailable" if value is None else "available",
            "reason": reason, **details}


def growth(current, previous):
    if previous <= 0:
        return metric(reason="Comparison revenue is zero; growth is undefined.")
    return metric((current / previous - 1) * 100)


class MetricTools:
    def __init__(self, dataset, as_of):
        self.data, self.as_of = dataset, month(as_of)
        self.snapshots = {}
        for row in dataset["snapshots"]:
            period = month(row["reporting_period"])
            if period > self.as_of:
                raise ValueError("Future snapshot in dataset.")
            if period in self.snapshots:
                raise ValueError("Duplicate monthly snapshot; resolve before calculating metrics.")
            row = dict(row)
            for key in ("revenue", "total_receivables", "overdue_receivables", "credit_used"):
                row[key] = number(row[key])
                if row[key] < 0:
                    raise ValueError("Negative financial value in snapshot.")
            if row["overdue_receivables"] > row["total_receivables"]:
                raise ValueError("Overdue receivables exceed total receivables.")
            self.snapshots[period] = row
        self.limit = number(dataset["profile"]["sanctioned_credit_limit"])
        if self.limit <= 0:
            raise ValueError("Sanctioned credit limit must be positive.")

    def execute(self, name):
        if name not in TOOL_DESCRIPTIONS:
            raise ValueError("Unknown metric tool.")
        result = getattr(self, name)()
        if name != "business_context":
            for key, item in result.items():
                value = item["value"]
                if value is None:
                    continue
                if "growth_gap" in key:
                    item["meaning"] = ("Receivables and revenue have equal YoY growth rates." if value == 0
                        else "Receivables grew faster than revenue." if value > 0
                        else "Receivables grew slower than revenue.")
                elif "growth_pct" in key:
                    item["meaning"] = ("No change from the comparison period." if value == 0
                        else "Growth from the comparison period." if value > 0
                        else "Decline from the comparison period.")
                elif "volatility" in key:
                    item["meaning"] = ("No variation among the twelve monthly revenues." if value == 0
                        else "Revenue varies; this does not distinguish seasonality from other causes.")
                elif "historical_deviation" in key:
                    item["meaning"] = ("Matches the prior same-calendar-month average; this does not prove seasonality." if value == 0
                        else "Above the prior same-calendar-month average; not proof of seasonality." if value > 0
                        else "Below the prior same-calendar-month average; not proof of seasonality.")
        return result

    def business_context(self):
        latest = self.snapshots.get(self.as_of)
        assessment = next((a for a in reversed(self.data["assessments"])
                           if month(a["reporting_period"]) == self.as_of), None)
        context = {"msme_id": int(self.data["profile"]["msme_id"]),
                   "business_name": self.data["profile"]["business_name"],
                   "sector": self.data["profile"]["sector"],
                   "is_active": self.data["profile"]["is_active"],
                   "as_of_month": self.as_of.isoformat(),
                   "snapshot_id": latest["snapshot_id"] if latest else None,
                   "history_months_available": len(self.snapshots),
                   "assessment": None,
                   "limitations": ["Credit limit is current master data, not historical limits.",
                       "Revenue is not profit or cash flow; repayment capacity cannot be calculated."]}
        if assessment:
            context["assessment"] = {k: assessment[k] for k in
                ("assessment_id", "threshold_config_id", "severity", "stress_pattern",
                 "revenue_decline_pct", "overdue_receivable_pct", "credit_utilisation_pct")}
        else:
            context["limitations"].append("No assessment available for the requested month as of its end; no default thresholds assumed.")
        if not latest:
            context["limitations"].append("Requested month has no snapshot; latest-month metrics are unavailable.")
        return context

    def _sum(self, end, count, field="revenue"):
        periods = [shift_month(end, -i) for i in range(count)]
        if any(p not in self.snapshots for p in periods):
            return None
        return sum((self.snapshots[p][field] for p in periods), Decimal(0))

    def _compare(self, end, count, offset):
        previous_end = shift_month(end, -offset)
        current, previous = self._sum(end, count), self._sum(previous_end, count)
        result = (metric(reason="Missing months in current or comparison window.")
                  if current is None or previous is None else growth(current, previous))
        result.update(current_start=shift_month(end, 1-count).isoformat(),
                      current_end=end.isoformat(),
                      comparison_start=shift_month(previous_end, 1-count).isoformat(),
                      comparison_end=previous_end.isoformat(),
                      formula="(sum_current_revenue / sum_comparison_revenue - 1) * 100")
        return result

    def revenue_metrics(self):
        # If the as-of month is mid-quarter, use the most recent complete quarter.
        end = self.as_of
        quarter_end = end if end.month % 3 == 0 else shift_month(end, -(end.month % 3))
        return {"revenue_mom_growth_pct": self._compare(end, 1, 1),
                "revenue_yoy_growth_pct": self._compare(end, 1, 12),
                "revenue_qoq_growth_pct": self._compare(quarter_end, 3, 3),
                "revenue_quarter_yoy_growth_pct": self._compare(quarter_end, 3, 12),
                "revenue_rolling_3m_growth_pct": self._compare(end, 3, 3),
                "revenue_ttm_growth_pct": self._compare(end, 12, 12)}

    def liquidity_metrics(self):
        current = self.snapshots.get(self.as_of)
        previous = self.snapshots.get(shift_month(self.as_of, -1))
        missing = lambda: metric(reason="Requested month snapshot is missing.")
        keys = ("overdue_ratio_pct", "credit_utilisation_pct", "credit_headroom_pct",
                "overdue_ratio_change_pp", "credit_utilisation_change_pp",
                "receivables_yoy_growth_pct", "receivables_vs_revenue_growth_gap_pp")
        if not current:
            return {k: missing() for k in keys}
        def overdue(row):
            return (row["overdue_receivables"] / row["total_receivables"] * 100
                    if row["total_receivables"] > 0 else None)
        ratio, prior_ratio = overdue(current), overdue(previous) if previous else None
        # Historical utilisation uses the current limit; disclose this assumption.
        utilisation = current["credit_used"] / self.limit * 100
        yoy = self.snapshots.get(shift_month(self.as_of, -12))
        rec_growth = growth(current["total_receivables"], yoy["total_receivables"]) if yoy else metric(reason="Same month last year missing.")
        revenue_growth = growth(current["revenue"], yoy["revenue"]) if yoy else metric(reason="Same month last year missing.")
        rec_value, rev_value = rec_growth["value"], revenue_growth["value"]
        return {
            "overdue_ratio_pct": metric(ratio, reason="Zero total receivables." if ratio is None else None,
                formula="overdue_receivables / total_receivables * 100"),
            "credit_utilisation_pct": metric(utilisation, formula="credit_used / current_sanctioned_credit_limit * 100"),
            "credit_headroom_pct": metric(100-utilisation, formula="100 - credit_utilisation_pct"),
            "overdue_ratio_change_pp": metric(ratio-prior_ratio if ratio is not None and prior_ratio is not None else None,
                unit="percentage_points", reason="Needs two valid consecutive monthly overdue ratios." if ratio is None or prior_ratio is None else None),
            "credit_utilisation_change_pp": metric((current["credit_used"]-previous["credit_used"]) / self.limit * 100 if previous else None,
                unit="percentage_points", reason="Previous month missing." if not previous else None,
                assumption="Both months use the current sanctioned credit limit."),
            "receivables_yoy_growth_pct": rec_growth,
            "receivables_vs_revenue_growth_gap_pp": metric(number(rec_value)-number(rev_value)
                if rec_value is not None and rev_value is not None else None,
                unit="percentage_points", reason="Requires valid YoY revenue and receivables growth." if rec_value is None or rev_value is None else None)}

    def seasonality_metrics(self):
        current = self.snapshots.get(self.as_of)
        prior = [self.snapshots[p]["revenue"] for i in range(1, 4)
                 if (p := shift_month(self.as_of, -12*i)) in self.snapshots]
        if current and len(prior) >= 2:
            expected = sum(prior) / len(prior)
            deviation = growth(current["revenue"], expected)
        else:
            deviation = metric(reason="Needs current month and at least two prior matching calendar months.")
        deviation.update(prior_matching_months=len(prior),
            formula="(current_revenue / mean_prior_same_calendar_month_revenue - 1) * 100",
            limitation="Exploratory seasonal comparison; not trend-adjusted or proof of seasonality.")
        total = self._sum(self.as_of, 12)
        volatility = metric(reason="Needs 12 consecutive months and a positive mean.")
        if total is not None and total > 0:
            values = [self.snapshots[shift_month(self.as_of, -i)]["revenue"] for i in range(12)]
            mean = total / 12
            variance = sum((v-mean)**2 for v in values) / 12
            volatility = metric(variance.sqrt() / mean * 100,
                formula="population_standard_deviation_12m / mean_revenue_12m * 100",
                limitation="Raw volatility includes normal seasonality.")
        return {"same_month_historical_deviation_pct": deviation,
                "revenue_volatility_12m_pct": volatility}

    def breach_persistence(self):
        result = {}
        for code in ("REVENUE_DETERIORATION", "OVERDUE_RECEIVABLES", "CREDIT_UTILISATION"):
            by_month = {}
            for row in self.data["indicators"]:
                if row["indicator_code"] != code:
                    continue
                period = month(row["reporting_period"])
                if period in by_month:
                    raise ValueError("Duplicate assessment indicator month.")
                by_month[period] = row
            window = [shift_month(self.as_of, -i) for i in range(12)]
            known = [by_month[p] for p in window if p in by_month and by_month[p]["calculated_value"] is not None]
            breaches = sum(r["breached_flag"] == "Y" for r in known)
            streak = 0
            for period in window:
                row = by_month.get(period)
                if not row or row["calculated_value"] is None or row["breached_flag"] != "Y":
                    break
                streak += 1
            latest_valid = self.as_of in by_month and by_month[self.as_of]["calculated_value"] is not None
            result[code.lower()+"_consecutive_breach_months"] = metric(
                number(streak) if latest_valid else None, unit="months",
                reason="Current month's saved indicator missing or unavailable." if not latest_valid else None,
                window_months=12, truncated=streak == 12)
            result[code.lower()+"_breach_frequency_12m_pct"] = metric(
                Decimal(breaches)/len(known)*100 if known else None,
                reason="No saved valid indicators in window." if not known else None,
                observed_months=len(known), window_months=12, breached_months=breaches,
                basis="Stored assessment breach flags and their historical thresholds, not recalculated flags.")
        return result


TOOL_DESCRIPTIONS = {
    "business_context": "Business metadata and the requested month's saved assessment and thresholds.",
    "revenue_metrics": "MoM, YoY, completed-quarter QoQ and YoY, rolling 3-month and trailing-year revenue growth.",
    "liquidity_metrics": "Overdue ratio, utilisation, headroom, monthly ratio changes and receivables/revenue growth gap.",
    "seasonality_metrics": "Exploratory same-calendar-month historical deviation and 12-month revenue volatility.",
    "breach_persistence": "Consecutive breaches and breach frequency from stored assessment indicator results."}
