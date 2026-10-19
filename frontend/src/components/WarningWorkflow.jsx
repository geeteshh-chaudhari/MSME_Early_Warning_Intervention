import { useState } from "react";
import { CircleAlert, X } from "lucide-react";
import { decideWarning, warningHistoryPath, warningsPath } from "../api/workflowApi";
import { useWorkflowAction, useWorkflowResource } from "../hooks/useWorkflowResource";
import { dateLabel, humanize, listOf } from "./workflowFormat";
import { ResourceState, WorkflowNotice, WorkflowPagination, WorkflowStatus } from "./WorkflowUi";

const statuses = ["OPEN", "UNDER_REVIEW", "ESCALATED", "INTERVENTION_ACTIVE", "CLOSED_REVIEWED", "CLOSED"];
const descriptions = {
  ACCEPT: "Move this warning to under review.",
  REJECT: "Close this warning as reviewed. Active interventions, if any, will be closed by the backend.",
  ESCALATE: "Send this warning to a Risk Admin for review.",
  RETURN: "Return this case to the RM; an existing active intervention stays active.",
  CONFIRM: "Confirm the escalation review and return the case to the RM workflow.",
  CLOSE: "Close this warning and any active interventions.",
};
function permittedDecisions(role, status, active) {
  if (!active) return [];
  if (role === "RM") return ["OPEN", "UNDER_REVIEW"].includes(status) ? ["ACCEPT", "REJECT", "ESCALATE"] : status === "INTERVENTION_ACTIVE" ? ["ESCALATE"] : [];
  return role === "RISK_ADMIN" && status === "ESCALATED" ? ["RETURN", "CONFIRM", "CLOSE"] : [];
}

function DecisionForm({ warning, choices, token, onSaved }) {
  const [decision, setDecision] = useState(choices[0]);
  const [reason, setReason] = useState("");
  const [reviewing, setReviewing] = useState(false);
  const [validationError, setValidationError] = useState("");
  const action = useWorkflowAction();
  function review(event) {
    event.preventDefault();
    if (!reason.trim()) { setValidationError("Enter a reason for this decision."); return; }
    setValidationError(""); setReviewing(true);
  }
  async function save() {
    const result = await action.execute(() => decideWarning(token, warning.warningId, { decision, reason: reason.trim() }), "Warning decision saved.");
    if (result.ok) onSaved(result.data);
    else setReviewing(false);
  }
  return <div className="workflow-inset workflow-decision"><h3>Record a decision</h3><WorkflowNotice {...action} error={validationError || action.error} />
    {reviewing ? <div className="snapshot-review"><h3>{humanize(decision)} warning #{warning.warningId}?</h3><p className="management-help">{descriptions[decision]}</p><p className="workflow-reason">{reason}</p><div className="snapshot-actions"><button className="button secondary" disabled={action.busy} onClick={() => setReviewing(false)}>Back to edit</button><button className="button primary" disabled={action.busy} onClick={save}>{action.busy ? "Saving…" : "Confirm decision"}</button></div></div>
      : <form onSubmit={review}><fieldset className="snapshot-fieldset" disabled={action.busy}><div className="snapshot-fields"><div className="snapshot-field"><label htmlFor="warning-decision">Decision</label><select id="warning-decision" value={decision} onChange={e => setDecision(e.target.value)}>{choices.map(c => <option key={c} value={c}>{humanize(c)}</option>)}</select><small className="management-help">{descriptions[decision]}</small></div><div className="snapshot-field"><label htmlFor="warning-reason">Decision reason</label><textarea id="warning-reason" value={reason} onChange={e => setReason(e.target.value)} maxLength={4000} required /></div></div><div className="snapshot-actions"><button className="button primary" type="submit">Review decision</button></div></fieldset></form>}
  </div>;
}

