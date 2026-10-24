export function assessmentMonths(reportingPeriod) {
  const match = /^(\d{4})-(\d{2})(?:-|$)/.exec(String(reportingPeriod || ""));
  if (!match || Number(match[2]) < 1 || Number(match[2]) > 12) {
    return ["Previous month", "Reporting month"];
  }
  const year = Number(match[1]);
  const month = Number(match[2]) - 1;
  const format = new Intl.DateTimeFormat("en-IN", {
    month: "short", year: "numeric", timeZone: "UTC",
  });
  return [month - 1, month].map(offset => format.format(new Date(Date.UTC(year, offset, 1))));
}

export function previousRevenueDecline(indicator) {
  if (indicator.indicatorCode !== "REVENUE_DETERIORATION") return null;
  // Read the saved first-period evidence; do not recalculate historical results.
  const match = /^Revenue decline: first period\s+(-?\d+(?:\.\d+)?)(?:%)?\s*;/i.exec(indicator.explanation || "");
  return match ? Number(match[1]) : null;
}

export function previousMonthIndicators(current, previous) {
  if (!current || !previous || current.msmeId !== previous.msmeId) return [];
  const match = /^(\d{4})-(\d{2})(?:-|$)/.exec(String(current.reportingPeriod || ""));
  if (!match || Number(match[2]) < 1 || Number(match[2]) > 12) return [];
  const expected = new Date(Date.UTC(Number(match[1]), Number(match[2]) - 2, 1))
    .toISOString().slice(0, 7);
  return String(previous.reportingPeriod || "").slice(0, 7) === expected
    ? previous.indicators || [] : [];
}

export function previousIndicatorValue(indicator, previousIndicators = []) {
  if (indicator.indicatorCode === "REVENUE_DETERIORATION") {
    return previousRevenueDecline(indicator);
  }
  return previousIndicators.find(item => item.indicatorCode === indicator.indicatorCode)?.calculatedValue ?? null;
}
