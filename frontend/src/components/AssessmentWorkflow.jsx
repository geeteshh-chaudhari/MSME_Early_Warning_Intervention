import { useState } from "react";
import { Activity, ArrowRight, Plus, X } from "lucide-react";
import { assessmentHistoryPath, createWarning, runAssessment } from "../api/workflowApi";
import { useWorkflowAction, useWorkflowResource } from "../hooks/useWorkflowResource";
import { amountLabel, dateLabel, humanize, listOf } from "./workflowFormat";
import { IndicatorTable, ResourceState, WorkflowNotice, WorkflowPagination, WorkflowStatus } from "./WorkflowUi";

function AssessmentDetails({ id, token, active, onWarningSaved, onClose }) {
  const resource = useWorkflowResource(`/assessment/assessments/${id}`, token);
  const action = useWorkflowAction();
  async function raiseWarning() {
    const result = await action.execute(() => createWarning(token, id), "Warning created. Open Warnings to review and decide the next action.");
    if (result.ok) onWarningSaved(result.data);
  }
  const detail = resource.data;
  return <section className="panel" aria-label="Assessment details">
    <div className="panel-heading"><div><h2>Assessment #{id}</h2><p>Saved evidence and applied thresholds</p></div><button className="icon-button" onClick={onClose} aria-label="Close assessment details"><X size={18} /></button></div>
    <ResourceState resource={resource}>
      {detail && <>
        <div className="workflow-summary"><WorkflowStatus value={detail.severity} /><span>{dateLabel(detail.assessmentDate)}</span><span>Pattern: {humanize(detail.stressPattern)}</span><span>Threshold #{detail.thresholdConfigId}</span></div>
        <IndicatorTable indicators={detail.indicators} />
        <div className="workflow-inset"><WorkflowNotice {...action} />
          {detail.severity !== "HEALTHY" && <><p className="management-help">Create a warning from these saved breaches. The backend prevents duplicate warnings for the same assessment.</p>
            <button className="button secondary" disabled={!active || action.busy || !!action.success || !detail.indicators?.some(i => i.breached)} onClick={raiseWarning}><Plus size={15} />Create warning</button></>}
        </div>
      </>}
    </ResourceState>
  </section>;
}

function AssessmentComparison({ selection, token }) {
  const path = `/assessment/assessments/compare?beforeId=${selection.before.assessmentId}&afterId=${selection.after.assessmentId}`;
  const resource = useWorkflowResource(path, token, selection.revision);
  const data = resource.data;
  return <section className="panel" aria-label="Assessment comparison result"><div className="panel-heading"><div><h2>Before / after comparison</h2><p>#{selection.before.assessmentId} → #{selection.after.assessmentId}</p></div></div>
    <ResourceState resource={resource}>{data && <>
      <div className="workflow-summary"><WorkflowStatus value={data.beforeSeverity} /><ArrowRight size={15} /><WorkflowStatus value={data.afterSeverity} /><strong>{humanize(data.outcome)}</strong></div>
      <p className="workflow-inset management-help">{data.explanation}</p>
      <div className="table-scroll"><table><thead><tr><th>Indicator</th><th>Before %</th><th>After %</th><th>Before threshold %</th><th>After threshold %</th><th>Change (pp)</th><th>Before breach</th><th>After breach</th><th>Improved?</th></tr></thead><tbody>
        {data.indicators?.map(i => <tr key={i.indicatorCode}><td>{humanize(i.indicatorCode)}</td><td>{amountLabel(i.beforeValue)}</td><td>{amountLabel(i.afterValue)}</td><td>{amountLabel(i.beforeThreshold)}</td><td>{amountLabel(i.afterThreshold)}</td><td>{amountLabel(i.change)}</td><td>{i.beforeBreached ? "Yes" : "No"}</td><td>{i.afterBreached ? "Yes" : "No"}</td><td>{i.improved ? "Yes" : "No"}</td></tr>)}
      </tbody></table></div>
    </>}</ResourceState>
  </section>;
}

