export const humanize = value => value == null || value === "" ? "—" : String(value).replaceAll("_", " ");
export function dateLabel(value) {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "—" : date.toLocaleString("en-IN", { dateStyle: "medium", timeStyle: "short" });
}
export const amountLabel = value => value == null ? "—" : new Intl.NumberFormat("en-IN", { maximumFractionDigits: 2 }).format(Number(value));
export const listOf = data => Array.isArray(data) ? data : data?.content || [];

// The database retains model JSON. Formatting belongs only to the presentation layer.
export function recommendationText(value) {
  if (!value) return "Recommendation pending.";
  let data;
  try { data = typeof value === "string" ? JSON.parse(value) : value; }
  catch { return value; } // Preserve readability of historical plain-text records.
  if (!data || typeof data !== "object") return String(value);
  const recommendation = data.recommendation || data;
  if (recommendation.status === "pending") return "Recommendation pending.";
  if (recommendation.status === "unavailable") return "Recommendation service unavailable.";
  const lines = (recommendation.actions || []).map((action, index) =>
    `${index + 1}. ${action.title ? `${action.title}: ` : ""}${action.suggested_action || ""}`);
  return [recommendation.summary, ...lines].filter(Boolean).join("\n\n") || "No recommended actions available.";
}
