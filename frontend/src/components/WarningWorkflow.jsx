import { useEffect, useRef, useState } from "react";
import { CircleAlert, X } from "lucide-react";
import InterventionWorkflow from "./InterventionWorkflow";
import { apiRequest } from "../api/client";
import { decideWarning, interventionsPath, warningHistoryPath, warningsPath, WARNING_PAGE_SIZE } from "../api/workflowApi";
import { useWorkflowAction, useWorkflowResource } from "../hooks/useWorkflowResource";
import { dateLabel, humanize, listOf, recommendationText } from "./workflowFormat";
import { ResourceState, WorkflowNotice, WorkflowPagination, WorkflowStatus } from "./WorkflowUi";

const statuses = ["OPEN", "UNDER_REVIEW", "ESCALATED", "INTERVENTION_ACTIVE", "CLOSED_REVIEWED", "CLOSED"];
const descriptions = {
  INTERVENE: "Record the RM action and append notes to this warning's intervention.",
  ESCALATE: "Append the escalation reason and enable Risk Admin advice on the same intervention.",
  CLOSE: "Close this warning explicitly and record the reason in its intervention.",
};
function permittedDecisions(role, status, active) {
  return active && role === "RM" && !["CLOSED", "CLOSED_REVIEWED"].includes(status)
    ? ["INTERVENE", "ESCALATE", "CLOSE"] : [];
}

