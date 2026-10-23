import { useEffect, useRef, useState } from "react";
import { RefreshCw, Sparkles, X } from "lucide-react";
import { useWorkflowResource } from "../hooks/useWorkflowResource";
import { WorkflowPagination } from "./WorkflowUi";
import { listOf, recommendationText } from "./workflowFormat";
import { insightsError, requestRecommendation } from "../api/insightsApi";
import { useReportingPeriod } from "../hooks/useReportingPeriod";
import { flattenEvidence, metricLabel, metricValue, monthLabel } from "./insightsFormat";
import EvidenceExplorer from "./EvidenceExplorer";

const defaultQuestion = "Review revenue trends and suggest collection actions.";

function RecommendationRequest({ business, user, month, warning }) {
  const [metricsOnly, setMetricsOnly] = useState(false);
  const [state, setState] = useState({ busy: false, data: null, error: "", info: "" });
  const [focusedMetric, setFocusedMetric] = useState("");
  const controller = useRef(null);
  const sequence = useRef(0);
  const evidenceRef = useRef(null);
  useEffect(() => () => { sequence.current += 1; controller.current?.abort(); }, []);
  function clearResult() { setState({ busy: false, data: null, error: "", info: "" }); setFocusedMetric(""); }
  async function submit(event) {
    event.preventDefault();
    if (controller.current) return;
    const current = ++sequence.current;
    const request = new AbortController(); controller.current = request;
    setState({ busy: true, data: null, error: "", info: "" }); setFocusedMetric("");
    try {
      const data = await requestRecommendation(user.accessToken, { warning_id: warning.warningId, msme_id: Number(business.msmeId), as_of: month, metrics_only: metricsOnly, question: defaultQuestion }, request.signal);
      if (current === sequence.current) setState({ busy: false, data, error: "", info: "" });
    } catch (error) {
      if (current === sequence.current && error.name !== "AbortError") setState({ busy: false, data: null, error: insightsError(error, "recommendation"), info: "" });
    } finally { if (current === sequence.current) controller.current = null; }
  }
  function cancel() {
    sequence.current += 1; controller.current?.abort(); controller.current = null;
    setState({ busy: false, data: null, error: "", info: "Request cancelled in this browser. The service may still be processing it; wait before retrying." });
  }
  function inspect(id) {
    setFocusedMetric(id);
    requestAnimationFrame(() => evidenceRef.current?.scrollIntoView({ behavior: "smooth", block: "start" }));
  }
  const data = state.data;
  const evidence = data?.evidence || {};
  const entries = flattenEvidence(evidence);
  const advice = data?.recommendation;
  return <>
    <section className="panel"><div className="panel-heading"><div><h2><Sparkles size={19} /> Recommendations & evidence</h2><p>Warning #{warning.warningId} for {business.businessName}, reporting {monthLabel(month)}.</p></div></div>
      <form className="workflow-inset" onSubmit={submit}>
        {state.error && <div className="notice error" role="alert">{state.error}</div>}{state.info && <div className="notice workflow-info" role="status">{state.info}</div>}
        <fieldset className="snapshot-fieldset" disabled={state.busy}><div className="snapshot-fields"><div className="snapshot-field"><label htmlFor="recommendation-mode">Analysis mode</label><select id="recommendation-mode" value={metricsOnly ? "evidence" : "advice"} onChange={e => { setMetricsOnly(e.target.value === "evidence"); clearResult(); }}><option value="advice">Generate recommendations</option><option value="evidence">Evidence only — no model required</option></select><small className="management-help">Evidence is calculated by the service. Recommendations use its configured local model and require human review.</small></div>
        </div>
          <div className="snapshot-actions"><button type="submit" className="button primary"><Sparkles size={16} />{state.busy ? "Analysing…" : metricsOnly ? "Load evidence" : "View / generate recommendation"}</button></div>
        </fieldset>
        {state.busy && <div className="recommendation-progress" role="status"><span><RefreshCw className="spin" size={18} />{metricsOnly ? "Calculating financial evidence…" : "Generating advice from financial evidence. The local model may take a few minutes."}</span><button type="button" className="button secondary" onClick={cancel}><X size={15} />Cancel request</button></div>}
        <p className="management-help">The saved recommendation for this exact warning is displayed first. Generation runs only when advice is missing or the service-unavailable message is saved. Failed generation keeps that message for an explicit retry.</p>
      </form>
    </section>
    {data && <>
      <div className="insights-report-meta"><span>Reporting {monthLabel(data.as_of_month)}</span><span>{data.status === "generated" ? "Generated advice" : data.status === "metrics_only" ? "Evidence only" : data.status === "insufficient_data" ? "Insufficient data" : data.status}</span><span>Formula version {data.formula_version}</span>{data.model && <span>Model: {data.model}</span>}</div>
      {data.status === "metrics_only" && <div className="notice workflow-info">Evidence loaded without calling the model. Choose Generate recommendations to request advisory interpretation.</div>}
      {data.status === "insufficient_data" && <div className="notice workflow-info">There is not enough usable evidence for recommendations. Review unavailable metrics and their reasons below.</div>}
      {advice && <section className="panel"><div className="panel-heading"><div><h2>Analysis summary</h2><p>Advisory interpretation · review the evidence before taking action</p></div></div><p className="workflow-inset recommendation-summary">{advice.summary || (data.stored_payload ? recommendationText(data.stored_payload) : "")}</p></section>}
      {advice?.actions?.length > 0 && <section className="panel"><div className="panel-heading"><div><h2>Suggested actions</h2><p>Each action links to the metric evidence returned with this analysis.</p></div></div><div className="recommendation-actions">{advice.actions.map((item, index) => <article className="recommendation-action" key={`${index}:${item.title}`}><span className="recommendation-step">{String(index + 1).padStart(2, "0")}</span><div><h3>{item.title}</h3><p>{item.interpretation}</p><div className="recommended-action">{item.suggested_action}</div><div className="recommendation-citations">{(item.evidence_metric_ids || []).map(id => { const entry = entries.find(entry => entry.id === id); return <button key={id} className="evidence-citation" disabled={!entry} onClick={() => inspect(id)} title={id}>{entry ? `${metricLabel(entry.key)} · ${metricValue(entry.item)}` : `${id} · Evidence unavailable`}</button>; })}</div></div></article>)}</div></section>}
      <div ref={evidenceRef}><EvidenceExplorer key={focusedMetric || "all"} evidence={evidence} focusedMetricId={focusedMetric} /></div>
      {data.tool_trace?.length > 0 && <details className="panel insights-trace"><summary>Analysis tools used</summary><ol>{data.tool_trace.map((step, index) => <li key={`${step.step}:${index}`}>{step.tool}</li>)}</ol></details>}
    </>}
  </>;
}

