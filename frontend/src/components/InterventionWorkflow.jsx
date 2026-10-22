import { useState } from "react";
import { CalendarDays, ClipboardList, Plus, X } from "lucide-react";
import { interventionsPath, updateIntervention } from "../api/workflowApi";
import { useWorkflowAction, useWorkflowResource } from "../hooks/useWorkflowResource";
import { dateLabel, humanize, listOf } from "./workflowFormat";
import { ResourceState, WorkflowNotice, WorkflowPagination } from "./WorkflowUi";

function todayDate() {
  const date = new Date();
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

function AppendForm({ intervention, user, onSaved, onCancel }) {
  const [actionText, setActionText] = useState("intervene");
  const [notes, setNotes] = useState("");
  const [date, setDate] = useState("");
  const [error, setError] = useState("");
  const action = useWorkflowAction();
  const rm = user.role === "RM";
  async function submit(event) {
    event.preventDefault();
    if (!notes.trim() || !/^\d{4}-\d{2}-\d{2}$/.test(date) || date < todayDate()) {
      setError("Enter notes and a follow-up date of today or later."); return;
    }
    setError("");
    const body = { notes: notes.trim(), followUpDate: date, ...(rm ? { actionText } : {}) };
    const result = await action.execute(() => updateIntervention(user.accessToken, intervention.interventionId, body), "Notes appended.");
    if (result.ok) onSaved();
  }
  return <section className="intervention-editor" aria-label="Append intervention notes">
    <div className="management-subheading"><h3>Append to intervention #{intervention.interventionId}</h3><button className="icon-button" disabled={action.busy} onClick={onCancel} aria-label="Close append form"><X size={18} /></button></div>
    <p className="management-help">Previous entries are preserved. The follow-up field stores the date from this latest entry. Closing this intervention leaves its warning open.</p>
    <form onSubmit={submit}><WorkflowNotice {...action} error={error || action.error} /><fieldset className="snapshot-fieldset" disabled={action.busy}>
      {rm && <div className="snapshot-field"><label htmlFor="append-action">Action</label><select id="append-action" value={actionText} onChange={e => setActionText(e.target.value)}><option value="intervene">Intervene</option><option value="escalate">Escalate</option><option value="close">Close intervention</option></select></div>}
      <div className="snapshot-fields"><div className="snapshot-field"><label htmlFor="append-notes">{rm ? "Notes / closure reason" : "Advice for the RM"}</label><textarea id="append-notes" value={notes} onChange={e => setNotes(e.target.value)} maxLength={4000} required /></div>
        <div className="snapshot-field"><label htmlFor="append-date">Follow-up date</label><input id="append-date" type="date" min={todayDate()} value={date} onChange={e => setDate(e.target.value)} required /></div></div>
      <div className="snapshot-actions"><button className="button secondary" type="button" onClick={onCancel}>Cancel</button><button className="button primary" type="submit">{action.busy ? "Appending…" : "Append"}</button></div>
    </fieldset></form>
  </section>;
}

export default function InterventionWorkflow({ warning, business, user, refreshVersion, onChanged }) {
  const [page, setPage] = useState(0);
  const [editingId, setEditingId] = useState(null);
  const [revision, setRevision] = useState(0);
  const resource = useWorkflowResource(interventionsPath(warning.warningId, page), user.accessToken, `${refreshVersion}:${revision}`);
  const rows = listOf(resource.data);
  const canAppend = plan => resource.data?.totalElements <= 1 && business.isActive === "Y" && plan.isActive === "Y"
    && !["CLOSED", "CLOSED_REVIEWED"].includes(warning.warningStatus)
    && (user.role === "RM" || (user.role === "RISK_ADMIN" && plan.actionText === "escalate"));
  function saved() {
    setEditingId(null); setRevision(value => value + 1);
    onChanged("Notes appended to the existing intervention. LLM recommendations are unchanged.");
  }
  return <div className="intervention-workspace" aria-label="Intervention log">
    <div className="panel-heading"><div><h2><ClipboardList size={18} /> Intervention log</h2><p>One intervention per warning. Entries show actor, action, notes and follow-up date.</p></div><button className="button secondary" disabled={resource.loading} onClick={() => setRevision(value => value + 1)}>Reload intervention</button></div>
    {!resource.loading && !resource.error && !rows.length && <p className="workflow-inset management-help">The first RM decision creates this warning's intervention.</p>}
    <ResourceState resource={resource} empty={!rows.length}><div className="intervention-list">{rows.map(plan => <article className="intervention-card" key={plan.interventionId}>
      <div className="warning-title"><h3>Intervention #{plan.interventionId}</h3><span className={`badge ${plan.isActive === "Y" ? "warning" : "neutral"}`}>{plan.isActive === "Y" ? "Active" : "Closed"}</span></div>
      <p>Latest action: <strong>{humanize(plan.actionText)}</strong></p>
      <p className="workflow-reason" style={{ whiteSpace: "pre-wrap" }}>{plan.notes || "No notes recorded."}</p>
      <div className="intervention-meta"><span><CalendarDays size={14} />Latest follow-up: {plan.followUpDate || "Not scheduled"}</span><span>Created {dateLabel(plan.createdAt)} · User #{plan.createdByUserId}</span>{plan.closedAt && <span>Closed {dateLabel(plan.closedAt)} · User #{plan.closedByUserId}</span>}</div>
      {canAppend(plan) && editingId !== plan.interventionId && <button className="button secondary" onClick={() => setEditingId(plan.interventionId)}><Plus size={15} />Append</button>}
      {editingId === plan.interventionId && canAppend(plan) && <AppendForm intervention={plan} user={user} onSaved={saved} onCancel={() => setEditingId(null)} />}
      {user.role === "RISK_ADMIN" && plan.isActive === "Y" && plan.actionText !== "escalate" && <p className="management-help">Advice becomes editable when the RM escalates this intervention.</p>}
    </article>)}</div></ResourceState>
    <WorkflowPagination page={page} onPage={setPage} loading={resource.loading} count={resource.data?.totalElements} hasNext={!resource.error && resource.data?.last === false} />
  </div>;
}