function todayDate() {
  const date = new Date();
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

function DecisionForm({ warning, choices, token, onSaved }) {
  const [decision, setDecision] = useState(choices[0]);
  const [reason, setReason] = useState("");
  const [followUpDate, setFollowUpDate] = useState("");
  const [reviewing, setReviewing] = useState(false);
  const [validationError, setValidationError] = useState("");
  const action = useWorkflowAction();
  function review(event) {
    event.preventDefault();
    if (!reason.trim() || !followUpDate || followUpDate < todayDate()) { setValidationError("Enter notes and a follow-up date of today or later."); return; }
    setValidationError(""); setReviewing(true);
  }
  async function save() {
    const result = await action.execute(() => decideWarning(token, warning.warningId, { decision, reason: reason.trim(), followUpDate }), "Warning decision saved.");
    if (result.ok) onSaved(result.data);
    else setReviewing(false);
  }
  return <div className="workflow-inset workflow-decision"><h3>Record a decision</h3><WorkflowNotice {...action} error={validationError || action.error} />
    {reviewing ? <div className="snapshot-review"><h3>{humanize(decision)} warning #{warning.warningId}?</h3><p className="management-help">{descriptions[decision]}</p><p className="workflow-reason">{reason}</p><p>Follow-up: {followUpDate}</p><div className="snapshot-actions"><button className="button secondary" disabled={action.busy} onClick={() => setReviewing(false)}>Back to edit</button><button className="button primary" disabled={action.busy} onClick={save}>{action.busy ? "Saving…" : "Confirm decision"}</button></div></div>
      : <form onSubmit={review}><fieldset className="snapshot-fieldset" disabled={action.busy}><div className="snapshot-fields"><div className="snapshot-field"><label htmlFor="warning-decision">Decision</label><select id="warning-decision" value={decision} onChange={e => setDecision(e.target.value)}>{choices.map(c => <option key={c} value={c}>{c === "CLOSE" ? "Close warning" : humanize(c)}</option>)}</select><small className="management-help">{descriptions[decision]}</small></div><div className="snapshot-field"><label htmlFor="warning-reason">Notes / reason</label><textarea id="warning-reason" value={reason} onChange={e => setReason(e.target.value)} maxLength={4000} required /></div><div className="snapshot-field"><label htmlFor="warning-followup">Follow-up date</label><input id="warning-followup" type="date" min={todayDate()} value={followUpDate} onChange={e => setFollowUpDate(e.target.value)} required /></div></div><div className="snapshot-actions"><button className="button primary" type="submit">Review decision</button></div></fieldset></form>}
  </div>;
}

function WarningCase({ id, user, business, refreshVersion, onChanged, onClose, caseSectionRef }) {
  const [revision, setRevision] = useState(0);
  const [caseRevision, setCaseRevision] = useState(0);
  const [historyPage, setHistoryPage] = useState(0);
  const [message, setMessage] = useState("");
  const resource = useWorkflowResource(`/warnings/${id}`, user.accessToken, `${caseRevision}:${refreshVersion}`);
  const history = useWorkflowResource(user.role !== "MSME_REP" ? warningHistoryPath(id, historyPage) : null, user.accessToken, `${revision}:${refreshVersion}`);
  const interventionState = useWorkflowResource(interventionsPath(id, 0), user.accessToken, `${revision}:${refreshVersion}`);
  const legacyInterventions = interventionState.data?.totalElements > 1;
  const interventionClosed = listOf(interventionState.data).some(plan => plan.isActive !== "Y");
  const warning = resource.data;
  const events = listOf(history.data);
  const choices = warning && !legacyInterventions && !interventionState.loading && !interventionState.error
    ? permittedDecisions(user.role, warning.warningStatus, business.isActive === "Y").filter(choice => !interventionClosed || choice === "CLOSE") : [];
  function saved(updated) {
    setCaseRevision(v => v + 1);
    setMessage(`Decision recorded. Warning status: ${humanize(updated.warningStatus)}.`);
    setHistoryPage(0); setRevision(v => v + 1); onChanged(updated);
  }
  async function interventionChanged(message) {
    setMessage(message);
    setHistoryPage(0);
    setRevision(v => v + 1);
    try {
      const updated = await apiRequest(`/warnings/${id}`, { token: user.accessToken });
      setCaseRevision(v => v + 1);
      onChanged(updated);
    } catch {
      setMessage(`${message} The warning status could not be refreshed. Use Reload case to retry.`);
      onChanged();
    }
  }
  return <section ref={caseSectionRef} className="panel" aria-label="Warning case details" tabIndex={-1} style={{ scrollMarginTop: "24px" }}><div className="panel-heading"><div><h2>Warning #{id}</h2><p>Case evidence, decisions and activity</p></div><div className="management-toolbar"><button className="button secondary" disabled={resource.loading} onClick={() => { setCaseRevision(v => v + 1); setRevision(v => v + 1); }}>Reload case</button><button className="icon-button" onClick={onClose} aria-label="Close warning details"><X size={18} /></button></div></div>
    {message && <div className="workflow-inset notice snapshot-success" role="status">{message}</div>}
    <ResourceState resource={resource}>{warning && <>
      <div className="workflow-summary"><WorkflowStatus value={warning.warningStatus} /><span>Assessment #{warning.assessmentId}</span><span>{dateLabel(warning.createdAt)}</span></div>
      <div className="workflow-inset"><h3 className="workflow-title">{humanize(warning.primaryDriver)}</h3><p className="management-help">{warning.whyNowText}</p>{["RM", "RISK_ADMIN"].includes(user.role) && <p className="recommended-action">{recommendationText(warning.recommendedAction)}</p>}</div>
      {choices.length > 0 ? <DecisionForm key={`${revision}:${warning.warningStatus}`} warning={warning} choices={choices} token={user.accessToken} onSaved={saved} />
        : user.role !== "MSME_REP" && <p className="workflow-inset management-help">{business.isActive !== "Y" ? "This business is inactive. Case changes are disabled." : "No decisions are available for your role in this warning's current status."}</p>}
      {legacyInterventions && <p className="workflow-inset notice error">This historical warning has multiple interventions. Its history is preserved; consolidation is needed before new actions can be appended. Use a newly generated warning to test the updated workflow.</p>}
      {interventionState.error && <p className="workflow-inset notice error">Could not check intervention availability: {interventionState.error}</p>}
      <InterventionWorkflow warning={warning} business={business} user={user} refreshVersion={revision} onChanged={interventionChanged} />
    </>}</ResourceState>
    {user.role !== "MSME_REP" && <><div className="panel-heading"><div><h2>Activity history</h2><p>Recorded actors, decisions, reasons and case notes</p></div></div><ResourceState resource={history} empty={!events.length}><ol className="workflow-timeline">{events.map(event => <li key={event.activityId}><span className="workflow-timeline-dot" /><div><strong>{humanize(event.actionCode)}</strong><small>{dateLabel(event.createdAt)} · {humanize(event.actorType)} #{event.actorUserId}</small><p className="workflow-reason">{event.description}</p></div></li>)}</ol></ResourceState><WorkflowPagination page={historyPage} onPage={setHistoryPage} loading={history.loading} count={history.data?.totalElements} hasNext={!history.error && (history.data?.last === false || (history.data?.last == null && events.length === 10))} /></>}
  </section>;
}

export default function WarningWorkflow({ business, user, onWarningSaved }) {
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState("");
  const [revision, setRevision] = useState(0);
  const [caseId, setCaseId] = useState(null);
  const caseSectionRef = useRef(null);
  function reviewCase(id) {
    setCaseId(id);
    requestAnimationFrame(() => {
      const section = caseSectionRef.current;
      if (!section) return;
      section.focus({ preventScroll: true });
      section.scrollIntoView({
        behavior: window.matchMedia("(prefers-reduced-motion: reduce)").matches ? "instant" : "smooth",
        block: "start",
      });
    });
  }
  useEffect(() => {
    function refresh() {
      if (document.visibilityState === "visible") setRevision(value => value + 1);
    }
    window.addEventListener("focus", refresh);
    document.addEventListener("visibilitychange", refresh);
    return () => {
      window.removeEventListener("focus", refresh);
      document.removeEventListener("visibilitychange", refresh);
    };
  }, []);
  const resource = useWorkflowResource(warningsPath(business.msmeId, page, status), user.accessToken, revision);
  const rows = listOf(resource.data);
  function changed(warning) { setRevision(v => v + 1); if (warning) onWarningSaved(warning); }
  return <div className="workflow-stack"><section className="panel"><div className="panel-heading"><div><h2>Warnings & decisions</h2><p>{user.role === "MSME_REP" ? "Read-only warnings for your business." : "Open a warning to review evidence, make a permitted decision and view its history."}</p></div><div className="management-toolbar"><div className="snapshot-field"><label htmlFor="warning-filter">Status</label><select id="warning-filter" value={status} onChange={e => { setStatus(e.target.value); setPage(0); }}><option value="">All statuses</option>{statuses.map(s => <option value={s} key={s}>{humanize(s)}</option>)}</select></div><button className="button secondary" disabled={resource.loading} onClick={() => setRevision(v => v + 1)}>Reload warnings</button></div></div>
    <ResourceState resource={resource} empty={!rows.length}><div className="warning-list">{rows.map(w => <article className="warning-item" key={w.warningId}><div className="warning-symbol"><CircleAlert size={20} /></div><div className="warning-body"><div className="warning-title"><h3>{humanize(w.primaryDriver)}</h3><WorkflowStatus value={w.warningStatus} /></div><p>{w.whyNowText}</p>{["RM", "RISK_ADMIN"].includes(user.role) && w.recommendedAction && <p className="recommended-action">{recommendationText(w.recommendedAction)}</p>}<small>{dateLabel(w.createdAt)} · Warning #{w.warningId}</small><button className="table-open" onClick={() => reviewCase(w.warningId)} aria-label={`Open warning ${w.warningId}`}>{user.role === "MSME_REP" ? "View details" : "Review case"}</button></div></article>)}</div></ResourceState>
    <WorkflowPagination page={page} onPage={setPage} loading={resource.loading} count={resource.data?.totalElements} hasNext={!resource.error && (resource.data?.last === false || (resource.data?.last == null && rows.length === WARNING_PAGE_SIZE))} />
  </section>{caseId && <WarningCase key={caseId} id={caseId} caseSectionRef={caseSectionRef} business={business} user={user} refreshVersion={revision} onChanged={changed} onClose={() => setCaseId(null)} />}</div>;
}
