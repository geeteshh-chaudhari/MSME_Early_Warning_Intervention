import { useState } from "react";
import { ShieldCheck, X } from "lucide-react";
import { createThreshold, reviewThreshold, thresholdHistoryPath } from "../api/workflowApi";
import { useWorkflowAction, useWorkflowResource } from "../hooks/useWorkflowResource";
import { amountLabel, dateLabel, listOf } from "./workflowFormat";
import { ResourceState, WorkflowNotice, WorkflowPagination, WorkflowStatus } from "./WorkflowUi";

function ThresholdProposal({ business, token, onSaved }) {
  const [values, setValues] = useState({ revenueDeclinePct: "", overdueReceivablePct: "", creditUtilisationPct: "", effectiveFrom: "" });
  const [validationError, setValidationError] = useState("");
  const action = useWorkflowAction();
  async function submit(event) {
    event.preventDefault(); setValidationError("");
    const fields = ["revenueDeclinePct", "overdueReceivablePct", "creditUtilisationPct"];
    if (fields.some(name => values[name] === "" || !Number.isFinite(Number(values[name])) || Number(values[name]) < 0) || Number(values.revenueDeclinePct) > 100 || Number(values.overdueReceivablePct) > 100 || Number(values.creditUtilisationPct) > 999.99) {
      setValidationError("Enter valid percentages: revenue and overdue from 0 to 100; credit utilisation from 0 to 999.99."); return;
    }
    const body = { msmeId: business.msmeId, revenueDeclinePct: Number(values.revenueDeclinePct), overdueReceivablePct: Number(values.overdueReceivablePct), creditUtilisationPct: Number(values.creditUtilisationPct), effectiveFrom: values.effectiveFrom ? `${values.effectiveFrom}:00` : null };
    const result = await action.execute(() => createThreshold(token, body), data => `Version ${data.versionNo} proposed. It must be approved before it can be used for new assessments.`);
    if (result.ok) { setValues({ revenueDeclinePct: "", overdueReceivablePct: "", creditUtilisationPct: "", effectiveFrom: "" }); onSaved(); }
  }
  return <section className="panel"><div className="panel-heading"><div><h2>Propose thresholds</h2><p>Create a new version for {business.businessName}.</p></div><ShieldCheck size={21} className="teal" /></div>
    <form className="workflow-inset" onSubmit={submit}><WorkflowNotice {...action} error={validationError || action.error} />
      <p className="snapshot-description">Approved thresholds apply to new calculations. Previously saved assessments keep the thresholds used at the time.</p>
      <fieldset className="snapshot-fieldset" disabled={action.busy}>
        <div className="snapshot-fields">{[["revenueDeclinePct", "Revenue decline (%)", 100], ["overdueReceivablePct", "Overdue receivables (%)", 100], ["creditUtilisationPct", "Credit utilisation (%)", 999.99]].map(([name, label, max]) => <div className="snapshot-field" key={name}><label htmlFor={name}>{label}</label><input id={name} type="number" min="0" max={max} step="0.01" value={values[name]} onChange={e => setValues(v => ({ ...v, [name]: e.target.value }))} required /></div>)}
          <div className="snapshot-field"><label htmlFor="threshold-effective">Effective from (optional)</label><input id="threshold-effective" type="datetime-local" value={values.effectiveFrom} onChange={e => setValues(v => ({ ...v, effectiveFrom: e.target.value }))} /><small className="management-help">Leave blank to use submission time. Approval is also required.</small></div>
        </div><div className="snapshot-actions"><button className="button primary" type="submit">{action.busy ? "Submitting…" : "Submit proposal"}</button></div>
      </fieldset>
    </form>
  </section>;
}

