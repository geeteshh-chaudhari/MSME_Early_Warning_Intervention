import { useRef, useState } from "react";
import { Save, X } from "lucide-react";
import { createBusiness, updateBusiness } from "../api/businessApi";

export default function BusinessForm({ token, business, onSaved, onCancel }) {
  const [values, setValues] = useState({
    businessName: business?.businessName || "",
    sector: business?.sector || "",
    contactName: business?.contactName || "",
    sanctionedCreditLimit: business?.sanctionedCreditLimit ?? "",
    consentStatus: business?.consentStatus || "",
  });
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
    const payload = {
      businessName: values.businessName.trim(),
      sector: values.sector.trim(),
      contactName: values.contactName.trim(),
      sanctionedCreditLimit: Number(values.sanctionedCreditLimit),
      consentStatus: values.consentStatus.trim() || null,
    };
    if (!payload.businessName || !payload.sector || !payload.contactName ||
        !Number.isFinite(payload.sanctionedCreditLimit) || payload.sanctionedCreditLimit < 0.01) {
      setError("Enter the business details and a credit limit of at least 0.01.");
      return;
    }
    inFlight.current = true;
    setSaving(true);
    setError("");
    try {
      const saved = business
        ? await updateBusiness(token, business.msmeId, payload)
        : await createBusiness(token, payload);
      onSaved(saved);
    } catch (err) {
      setError(err.status === 401 ? "Your session has expired. Sign out and log in again." : err.message);
    } finally {
      inFlight.current = false;
      setSaving(false);
    }
  }

  return <section className="panel management-editor" aria-labelledby="business-form-title">
    <div className="panel-heading">
      <div><h2 id="business-form-title">{business ? "Edit business" : "Add business"}</h2>
        <p>{business ? "Update your assigned business details." : "The new business will be assigned to your RM account."}</p></div>
      <button className="icon-button" type="button" onClick={onCancel} disabled={saving} aria-label="Close business form"><X size={19} /></button>
    </div>
    <form className="snapshot-content" onSubmit={submit}>
      {error && <div className="notice error" role="alert">{error}</div>}
      <fieldset className="snapshot-fieldset" disabled={saving}>
        <div className="snapshot-fields">
          {[["businessName", "Business name", 150], ["sector", "Sector", 50], ["contactName", "Contact name", 100]].map(([name, label, max]) =>
            <div className="snapshot-field" key={name}><label htmlFor={`business-${name}`}>{label}</label>
              <input id={`business-${name}`} name={name} value={values[name]} onChange={change} maxLength={max} required /></div>)}
          <div className="snapshot-field"><label htmlFor="business-limit">Sanctioned credit limit</label>
            <input id="business-limit" name="sanctionedCreditLimit" type="number" min="0.01" step="0.01" value={values.sanctionedCreditLimit} onChange={change} required /></div>
          <div className="snapshot-field"><label htmlFor="business-consent">Consent status (optional)</label>
            <input id="business-consent" name="consentStatus" maxLength={20} value={values.consentStatus} onChange={change} /></div>
        </div>
        <div className="snapshot-actions"><button type="button" className="button secondary" onClick={onCancel}>Cancel</button>
          <button type="submit" className="button primary"><Save size={16} />{saving ? "Saving…" : business ? "Save changes" : "Create business"}</button></div>
      </fieldset>
    </form>
  </section>;
}
