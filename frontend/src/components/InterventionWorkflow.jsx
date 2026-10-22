import { useState } from "react";
import { CalendarDays, Check, ClipboardList, Pencil, Plus, X } from "lucide-react";
import { assessmentHistoryPath, createIntervention, followUpsPath, interventionsPath, recordFollowUp, updateIntervention } from "../api/workflowApi";
import { useWorkflowAction, useWorkflowResource } from "../hooks/useWorkflowResource";
import { dateLabel, humanize, listOf } from "./workflowFormat";
import { ResourceState, WorkflowNotice, WorkflowPagination, WorkflowStatus } from "./WorkflowUi";

function todayDate() {
  const date = new Date();
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

function validFollowUpDate(value) {
  return /^\d{4}-\d{2}-\d{2}$/.test(value) && value >= todayDate();
}

function PlanForm({ warningId, token, intervention, revising, onSaved, onCancel }) {
  const [actionText, setActionText] = useState(intervention?.actionText || "");
  const [notes, setNotes] = useState(intervention?.notes || "");
  const [date, setDate] = useState(intervention?.followUpDate || "");
  const [error, setError] = useState("");
  const action = useWorkflowAction();
  const editing = !!intervention;
  async function submit(event) {
    event.preventDefault();
    if ((!editing && !actionText.trim()) || (editing && !notes.trim()) || !validFollowUpDate(date)) {
      setError("Enter the required plan details and a follow-up date of today or later."); return;
    }
    setError("");
    const result = await action.execute(() => editing
      ? updateIntervention(token, intervention.interventionId, { notes: notes.trim(), followUpDate: date })
      : createIntervention(token, warningId, { actionText: actionText.trim(), notes: notes.trim() || null, followUpDate: date }), "Intervention saved.");
    if (result.ok) onSaved(result.data, editing ? "updated" : revising ? "revised" : "created");
  }
  return <section className="intervention-editor" aria-label={editing ? "Edit intervention" : "Create intervention"}>
    <div className="management-subheading"><h3>{editing ? `Update intervention #${intervention.interventionId}` : revising ? "Revise the intervention plan" : "Create an intervention plan"}</h3><button className="icon-button" disabled={action.busy} onClick={onCancel} aria-label="Close intervention form"><X size={18} /></button></div>
    <p className="management-help">{editing ? "Update notes and the next follow-up date. The original action plan is retained." : revising ? "This starts a new intervention cycle and closes the previous active plan while preserving its reviews." : "Describe the action agreed with this business and schedule a follow-up."}</p>
    <form onSubmit={submit}><WorkflowNotice {...action} error={error || action.error} /><fieldset className="snapshot-fieldset" disabled={action.busy}>
      {!editing && <div className="snapshot-field"><label htmlFor="intervention-action">Action plan</label><textarea id="intervention-action" value={actionText} onChange={e => setActionText(e.target.value)} maxLength={4000} required /></div>}
      <div className="snapshot-fields intervention-form-fields"><div className="snapshot-field"><label htmlFor="intervention-notes">{editing ? "Update notes" : "Notes (optional)"}</label><textarea id="intervention-notes" value={notes} onChange={e => setNotes(e.target.value)} maxLength={4000} required={editing} /></div>
        <div className="snapshot-field"><label htmlFor="intervention-date">Follow-up date</label><input id="intervention-date" type="date" min={todayDate()} value={date} onChange={e => setDate(e.target.value)} required /></div></div>
      <div className="snapshot-actions"><button className="button secondary" type="button" onClick={onCancel}>Cancel</button><button className="button primary" type="submit">{action.busy ? "Saving…" : editing ? "Save intervention update" : revising ? "Save revised intervention" : "Create intervention"}</button></div>
    </fieldset></form>
  </section>;
}

function FollowUpForm({ intervention, warning, business, token, latestReview, onSaved, onCancel }) {
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState(null);
  const [outcome, setOutcome] = useState("IMPROVING");
  const [notes, setNotes] = useState("");
  const [keepActive, setKeepActive] = useState(true);
  const [nextDate, setNextDate] = useState("");
  const [reviewing, setReviewing] = useState(false);
  const [error, setError] = useState("");
  const action = useWorkflowAction();
  const snapshots = useWorkflowResource(`/snapshots/msme/${business.msmeId}`, token);
  const baseline = useWorkflowResource(`/assessment/assessments/${warning.assessmentId}`, token);
  const previous = useWorkflowResource(latestReview ? `/assessment/assessments/${latestReview.assessmentId}` : null, token);
  const history = useWorkflowResource(assessmentHistoryPath(business.msmeId, page), token);
  const rows = listOf(history.data);
  const periods = new Map(listOf(snapshots.data).map(s => [s.snapshotId, String(s.reportingPeriod).slice(0, 7)]));
  const baselineMonth = periods.get(baseline.data?.snapshotId);
  const previousMonth = latestReview ? periods.get(previous.data?.snapshotId) : null;
  const creationMonth = String(intervention.createdAt).slice(0, 7);
  const currentMonth = todayDate().slice(0, 7);
  const evidenceLoading = snapshots.loading || baseline.loading || previous.loading;
  const evidenceError = snapshots.error || baseline.error || previous.error;
  const evidenceReady = !evidenceLoading && !evidenceError && !!baselineMonth && (!latestReview || !!previousMonth);
  function eligible(assessment) {
    const month = periods.get(assessment.snapshotId);
    return evidenceReady && assessment.msmeId === business.msmeId && month && month > baselineMonth && month > creationMonth && month <= currentMonth && (!previousMonth || month > previousMonth) && new Date(assessment.assessmentDate) <= new Date();
  }
  function review(event) {
    event.preventDefault();
    if (!selected || !eligible(selected)) { setError("Choose eligible assessment evidence from a later reporting month."); return; }
    if (!notes.trim()) { setError("Enter review notes."); return; }
    if (!keepActive && !["IMPROVING", "STABILISING"].includes(outcome)) { setError("Only improving or stabilising outcomes can close the case."); return; }
    if (keepActive && !validFollowUpDate(nextDate)) { setError("Choose the next follow-up date, today or later."); return; }
    setError(""); setReviewing(true);
  }
  async function save() {
    if (!selected || !eligible(selected)) { setError("The selected evidence is not eligible. Choose another assessment."); setReviewing(false); return; }
    const result = await action.execute(() => recordFollowUp(token, intervention.interventionId, {
      assessmentId: selected.assessmentId, outcome, reviewNotes: notes.trim(), keepInterventionActive: keepActive, nextFollowUpDate: keepActive ? nextDate : null,
    }), "Follow-up saved.");
    if (result.ok) onSaved(result.data); else setReviewing(false);
  }
  return <section className="intervention-editor" aria-label="Record follow-up">
    <div className="management-subheading"><h3>Follow-up for intervention #{intervention.interventionId}</h3><button className="icon-button" disabled={action.busy} onClick={onCancel} aria-label="Close follow-up form"><X size={18} /></button></div>
    <p className="snapshot-description">Use a reporting month later than the baseline ({baselineMonth || "loading…"}), the intervention creation month ({creationMonth}), and any previous review{previousMonth ? ` (${previousMonth})` : ""}. Future evidence is not permitted.</p>
    {creationMonth >= currentMonth && <div className="notice workflow-info">This intervention was created this month. A follow-up can be recorded once a later reporting month has arrived and its assessment is available. You can update the plan and scheduled date now.</div>}
    {evidenceLoading && <p className="workflow-loading" role="status">Checking financial periods…</p>}
    {evidenceError && <div className="notice error" role="alert">{evidenceError}</div>}
    {!evidenceLoading && !evidenceError && !evidenceReady && <p className="notice error">The financial snapshot for the baseline or previous review could not be matched. Reload the case and check its records.</p>}
    <WorkflowNotice {...action} error={error || action.error} />
    {reviewing ? <div className="snapshot-review"><h3>Confirm follow-up review</h3><dl className="intervention-review"><div><dt>Assessment</dt><dd>#{selected.assessmentId} · {periods.get(selected.snapshotId)}</dd></div><div><dt>Outcome</dt><dd>{humanize(outcome)}</dd></div><div><dt>Case action</dt><dd>{!keepActive ? "Close warning and active interventions" : outcome === "ESCALATED" ? "Escalate to Risk Admin; keep intervention active" : "Keep intervention active"}</dd></div><div><dt>Next follow-up</dt><dd>{keepActive ? nextDate : "None — case will close"}</dd></div></dl><p className="workflow-reason">{notes}</p><div className="snapshot-actions"><button className="button secondary" disabled={action.busy} onClick={() => setReviewing(false)}>Back to edit</button><button className="button primary" disabled={action.busy} onClick={save}>{action.busy ? "Saving…" : "Confirm follow-up"}</button></div></div>
      : <form onSubmit={review}><fieldset className="snapshot-fieldset" disabled={action.busy}>
        <div className="management-subheading"><h3>Select assessment evidence</h3>{selected && <span className="badge success">Selected #{selected.assessmentId}</span>}</div>
        <ResourceState resource={history} empty={!rows.length}><div className="table-scroll"><table><thead><tr><th>Assessment</th><th>Reporting month</th><th>Severity</th><th>Evidence</th></tr></thead><tbody>{rows.map(a => <tr key={a.assessmentId}><td>#{a.assessmentId}</td><td>{periods.get(a.snapshotId) || "Unavailable"}</td><td><WorkflowStatus value={a.severity} /></td><td><button className="table-open" type="button" disabled={!eligible(a)} aria-pressed={selected?.assessmentId === a.assessmentId} onClick={() => { setSelected(a); setError(""); }} aria-label={`Select assessment ${a.assessmentId} for follow-up`}>{eligible(a) ? "Select evidence" : "Not eligible"}</button></td></tr>)}</tbody></table></div></ResourceState>
        <WorkflowPagination page={page} onPage={setPage} loading={history.loading} count={history.error ? undefined : rows.length} hasNext={!history.error && rows.length === 10} />
        <div className="snapshot-fields intervention-form-fields"><div className="snapshot-field"><label htmlFor="followup-outcome">Review outcome</label><select id="followup-outcome" value={outcome} onChange={e => { setOutcome(e.target.value); if (["STILL_STRESSED", "ESCALATED"].includes(e.target.value)) setKeepActive(true); }}>{["IMPROVING", "STABILISING", "STILL_STRESSED", "ESCALATED"].map(o => <option value={o} key={o}>{humanize(o)}</option>)}</select></div>
          <div className="snapshot-field"><label htmlFor="followup-case-action">Keep intervention active?</label><select id="followup-case-action" value={keepActive ? "yes" : "no"} onChange={e => setKeepActive(e.target.value === "yes")}><option value="yes">Yes — continue monitoring</option>{["IMPROVING", "STABILISING"].includes(outcome) && <option value="no">No — close the warning and interventions</option>}</select></div>
          {keepActive && <div className="snapshot-field"><label htmlFor="followup-next-date">Next follow-up date</label><input id="followup-next-date" type="date" min={todayDate()} value={nextDate} onChange={e => setNextDate(e.target.value)} required /></div>}
          <div className="snapshot-field"><label htmlFor="followup-notes">Review notes</label><textarea id="followup-notes" value={notes} onChange={e => setNotes(e.target.value)} maxLength={4000} required /></div>
        </div><div className="snapshot-actions"><button className="button secondary" type="button" onClick={onCancel}>Cancel</button><button className="button primary" disabled={!selected || !evidenceReady || creationMonth >= currentMonth} type="submit">Review follow-up</button></div>
      </fieldset></form>}
  </section>;
}

function FollowUpHistory({ intervention, token, revision }) {
  const [page, setPage] = useState(0);
  const resource = useWorkflowResource(followUpsPath(intervention.interventionId, page), token, revision);
  const rows = listOf(resource.data);
  return <div className="intervention-history"><div className="panel-heading"><div><h2>Follow-up history · plan #{intervention.interventionId}</h2><p>Assessment evidence, outcomes and review notes</p></div></div>
    <ResourceState resource={resource} empty={!rows.length}><ol className="workflow-timeline">{rows.map(r => <li key={r.followupId}><span className="workflow-timeline-dot" /><div><strong>{humanize(r.outcome)}</strong><small>Review #{r.followupId} · Assessment #{r.assessmentId} · User #{r.reviewedByUserId} · {dateLabel(r.reviewedAt)}</small><p>{r.reviewNotes}</p><span className={`badge ${r.keepInterventionActive === "Y" ? "warning" : "success"}`}>{r.keepInterventionActive === "Y" ? "Kept active at review" : "Case closed at review"}</span></div></li>)}</ol></ResourceState>
    <WorkflowPagination page={page} onPage={setPage} loading={resource.loading} count={resource.data?.totalElements} hasNext={!resource.error && (resource.data?.last === false || (resource.data?.last == null && rows.length === 10))} />
  </div>;
}

export default function InterventionWorkflow({ warning, business, user, refreshVersion, onChanged }) {
  const [page, setPage] = useState(0);
  const [editor, setEditor] = useState(null);
  const [selected, setSelected] = useState(null);
  const [revision, setRevision] = useState(0);
  const [notice, setNotice] = useState("");
  const version = `${refreshVersion}:${revision}`;
  const resource = useWorkflowResource(interventionsPath(warning.warningId, page), user.accessToken, version);
  const rows = listOf(resource.data);
  const activePlan = rows.find(i => i.isActive === "Y");
  const latestActiveReview = useWorkflowResource(activePlan ? followUpsPath(activePlan.interventionId, 0, 1) : null, user.accessToken, version);
  const lastReview = listOf(latestActiveReview.data)[0];
  const rm = user.role === "RM";
  const writable = rm && business.isActive === "Y";
  const revisable = warning.warningStatus === "INTERVENTION_ACTIVE" && activePlan && !latestActiveReview.loading && !latestActiveReview.error && lastReview?.outcome === "STILL_STRESSED";
  const canCreate = writable && page === 0 && !resource.loading && !resource.error && (warning.warningStatus === "UNDER_REVIEW" && !activePlan || revisable);
  const detail = selected ? rows.find(i => i.interventionId === selected.interventionId) || selected : null;
  const canModify = writable && warning.warningStatus === "INTERVENTION_ACTIVE" && detail?.isActive === "Y";
  const detailReview = useWorkflowResource(detail ? followUpsPath(detail.interventionId, 0, 1) : null, user.accessToken, version);
  function notify(message) {
    setEditor(null); setNotice(message); setRevision(v => v + 1); onChanged(message);
  }
  function planSaved(plan, operation) {
    setSelected(plan); setPage(0); notify(`Intervention #${plan.interventionId} ${operation} successfully.`);
  }

  return <div className="intervention-workspace" aria-label="Interventions and follow-ups">
    <div className="panel-heading"><div><h2><ClipboardList size={18} /> Interventions & follow-ups</h2><p>{rm ? "Plan actions, schedule reviews and track the business's progress." : "Read-only action plans and follow-up results for this case."}</p></div><div className="management-toolbar">
      <button className="button secondary" disabled={resource.loading || !!editor} onClick={() => setRevision(v => v + 1)}>Reload plans</button>
      {canCreate && <button className="button primary" disabled={!!editor} onClick={() => { setNotice(""); setEditor({ mode: "create" }); }}><Plus size={15} />{revisable ? "Revise intervention" : "Add intervention"}</button>}
    </div></div>
    {notice && <div className="workflow-inset notice snapshot-success" role="status"><Check size={16} />{notice}</div>}
    {rm && !canCreate && !resource.loading && <p className="workflow-inset management-help">{business.isActive !== "Y" ? "Inactive business cases cannot be changed." : warning.warningStatus === "OPEN" ? "Accept the warning first to move it under review, then add an intervention." : warning.warningStatus === "INTERVENTION_ACTIVE" ? "Update or follow up the active plan, or select Close intervention in the decision dropdown above to close the case. A revised plan can be created after a STILL STRESSED review; return to page 1 to revise it." : warning.warningStatus === "ESCALATED" ? "This case is awaiting Risk Admin review. Plan changes are paused." : ["CLOSED", "CLOSED_REVIEWED"].includes(warning.warningStatus) ? "This case is closed. Its intervention and review history remains available." : page !== 0 ? "Return to page 1 to add a plan." : "Reload the case to check intervention availability."}</p>}
    {editor?.mode === "create" && canCreate && <PlanForm key="create" warningId={warning.warningId} token={user.accessToken} revising={!!revisable} onSaved={planSaved} onCancel={() => setEditor(null)} />}
    {latestActiveReview.error && rm && warning.warningStatus === "INTERVENTION_ACTIVE" && <p className="workflow-inset notice error">Could not check whether the active plan can be revised: {latestActiveReview.error}</p>}
    <ResourceState resource={resource} empty={!rows.length}><div className="intervention-list">{rows.map(plan => <article className="intervention-card" key={plan.interventionId}>
      <div className="warning-title"><h3>Intervention #{plan.interventionId}</h3><span className={`badge ${plan.isActive === "Y" ? "warning" : "neutral"}`}>{plan.isActive === "Y" ? "Active plan" : "Closed plan"}</span></div>
      <p className="workflow-reason">{plan.actionText}</p>{plan.notes && <p className="management-help">{plan.notes}</p>}
      <div className="intervention-meta"><span><CalendarDays size={14} />Follow-up: {plan.followUpDate || "Not scheduled"}</span>{plan.isActive === "Y" && plan.followUpDate < todayDate() && <span className="badge danger">Overdue</span>}<span>Created {dateLabel(plan.createdAt)} · User #{plan.createdByUserId}</span>{plan.closedAt && <span>Closed {dateLabel(plan.closedAt)} · User #{plan.closedByUserId}</span>}</div>
      <button className="table-open" disabled={!!editor} onClick={() => setSelected(plan)} aria-label={`Open intervention ${plan.interventionId}`}>View plan & follow-ups</button>
    </article>)}</div></ResourceState>
    <WorkflowPagination page={page} onPage={setPage} loading={resource.loading || !!editor} count={resource.data?.totalElements} hasNext={!resource.error && (resource.data?.last === false || (resource.data?.last == null && rows.length === 10))} />
    {detail && <section className="intervention-detail" aria-label={`Intervention ${detail.interventionId} details`}>
      <div className="panel-heading"><div><h2>Plan #{detail.interventionId} · reviews and updates</h2><p>Action: {detail.actionText}</p></div><button className="icon-button" disabled={!!editor} onClick={() => setSelected(null)} aria-label="Close intervention details"><X size={18} /></button></div>
      {canModify && !editor && <div className="workflow-inset management-toolbar"><button className="button secondary" onClick={() => setEditor({ mode: "edit" })}><Pencil size={15} />Update notes & date</button><button className="button primary" disabled={detailReview.loading || !!detailReview.error} onClick={() => setEditor({ mode: "followup" })}>Record follow-up</button></div>}
      {detailReview.error && <p className="workflow-inset notice error">Could not load the latest review: {detailReview.error}</p>}
      {editor?.mode === "edit" && canModify && <PlanForm key={`edit:${detail.interventionId}`} warningId={warning.warningId} token={user.accessToken} intervention={detail} onSaved={planSaved} onCancel={() => setEditor(null)} />}
      {editor?.mode === "followup" && canModify && <FollowUpForm key={`followup:${detail.interventionId}`} intervention={detail} warning={warning} business={business} token={user.accessToken} latestReview={listOf(detailReview.data)[0]} onCancel={() => setEditor(null)} onSaved={review => notify(`Follow-up #${review.followupId} recorded: ${humanize(review.outcome)}.`)} />}
      <FollowUpHistory key={detail.interventionId} intervention={detail} token={user.accessToken} revision={version} />
    </section>}
  </div>;
}
