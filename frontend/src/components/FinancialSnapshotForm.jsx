import { useRef, useState } from "react";
import { Check, FileText, Send } from "lucide-react";
import { apiRequest } from "../api/client";

const fields = [
  ["revenue", "Revenue"],
  ["totalReceivables", "Total receivables"],
  ["overdueReceivables", "Overdue receivables"],
  ["creditUsed", "Credit used"],
];

const initialValues = {
  reportingMonth: "",
  revenue: "",
  totalReceivables: "",
  overdueReceivables: "",
  creditUsed: "",
};

function displayAmount(value) {
  return new Intl.NumberFormat("en-IN", {
    maximumFractionDigits: 2,
  }).format(Number(value));
}

export default function FinancialSnapshotForm({
  business,
  token,
  existingSnapshots,
  onSaved,
}) {
  const [values, setValues] = useState(initialValues);
  const [errors, setErrors] = useState({});
  const [reviewing, setReviewing] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [message, setMessage] = useState("");
  const [success, setSuccess] = useState("");

  const requestInProgress = useRef(false);

  const inactive = business.isActive !== "Y";

  function updateField(event) {
    const { name, value } = event.target;

    setValues((previous) => ({
      ...previous,
      [name]: value,
    }));

    setErrors((previous) => ({
      ...previous,
      [name]: "",
    }));

    setMessage("");
    setSuccess("");
  }

  function validate() {
    const nextErrors = {};

    if (!/^\d{4}-(0[1-9]|1[0-2])$/.test(values.reportingMonth)) {
      nextErrors.reportingMonth = "Select a reporting month.";
    }

    for (const [name] of fields) {
      const value = values[name].trim();

      if (!value) {
        nextErrors[name] = "This amount is required.";
      } else if (
        !Number.isFinite(Number(value)) ||
        Number(value) < 0
      ) {
        nextErrors[name] = "Enter a valid amount of zero or more.";
      }
    }

    if (
      !nextErrors.totalReceivables &&
      !nextErrors.overdueReceivables &&
      Number(values.overdueReceivables) >
        Number(values.totalReceivables)
    ) {
      nextErrors.overdueReceivables =
        "Overdue receivables cannot exceed total receivables.";
    }

    const duplicate = existingSnapshots.some(
      (snapshot) =>
        String(snapshot.reportingPeriod).slice(0, 7) ===
        values.reportingMonth
    );

    if (duplicate) {
      nextErrors.reportingMonth =
        "A snapshot already exists for this month.";
    }

    return nextErrors;
  }

  function review(event) {
    event.preventDefault();
    setMessage("");
    setSuccess("");

    const nextErrors = validate();
    setErrors(nextErrors);

    if (!Object.keys(nextErrors).length) {
      setReviewing(true);
    }
  }

  async function submit() {
    if (requestInProgress.current || inactive) return;

    const nextErrors = validate();

    if (Object.keys(nextErrors).length) {
      setErrors(nextErrors);
      setReviewing(false);
      return;
    }

    requestInProgress.current = true;
    setSubmitting(true);
    setMessage("");

    try {
      const snapshot = await apiRequest("/snapshots", {
        method: "POST",
        token,
        body: {
          reportingPeriod: `${values.reportingMonth}-01`,
          revenue: Number(values.revenue),
          totalReceivables: Number(values.totalReceivables),
          overdueReceivables: Number(values.overdueReceivables),
          creditUsed: Number(values.creditUsed),
        },
      });

      onSaved(snapshot);

      setSuccess(
        `Financial snapshot for ${values.reportingMonth} submitted successfully.`
      );

      setValues({ ...initialValues });
      setErrors({});
      setReviewing(false);
    } catch (error) {
      const messages = {
        401: "Your session has expired. Sign out and log in again.",
        403: "Your account is not permitted to submit this snapshot.",
        409: "A snapshot already exists for this reporting month.",
      };

      setMessage(
        messages[error.status] ||
          error.message ||
          "Submission failed. Please try again."
      );

      // Keep entered data available for correction.
      setReviewing(false);
    } finally {
      requestInProgress.current = false;
      setSubmitting(false);
    }
  }

  return (
    <section className="panel snapshot-panel">
      <div className="panel-heading">
        <div>
          <h2>Submit monthly financial data</h2>
          <p>
            Add a financial snapshot for {business.businessName}.
          </p>
        </div>
        <FileText size={21} className="teal" />
      </div>

      <div className="snapshot-content">
        <p className="snapshot-description">
          Enter amounts using the same units as your existing
          financial records. Review the details before submitting.
        </p>

        {inactive && (
          <div className="notice error" role="alert">
            Financial data cannot be submitted for an inactive
            business.
          </div>
        )}

        {message && (
          <div className="notice error" role="alert">
            {message}
          </div>
        )}

        {success && (
          <div className="notice snapshot-success" role="status">
            <Check size={18} />
            {success}
          </div>
        )}

        {reviewing ? (
          <div className="snapshot-review">
            <h3>Review your submission</h3>

            <dl>
              <div>
                <dt>Business</dt>
                <dd>{business.businessName}</dd>
              </div>

              <div>
                <dt>Reporting month</dt>
                <dd>{values.reportingMonth}</dd>
              </div>

              {fields.map(([name, label]) => (
                <div key={name}>
                  <dt>{label}</dt>
                  <dd>{displayAmount(values[name])}</dd>
                </div>
              ))}
            </dl>

            <p className="snapshot-description">
              Only one snapshot can be submitted per reporting
              month. This screen does not support editing a saved
              snapshot.
            </p>

            <div className="snapshot-actions">
              <button
                type="button"
                className="button secondary"
                onClick={() => setReviewing(false)}
                disabled={submitting}
              >
                Back to edit
              </button>

              <button
                type="button"
                className="button primary"
                onClick={submit}
                disabled={submitting || inactive}
              >
                <Send size={16} />
                {submitting
                  ? "Submitting…"
                  : "Confirm and submit"}
              </button>
            </div>
          </div>
        ) : (
          <form onSubmit={review}>
            <fieldset
              className="snapshot-fieldset"
              disabled={inactive || submitting}
            >
              <div className="snapshot-fields">
                <div className="snapshot-field">
                  <label htmlFor="reportingMonth">
                    Reporting month
                  </label>

                  <input
                    id="reportingMonth"
                    name="reportingMonth"
                    type="month"
                    value={values.reportingMonth}
                    onChange={updateField}
                    aria-invalid={!!errors.reportingMonth}
                    aria-describedby={
                      errors.reportingMonth
                        ? "reportingMonth-error"
                        : undefined
                    }
                    required
                  />

                  {errors.reportingMonth && (
                    <small
                      className="field-error"
                      id="reportingMonth-error"
                    >
                      {errors.reportingMonth}
                    </small>
                  )}
                </div>

                {fields.map(([name, label]) => (
                  <div className="snapshot-field" key={name}>
                    <label htmlFor={`snapshot-${name}`}>
                      {label}
                    </label>

                    <input
                      id={`snapshot-${name}`}
                      name={name}
                      type="number"
                      min="0"
                      step="0.01"
                      placeholder="0.00"
                      value={values[name]}
                      onChange={updateField}
                      aria-invalid={!!errors[name]}
                      aria-describedby={
                        errors[name]
                          ? `${name}-error`
                          : undefined
                      }
                      required
                    />

                    {errors[name] && (
                      <small
                        className="field-error"
                        id={`${name}-error`}
                      >
                        {errors[name]}
                      </small>
                    )}
                  </div>
                ))}
              </div>

              <div className="snapshot-actions">
                <button
                  className="button primary"
                  type="submit"
                >
                  Review submission
                </button>
              </div>
            </fieldset>
          </form>
        )}
      </div>
    </section>
  );
}