function WarningCase({ id, user, business, onChanged, onClose }) {
  const [revision, setRevision] = useState(0);
  const [historyPage, setHistoryPage] = useState(0);
  const [message, setMessage] = useState("");
  const resource = useWorkflowResource(`/warnings/${id}`, user.accessToken, revision);
  const history = useWorkflowResource(user.role !== "MSME_REP" ? warningHistoryPath(id, historyPage) : null, user.accessToken, revision);
  const warning = resource.data;
  const events = listOf(history.data);
  const choices = warning ? permittedDecisions(user.role, warning.warningStatus, business.isActive === "Y") : [];
  function saved(updated) {
    setMessage(`Decision recorded. Warning status: ${humanize(updated.warningStatus)}.`);
    setHistoryPage(0); setRevision(v => v + 1); onChanged(updated);
  }
  return <section className="panel" aria-label="Warning case details"><div className="panel-heading"><div><h2>Warning #{id}</h2><p>Case evidence, decisions and activity</p></div><button className="icon-button" onClick={onClose} aria-label="Close warning details"><X size={18} /></button></div>
    {message && <div className="workflow-inset notice snapshot-success" role="status">{message}</div>}
    <ResourceState resource={resource}>{warning && <>
      <div className="workflow-summary"><WorkflowStatus value={warning.warningStatus} /><span>Assessment #{warning.assessmentId}</span><span>{dateLabel(warning.createdAt)}</span></div>
      <div className="workflow-inset"><h3 className="workflow-title">{humanize(warning.primaryDriver)}</h3><p className="management-help">{warning.whyNowText}</p><p className="recommended-action">{warning.recommendedAction}</p></div>
      {choices.length > 0 ? <DecisionForm key={`${revision}:${warning.warningStatus}`} warning={warning} choices={choices} token={user.accessToken} onSaved={saved} />
        : user.role !== "MSME_REP" && <p className="workflow-inset management-help">{business.isActive !== "Y" ? "This business is inactive. Case changes are disabled." : "No decisions are available for your role in this warning's current status."}</p>}
    </>}</ResourceState>
    {user.role !== "MSME_REP" && <><div className="panel-heading"><div><h2>Activity history</h2><p>Recorded actors, decisions and reasons</p></div></div><ResourceState resource={history} empty={!events.length}><ol className="workflow-timeline">{events.map(event => <li key={event.activityId}><span className="workflow-timeline-dot" /><div><strong>{humanize(event.actionCode)}</strong><small>{dateLabel(event.createdAt)} · {humanize(event.actorType)} #{event.actorUserId}</small><p>{event.description}</p></div></li>)}</ol></ResourceState><WorkflowPagination page={historyPage} onPage={setHistoryPage} loading={history.loading} count={history.data?.totalElements} hasNext={!history.error && (history.data?.last === false || (history.data?.last == null && events.length === 10))} /></>}
  </section>;
}

export default function WarningWorkflow({ business, user, onWarningSaved }) {
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState("");
  const [revision, setRevision] = useState(0);
  const [caseId, setCaseId] = useState(null);
  const resource = useWorkflowResource(warningsPath(business.msmeId, page, status), user.accessToken, revision);
  const rows = listOf(resource.data);
  function changed(warning) { setRevision(v => v + 1); onWarningSaved(warning); }
  return <div className="workflow-stack"><section className="panel"><div className="panel-heading"><div><h2>Warnings & decisions</h2><p>{user.role === "MSME_REP" ? "Read-only warnings for your business." : "Open a warning to review evidence, make a permitted decision and view its history."}</p></div><div className="management-toolbar"><div className="snapshot-field"><label htmlFor="warning-filter">Status</label><select id="warning-filter" value={status} onChange={e => { setStatus(e.target.value); setPage(0); }}><option value="">All statuses</option>{statuses.map(s => <option value={s} key={s}>{humanize(s)}</option>)}</select></div><button className="button secondary" disabled={resource.loading} onClick={() => setRevision(v => v + 1)}>Reload warnings</button></div></div>
    <ResourceState resource={resource} empty={!rows.length}><div className="warning-list">{rows.map(w => <article className="warning-item" key={w.warningId}><div className="warning-symbol"><CircleAlert size={20} /></div><div className="warning-body"><div className="warning-title"><h3>{humanize(w.primaryDriver)}</h3><WorkflowStatus value={w.warningStatus} /></div><p>{w.whyNowText}</p>{w.recommendedAction && <p className="recommended-action">{w.recommendedAction}</p>}<small>{dateLabel(w.createdAt)} · Warning #{w.warningId}</small><button className="table-open" onClick={() => setCaseId(w.warningId)} aria-label={`Open warning ${w.warningId}`}>{user.role === "MSME_REP" ? "View details" : "Review case"}</button></div></article>)}</div></ResourceState>
    <WorkflowPagination page={page} onPage={setPage} loading={resource.loading} count={resource.data?.totalElements} hasNext={!resource.error && (resource.data?.last === false || (resource.data?.last == null && rows.length === 10))} />
  </section>{caseId && <WarningCase key={caseId} id={caseId} business={business} user={user} onChanged={changed} onClose={() => setCaseId(null)} />}</div>;
}
