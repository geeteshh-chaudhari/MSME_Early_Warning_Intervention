export const metricGroups = {
  revenue_metrics: "Revenue growth",
  liquidity_metrics: "Liquidity & credit",
  seasonality_metrics: "Seasonal context",
  breach_persistence: "Saved breach history",
};

export function metricLabel(key) {
  const names = {
    revenue_mom_growth_pct: "Revenue growth · month over month",
    revenue_yoy_growth_pct: "Revenue growth · year over year",
    revenue_qoq_growth_pct: "Revenue growth · completed quarter",
    revenue_quarter_yoy_growth_pct: "Quarterly revenue · year over year",
    revenue_rolling_3m_growth_pct: "Revenue growth · rolling 3 months",
    revenue_ttm_growth_pct: "Revenue growth · trailing 12 months",
    overdue_ratio_pct: "Overdue receivables ratio",
    credit_utilisation_pct: "Credit utilisation",
    credit_headroom_pct: "Credit headroom",
    overdue_ratio_change_pp: "Overdue ratio · monthly change",
    credit_utilisation_change_pp: "Credit utilisation · monthly change",
    receivables_yoy_growth_pct: "Receivables growth · year over year",
    receivables_vs_revenue_growth_gap_pp: "Receivables vs revenue · growth gap",
    same_month_historical_deviation_pct: "Revenue vs prior matching months",
    revenue_volatility_12m_pct: "Revenue volatility · 12 months",
  };
  return names[key] || key.replace(/_pct$/, " (%)").replaceAll("_", " ");
}

export function finiteValue(value) {
  return value !== null && value !== undefined && value !== "" && Number.isFinite(Number(value)) ? Number(value) : null;
}
export function metricNumber(item) {
  return item?.status === "available" ? finiteValue(item.value) : null;
}
export function metricValue(item) {
  const value = metricNumber(item);
  if (value === null) return "Unavailable";
  const unit = item.unit === "percent" ? "%" : item.unit === "percentage_points" ? " pp" : item.unit === "months" ? " months" : "";
  return `${new Intl.NumberFormat("en-IN", { maximumFractionDigits: 2 }).format(value)}${unit}`;
}
export function flattenEvidence(evidence = {}) {
  return Object.entries(evidence).filter(([group]) => group !== "business_context").flatMap(([group, items]) =>
    Object.entries(items || {}).filter(([, item]) => item && typeof item === "object" && "status" in item).map(([key, item]) => ({ id: `${group}.${key}`, group, key, item })));
}
export const monthLabel = month => new Date(`${String(month).slice(0, 7)}-01T00:00:00`).toLocaleDateString("en-IN", { month: "short", year: "numeric" });
