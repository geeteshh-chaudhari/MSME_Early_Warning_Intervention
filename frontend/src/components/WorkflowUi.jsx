import { RefreshCw } from "lucide-react";
import { amountLabel, humanize } from "./workflowFormat";
import { assessmentMonths, previousIndicatorValue } from "./assessmentDisplay";

export function WorkflowStatus({ value }) {
  const tone = ["HEALTHY", "APPROVED", "CLOSED", "CLOSED_REVIEWED"].includes(value) ? "success"
    : ["HIGH_PRIORITY", "ESCALATED", "REJECTED"].includes(value) ? "danger" : "warning";
  return <span className={`badge ${tone}`}>{humanize(value)}</span>;
}
export function WorkflowNotice({ error, success, busy }) {
  return <>{error && <div className="notice error" role="alert">{error}</div>}
    {success && <div className="notice snapshot-success" role="status">{success}</div>}
    {busy && <p className="workflow-loading" role="status"><RefreshCw size={15} className="spin" />Saving…</p>}</>;
}
export function ResourceState({ resource, empty, children }) {
  if (resource.loading) return <div className="loading-state" role="status"><RefreshCw className="spin" size={20} />Loading…</div>;
  if (resource.error) return <div className="workflow-inset notice error" role="alert">{resource.error}</div>;
  if (empty) return <div className="empty-state"><h3>No records to display</h3><p>Records will appear here when available.</p></div>;
  return children;
}
export function WorkflowPagination({ page, onPage, loading, count, hasNext }) {
  return <div className="workflow-pagination">
    <span>Page {page + 1}{count != null ? ` · ${count} records` : ""}</span>
    <div><button type="button" className="button secondary" disabled={loading || page === 0} onClick={() => onPage(page - 1)}>Previous</button>
      <button type="button" className="button secondary" disabled={loading || !hasNext} onClick={() => onPage(page + 1)}>Next</button></div>
  </div>;
}
export function IndicatorTable({ indicators = [], reportingPeriod, previousIndicators = [], previousLoading = false }) {
  const [previousMonth, currentMonth] = assessmentMonths(reportingPeriod);
  return <div className="table-scroll"><table><thead><tr><th scope="col">Indicator</th><th scope="col">{previousMonth}<br />Calculated %</th><th scope="col">{currentMonth}<br />Calculated %</th><th scope="col">Threshold %</th><th scope="col">Result</th><th scope="col">Explanation</th></tr></thead>
    <tbody>{indicators.map(i => <tr key={i.indicatorCode}><td>{humanize(i.indicatorCode)}</td><td>{previousLoading && i.indicatorCode !== "REVENUE_DETERIORATION" ? "Loading…" : amountLabel(previousIndicatorValue(i, previousIndicators))}</td><td>{amountLabel(i.calculatedValue)}</td><td>{amountLabel(i.thresholdValueUsed)}</td>
      <td><span className={`badge ${i.breached ? "danger" : "success"}`}>{i.breached ? "Breached" : "Within threshold"}</span></td><td className="workflow-wrap">{i.explanation || "—"}</td></tr>)}</tbody></table></div>;
}