export default function RecommendationWorkspace(props) {
  if (!["RM", "RISK_ADMIN"].includes(props.user.role)) return <p role="alert">Only the assigned RM or Risk Admin can access recommendations.</p>;
  return <RmRecommendationWorkspace key={props.business.msmeId} {...props} />;
}

function RmRecommendationWorkspace({ business, user }) {
  const period = useReportingPeriod(user, business);
  const [page, setPage] = useState(0);
  const [revision, setRevision] = useState(0);
  const [chosen, setChosen] = useState("");
  const active = business.isActive === "Y";
  const warnings = useWorkflowResource(`/warnings?msmeId=${business.msmeId}&page=${page}&size=20`, user.accessToken, revision);
  const rows = listOf(warnings.data).filter(row => !["CLOSED", "CLOSED_REVIEWED"].includes(row.warningStatus));
  const selected = rows.find(row => String(row.warningId) === chosen) || rows[0];
  const assessment = useWorkflowResource(selected ? `/assessment/assessments/${selected.assessmentId}` : null, user.accessToken);
  const snapshot = listOf(period.resource.data).find(row => row.snapshotId === assessment.data?.snapshotId);
  const month = snapshot ? String(snapshot.reportingPeriod).slice(0, 7) : "";
  const eligible = month && period.months.includes(month);
  function reload() { period.reload(); setRevision(value => value + 1); }
  return <div className="workflow-stack"><section className="panel"><div className="panel-heading"><div>
    <h2>Choose an open warning</h2><p>Read or generate advice for one specific warning.</p>
    </div><button className="button secondary" disabled={warnings.loading} onClick={reload}>Reload warnings</button></div>
    <div className="workflow-inset"><div className="snapshot-field insights-month-field">
      <label htmlFor="recommendation-warning">Open warning</label>
      <select id="recommendation-warning" value={selected ? String(selected.warningId) : ""}
        disabled={!active || warnings.loading || !rows.length} onChange={event => setChosen(event.target.value)}>
        {!rows.length && <option value="">No open warnings on this page</option>}
        {rows.map(row => <option key={row.warningId} value={String(row.warningId)}>Warning #{row.warningId} - Assessment #{row.assessmentId}</option>)}
      </select>
      {month && <small className="management-help">Reporting {monthLabel(month)}</small>}
    </div>
    {(warnings.error || assessment.error || period.resource.error) && <div className="notice error" role="alert">{warnings.error || assessment.error || period.resource.error}</div>}
    {!active && <div className="notice error" role="alert">Recommendations are available for active businesses only.</div>}
    {(warnings.loading || assessment.loading || period.resource.loading) && <p className="workflow-loading" role="status">Loading warning and reporting period...</p>}
    {!warnings.loading && !rows.length && <p className="management-help">No open warnings are loaded on this page. Check another page, or run an eligible assessment and reload.</p>}
    {selected && !assessment.loading && !period.resource.loading && !eligible && !assessment.error && !period.resource.error && <p className="notice error">The warning's completed reporting month could not be matched to its snapshot. Reload the records.</p>}
    <WorkflowPagination page={page} onPage={value => { setPage(value); setChosen(""); }} loading={warnings.loading}
      count={warnings.data?.totalElements} hasNext={warnings.data?.last === false} />
    </div></section>
    {active && eligible && selected && <RecommendationRequest key={`${selected.warningId}:${month}`} business={business} user={user} month={month} warning={selected} />}
  </div>;
}