export default function AssessmentWorkflow({ business, user, snapshots, onAssessmentSaved, onWarningSaved }) {
  const [page, setPage] = useState(0);
  const [revision, setRevision] = useState(0);
  const [month, setMonth] = useState("");
  const [detailId, setDetailId] = useState(null);
  const [before, setBefore] = useState(null);
  const [after, setAfter] = useState(null);
  const [comparison, setComparison] = useState(null);
  const [comparisonError, setComparisonError] = useState("");
  const action = useWorkflowAction();
  const resource = useWorkflowResource(assessmentHistoryPath(business.msmeId, page), user.accessToken, revision);
  const rows = listOf(resource.data);
  const months = [...new Set(snapshots.map(s => String(s.reportingPeriod).slice(0, 7)))].sort().reverse();
  const active = business.isActive === "Y";
  async function submit(event) {
    event.preventDefault();
    if (!month || !active) return;
    const result = await action.execute(() => runAssessment(user.accessToken, business.msmeId, month), data => `Assessment #${data.assessmentId} is ready. If this month was already assessed, the existing result is returned.`);
    if (result.ok) {
      setPage(0); setRevision(v => v + 1); setDetailId(result.data.assessmentId);
      onAssessmentSaved(result.data);
    }
  }
  function choose(position, item) {
    if (position === "before") setBefore(item); else setAfter(item);
    setComparison(null); setComparisonError("");
  }
  function compare() {
    if (!before || !after) { setComparisonError("Select both a before and an after assessment from the history rows."); return; }
    if (before.assessmentId === after.assessmentId) { setComparisonError("Choose two different assessments."); return; }
    if (before.msmeId !== after.msmeId || new Date(before.assessmentDate) >= new Date(after.assessmentDate)) {
      setComparisonError("Both assessments must belong to this business, with Before earlier than After."); return;
    }
    setComparisonError(""); setComparison(previous => ({ before, after, revision: (previous?.revision || 0) + 1 }));
  }

  return <div className="workflow-stack">
    <section className="panel"><div className="panel-heading"><div><h2>Run an assessment</h2><p>Use submitted financial data for {business.businessName}.</p></div><Activity size={21} className="teal" /></div>
      <form className="workflow-inset" onSubmit={submit}><WorkflowNotice {...action} />
        <p className="snapshot-description">An approved effective threshold and the required three-month financial history must be available. Existing assessments are reused without recalculating saved results.</p>
        {!active && <p className="notice error">This business is inactive. New assessments are disabled.</p>}
        <fieldset className="snapshot-fieldset workflow-form-row" disabled={action.busy || !active}>
          <div className="snapshot-field"><label htmlFor="assessment-month">Reporting month</label><select id="assessment-month" value={month} onChange={e => setMonth(e.target.value)} required><option value="">Choose a submitted month</option>{months.map(m => <option key={m} value={m}>{m}</option>)}</select></div>
          <button type="submit" className="button primary" disabled={!months.length}>{action.busy ? "Running…" : "Run assessment"}</button>
        </fieldset>
        {!months.length && <p className="management-help">No submitted financial months are loaded. Check Financial history before running an assessment.</p>}
      </form>
    </section>
    {detailId && <AssessmentDetails key={`${detailId}:${revision}`} id={detailId} token={user.accessToken} active={active} onWarningSaved={onWarningSaved} onClose={() => setDetailId(null)} />}
    <section className="panel"><div className="panel-heading"><div><h2>Assessment history</h2><p>Open details or select records for comparison. Selections are kept when changing pages.</p></div><button className="button secondary" onClick={() => setRevision(v => v + 1)} disabled={resource.loading}>Reload history</button></div>
      <ResourceState resource={resource} empty={!rows.length}><div className="table-scroll"><table><thead><tr><th>Assessment</th><th>Date</th><th>Severity</th><th>Stress pattern</th><th>Actions</th></tr></thead><tbody>
        {rows.map(item => <tr key={item.assessmentId}><td>#{item.assessmentId}</td><td>{dateLabel(item.assessmentDate)}</td><td><WorkflowStatus value={item.severity} /></td><td>{humanize(item.stressPattern)}</td><td><div className="management-row-actions">
          <button className="table-open" onClick={() => setDetailId(item.assessmentId)} aria-label={`Details for assessment ${item.assessmentId}`}>Details</button>
          <button className="table-open" aria-pressed={before?.assessmentId === item.assessmentId} onClick={() => choose("before", item)} aria-label={`Use assessment ${item.assessmentId} as before`}>Before</button>
          <button className="table-open" aria-pressed={after?.assessmentId === item.assessmentId} onClick={() => choose("after", item)} aria-label={`Use assessment ${item.assessmentId} as after`}>After</button>
        </div></td></tr>)}
      </tbody></table></div></ResourceState>
      <WorkflowPagination page={page} onPage={setPage} loading={resource.loading} count={resource.error ? undefined : rows.length} hasNext={!resource.error && rows.length === 10} />
    </section>
    <section className="panel"><div className="panel-heading"><div><h2>Compare assessments</h2><p>Select Before and After from any history page.</p></div></div><div className="workflow-inset">
      <div className="workflow-selection"><span>Before: <strong>{before ? `#${before.assessmentId} · ${dateLabel(before.assessmentDate)}` : "Not selected"}</strong></span><span>After: <strong>{after ? `#${after.assessmentId} · ${dateLabel(after.assessmentDate)}` : "Not selected"}</strong></span></div>
      {comparisonError && <p className="notice error" role="alert">{comparisonError}</p>}
      <div className="snapshot-actions"><button className="button secondary" onClick={() => { setBefore(null); setAfter(null); setComparison(null); setComparisonError(""); }}>Clear selection</button><button className="button primary" onClick={compare}>Compare assessments</button></div>
    </div></section>
    {comparison && <AssessmentComparison selection={comparison} token={user.accessToken} />}
  </div>;
}