function ThresholdReview({ threshold, token, onSaved, onCancel }) {
  const [decision, setDecision] = useState("APPROVED");
  const [reason, setReason] = useState("");
  const [validationError, setValidationError] = useState("");
  const action = useWorkflowAction();
  async function submit(event) {
    event.preventDefault();
    if (!reason.trim()) { setValidationError("Enter a review reason."); return; }
    setValidationError("");
    const result = await action.execute(() => reviewThreshold(token, threshold.thresholdConfigId, { decision, reason: reason.trim() }), "Threshold review saved.");
    if (result.ok) onSaved(result.data);
  }
  return <section className="panel" aria-label="Review threshold"><div className="panel-heading"><div><h2>Review threshold version {threshold.versionNo}</h2><p>Revenue {amountLabel(threshold.revenueDeclinePct)}% · Overdue {amountLabel(threshold.overdueReceivablePct)}% · Credit {amountLabel(threshold.creditUtilisationPct)}%</p></div><button className="icon-button" disabled={action.busy} onClick={onCancel} aria-label="Close threshold review"><X size={18} /></button></div>
    <form className="workflow-inset" onSubmit={submit}><WorkflowNotice {...action} error={validationError || action.error} /><fieldset className="snapshot-fieldset" disabled={action.busy}><div className="snapshot-fields">
      <div className="snapshot-field"><label htmlFor="threshold-decision">Review decision</label><select id="threshold-decision" value={decision} onChange={e => setDecision(e.target.value)}><option value="APPROVED">Approve</option><option value="REJECTED">Reject</option></select></div>
      <div className="snapshot-field"><label htmlFor="threshold-reason">Review reason</label><textarea id="threshold-reason" value={reason} maxLength={2000} onChange={e => setReason(e.target.value)} required /></div>
    </div><div className="snapshot-actions"><button className="button secondary" type="button" onClick={onCancel}>Cancel</button><button className="button primary" type="submit">{action.busy ? "Saving…" : decision === "APPROVED" ? "Approve threshold" : "Reject threshold"}</button></div></fieldset></form>
  </section>;
}

export default function ThresholdManagement({ business, user }) {
  const [page, setPage] = useState(0);
  const [revision, setRevision] = useState(0);
  const [reviewing, setReviewing] = useState(null);
  const [message, setMessage] = useState("");
  const resource = useWorkflowResource(thresholdHistoryPath(business.msmeId, page), user.accessToken, revision);
  const rows = listOf(resource.data);
  const admin = user.role === "RISK_ADMIN";
  function reload() { setRevision(v => v + 1); }
  return <div className="workflow-stack">
    {admin && <ThresholdProposal business={business} token={user.accessToken} onSaved={() => { setPage(0); reload(); }} />}
    {message && <div className="notice snapshot-success" role="status">{message}</div>}
    {admin && reviewing && <ThresholdReview key={reviewing.thresholdConfigId} threshold={reviewing} token={user.accessToken} onCancel={() => setReviewing(null)} onSaved={data => { setReviewing(null); setMessage(`Threshold version ${data.versionNo}: ${data.status.toLowerCase()}.`); reload(); }} />}
    <section className="panel"><div className="panel-heading"><div><h2>Threshold history</h2><p>{admin ? "Only pending proposals can be approved or rejected." : "Read-only view of threshold versions. Risk Admins manage proposals and reviews."}</p></div><button className="button secondary" disabled={resource.loading} onClick={reload}>Reload thresholds</button></div>
      <ResourceState resource={resource} empty={!rows.length}><div className="table-scroll"><table><thead><tr><th>Version</th><th>Revenue %</th><th>Overdue %</th><th>Credit %</th><th>Status</th><th>Effective from</th><th>Review</th></tr></thead><tbody>
        {rows.map(t => <tr key={t.thresholdConfigId}><td>v{t.versionNo} · #{t.thresholdConfigId}</td><td>{amountLabel(t.revenueDeclinePct)}</td><td>{amountLabel(t.overdueReceivablePct)}</td><td>{amountLabel(t.creditUtilisationPct)}</td><td><WorkflowStatus value={t.status} /></td><td>{dateLabel(t.effectiveFrom)}</td><td className="workflow-wrap">
          {admin && t.status === "PENDING" && <button className="table-open" onClick={() => { setMessage(""); setReviewing(t); }} aria-label={`Review threshold version ${t.versionNo}`}>Review proposal</button>}
          <details className="workflow-audit"><summary>Version details</summary><p>Proposed by #{t.proposedByUserId} · {dateLabel(t.proposedAt)}</p><p>Reviewed by {t.reviewedByUserId ? `#${t.reviewedByUserId}` : "—"} · {dateLabel(t.reviewedAt)}</p><p>{t.reviewReason || "No review reason recorded."}</p></details>
        </td></tr>)}
      </tbody></table></div></ResourceState>
      <WorkflowPagination page={page} onPage={setPage} loading={resource.loading} count={resource.error ? undefined : rows.length} hasNext={!resource.error && rows.length === 10} />
    </section>
  </div>;
}
