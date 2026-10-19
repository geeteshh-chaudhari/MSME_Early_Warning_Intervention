import { useEffect, useRef, useState } from "react";
import { Check, Pencil, Plus, RefreshCw, Users, X } from "lucide-react";
import { createRepresentative, getRepresentatives, updateRepresentative } from "../api/businessApi";

function RepresentativeForm({ token, representative, businesses, onSaved, onCancel }) {
  const [values, setValues] = useState({ name: representative?.name || "", email: representative?.email || "", password: "", msmeId: "" });
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const inFlight = useRef(false);
  function change(event) {
    setValues(previous => ({ ...previous, [event.target.name]: event.target.value }));
    setError("");
  }
  async function submit(event) {
    event.preventDefault();
    if (inFlight.current) return;
    if (!values.name.trim() || !values.email.trim() || (!representative && (!values.password || !values.msmeId))) {
      setError("Complete all required fields."); return;
    }
    inFlight.current = true; setSaving(true); setError("");
    try {
      const payload = { name: values.name.trim(), email: values.email.trim() };
      const saved = representative
        ? await updateRepresentative(token, representative.userId, payload)
        : await createRepresentative(token, { ...payload, password: values.password, msmeId: Number(values.msmeId) });
      setValues(previous => ({ ...previous, password: "" }));
      onSaved(saved);
    } catch (err) {
      setError(err.status === 401 ? "Your session has expired. Sign out and log in again." : err.message);
    } finally { inFlight.current = false; setSaving(false); }
  }
  return <form className="representative-form" onSubmit={submit} aria-label={representative ? "Edit representative" : "Add representative"}>
    <div className="management-subheading"><h3>{representative ? "Edit representative" : "Add representative"}</h3>
      <button type="button" className="icon-button" onClick={onCancel} disabled={saving} aria-label="Close representative form"><X size={18} /></button></div>
    {error && <div className="notice error" role="alert">{error}</div>}
    <fieldset className="snapshot-fieldset" disabled={saving}>
      <div className="snapshot-fields">
        <div className="snapshot-field"><label htmlFor="rep-name">Name</label><input id="rep-name" name="name" value={values.name} onChange={change} maxLength={100} autoComplete="off" required /></div>
        <div className="snapshot-field"><label htmlFor="rep-email">Email address</label><input id="rep-email" name="email" type="email" value={values.email} onChange={change} maxLength={150} autoComplete="off" required /></div>
        {!representative && <>
          <div className="snapshot-field"><label htmlFor="rep-password">Initial password</label><input id="rep-password" name="password" type="password" value={values.password} onChange={change} autoComplete="new-password" required /></div>
          <div className="snapshot-field"><label htmlFor="rep-business">Business</label><select id="rep-business" name="msmeId" value={values.msmeId} onChange={change} required>
            <option value="">Choose a business</option>{businesses.map(b => <option key={b.msmeId} value={b.msmeId}>{b.businessName}</option>)}</select></div>
        </>}
      </div>
      <p className="management-help">{representative ? "Only name and email can be changed here. Business assignment, password and role remain managed by the backend." : "One representative account is allowed per business. Use these credentials to sign in as that representative."}</p>
      <div className="snapshot-actions"><button type="button" className="button secondary" onClick={onCancel}>Cancel</button>
        <button type="submit" className="button primary">{saving ? "Saving…" : representative ? "Save changes" : "Create representative"}</button></div>
    </fieldset>
  </form>;
}

export default function RepresentativeManagement({ token, businesses }) {
  const [result, setResult] = useState(null);
  const [revision, setRevision] = useState(0);
  const [editor, setEditor] = useState(null);
  const [message, setMessage] = useState("");
  useEffect(() => {
    let active = true;
    getRepresentatives(token).then(data => {
      if (active) setResult({ rows: data, error: "" });
    }).catch(error => {
      if (active) setResult({ rows: [], error: error.status === 401 ? "Your session has expired. Sign out and log in again." : error.message });
    });
    return () => { active = false; };
  }, [token, businesses, revision]);

  const rows = result?.rows || [];
  const available = businesses.filter(b => b.isActive === "Y" && !rows.some(r => r.msmeId === b.msmeId));
  function saved(representative) {
    setResult(previous => ({ error: "", rows: previous.rows.some(r => r.userId === representative.userId)
      ? previous.rows.map(r => r.userId === representative.userId ? representative : r)
      : [...previous.rows, representative] }));
    setEditor(null); setMessage("Representative saved successfully.");
  }

  return <section className="panel">
    <div className="panel-heading"><div><h2><Users size={17} /> Business representatives</h2><p>Manage login accounts for the businesses assigned to you.</p></div>
      <div className="management-toolbar"><button className="button secondary" onClick={() => { setResult(null); setRevision(v => v + 1); }} disabled={!!editor || !result} aria-label="Refresh representatives"><RefreshCw size={15} /></button>
        <button className="button primary" disabled={!!editor || !result || !!result.error || !available.length} onClick={() => { setMessage(""); setEditor({ mode: "create" }); }}><Plus size={15} />Add representative</button></div></div>
    {message && <div className="management-notice notice snapshot-success" role="status"><Check size={17} />{message}</div>}
    {result?.error && <div className="management-notice notice error" role="alert">{result.error}</div>}
    {editor && <RepresentativeForm key={editor.representative?.userId || "new"} token={token} representative={editor.representative} businesses={available} onSaved={saved} onCancel={() => setEditor(null)} />}
    {!result ? <div className="loading-state" role="status">Loading representative accounts…</div> : !result.error && <>
      <div className="table-scroll"><table><thead><tr><th>Representative</th><th>Business</th><th>Account status</th><th>Actions</th></tr></thead><tbody>
        {rows.map(rep => { const business = businesses.find(b => b.msmeId === rep.msmeId); return <tr key={rep.userId}>
          <td><div className="business-cell"><span className="business-avatar">{rep.name?.charAt(0) || "U"}</span><div><strong>{rep.name}</strong><small>{rep.email}</small></div></div></td>
          <td>{business?.businessName || `Business #${rep.msmeId}`}</td><td><span className={`badge ${rep.accountStatus === "ACTIVE" ? "success" : "warning"}`}>{rep.accountStatus}</span></td>
          <td><button className="table-open" disabled={!!editor} onClick={() => { setMessage(""); setEditor({ mode: "edit", representative: rep }); }} aria-label={`Edit ${rep.name}`}><Pencil size={14} />Edit</button></td>
        </tr>; })}
      </tbody></table></div>
      {!rows.length && <div className="empty-state"><Users size={27} /><h3>No representatives yet</h3><p>Create an account so a business representative can sign in and submit financial data.</p></div>}
      {!available.length && <p className="management-help management-notice">To add another representative, first add an active business without a representative account.</p>}
    </>}
  </section>;
}
