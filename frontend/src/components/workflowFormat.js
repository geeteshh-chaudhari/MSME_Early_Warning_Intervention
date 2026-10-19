export const humanize = value => value == null || value === "" ? "—" : String(value).replaceAll("_", " ");
export function dateLabel(value) {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "—" : date.toLocaleString("en-IN", { dateStyle: "medium", timeStyle: "short" });
}
export const amountLabel = value => value == null ? "—" : new Intl.NumberFormat("en-IN", { maximumFractionDigits: 2 }).format(Number(value));
export const listOf = data => Array.isArray(data) ? data : data?.content || [];
