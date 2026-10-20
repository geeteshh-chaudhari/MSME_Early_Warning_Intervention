import { useEffect, useRef, useState } from "react";
import { RefreshCw, Sparkles, X } from "lucide-react";
import { insightsError, requestRecommendation } from "../api/insightsApi";
import { useReportingPeriod } from "../hooks/useReportingPeriod";
import { flattenEvidence, metricLabel, metricValue, monthLabel } from "./insightsFormat";
import EvidenceExplorer from "./EvidenceExplorer";

const defaultQuestion = "Interpret financial trends, seasonal context and propose RM review actions.";

function RecommendationRequest({ business, user, month }) {
  const [question, setQuestion] = useState(defaultQuestion);
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
    if (controller.current || !question.trim()) return;
    const current = ++sequence.current;
    const request = new AbortController(); controller.current = request;
    setState({ busy: true, data: null, error: "", info: "" }); setFocusedMetric("");
    try {
      const data = await requestRecommendation(user.accessToken, { msme_id: Number(business.msmeId), as_of: month, metrics_only: metricsOnly, question: question.trim() }, request.signal);
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
  const limitations = [...new Set([...(advice?.limitations || []), ...(evidence.business_context?.limitations || [])])];
  return <>
    <section className="panel"><div className="panel-heading"><div><h2><Sparkles size={19} /> Recommendations & evidence</h2><p>Analyse {business.businessName} for {monthLabel(month)}.</p></div></div>
      <form className="workflow-inset" onSubmit={submit}>
        {state.error && <div className="notice error" role="alert">{state.error}</div>}{state.info && <div className="notice workflow-info" role="status">{state.info}</div>}
        <fieldset className="snapshot-fieldset" disabled={state.busy}><div className="snapshot-fields"><div className="snapshot-field"><label htmlFor="recommendation-mode">Analysis mode</label><select id="recommendation-mode" value={metricsOnly ? "evidence" : "advice"} onChange={e => { setMetricsOnly(e.target.value === "evidence"); clearResult(); }}><option value="advice">Generate recommendations</option><option value="evidence">Evidence only — no model required</option></select><small className="management-help">Evidence is calculated by the service. Recommendations use its configured local model and require human review.</small></div>
          <div className="snapshot-field"><label htmlFor="recommendation-question">Question or focus</label><textarea id="recommendation-question" value={question} maxLength={2000} onChange={e => { setQuestion(e.target.value); clearResult(); }} required /></div></div>
          <div className="snapshot-actions"><button type="submit" className="button primary" disabled={!question.trim()}><Sparkles size={16} />{state.busy ? "Analysing…" : metricsOnly ? "Load evidence" : "Generate recommendations"}</button></div>
        </fieldset>
        {state.busy && <div className="recommendation-progress" role="status"><span><RefreshCw className="spin" size={18} />{metricsOnly ? "Calculating financial evidence…" : "Generating advice from financial evidence. The local model may take a few minutes."}</span><button type="button" className="button secondary" onClick={cancel}><X size={15} />Cancel request</button></div>}
        <p className="management-help">This analysis does not change financial records, warning decisions or intervention plans.</p>
      </form>
    </section>
    {data && <>
      <div className="insights-report-meta"><span>Reporting {monthLabel(data.as_of_month)}</span><span>{data.status === "generated" ? "Generated advice" : data.status === "metrics_only" ? "Evidence only" : data.status === "insufficient_data" ? "Insufficient data" : data.status}</span><span>Formula version {data.formula_version}</span>{data.model && <span>Model: {data.model}</span>}</div>
      {data.status === "metrics_only" && <div className="notice workflow-info">Evidence loaded without calling the model. Choose Generate recommendations to request advisory interpretation.</div>}
      {data.status === "insufficient_data" && <div className="notice workflow-info">There is not enough usable evidence for recommendations. Review unavailable metrics and their reasons below.</div>}
      {advice && <section className="panel"><div className="panel-heading"><div><h2>Analysis summary</h2><p>Advisory interpretation · review the evidence before taking action</p></div></div><p className="workflow-inset recommendation-summary">{advice.summary}</p></section>}
      {advice?.actions?.length > 0 && <section className="panel"><div className="panel-heading"><div><h2>Suggested actions</h2><p>Each action links to the metric evidence returned with this analysis.</p></div></div><div className="recommendation-actions">{advice.actions.map((item, index) => <article className="recommendation-action" key={`${index}:${item.title}`}><span className="recommendation-step">{String(index + 1).padStart(2, "0")}</span><div><h3>{item.title}</h3><p>{item.interpretation}</p><div className="recommended-action">{item.suggested_action}</div><div className="recommendation-citations">{(item.evidence_metric_ids || []).map(id => { const entry = entries.find(entry => entry.id === id); return <button key={id} className="evidence-citation" disabled={!entry} onClick={() => inspect(id)} title={id}>{entry ? `${metricLabel(entry.key)} · ${metricValue(entry.item)}` : `${id} · Evidence unavailable`}</button>; })}</div></div></article>)}</div></section>}
      {limitations.length > 0 && <section className="panel"><div className="panel-heading"><div><h2>Limitations & review notes</h2><p>Model citations support inspection; they do not guarantee the interpretation is correct.</p></div></div><ul className="insights-notes">{limitations.map(note => <li key={note}>{note}</li>)}</ul></section>}
      <div ref={evidenceRef}><EvidenceExplorer key={focusedMetric || "all"} evidence={evidence} focusedMetricId={focusedMetric} /></div>
      {data.tool_trace?.length > 0 && <details className="panel insights-trace"><summary>Analysis tools used</summary><ol>{data.tool_trace.map((step, index) => <li key={`${step.step}:${index}`}>{step.tool}</li>)}</ol></details>}
    </>}
  </>;
}

export default function RecommendationWorkspace({ business, user }) {
  const period = useReportingPeriod(user, business);
  const active = business.isActive === "Y";
  return <div className="workflow-stack"><section className="panel"><div className="panel-heading"><div><h2>Choose a reporting month</h2><p>Use submitted data from a completed month.</p></div><button className="button secondary" disabled={period.resource.loading} onClick={period.reload}>Reload reporting months</button></div><div className="workflow-inset">
    <div className="snapshot-field insights-month-field"><label htmlFor="recommendation-month">Reporting month</label><select id="recommendation-month" value={period.month} disabled={!active || period.resource.loading || !period.months.length} onChange={e => period.setMonth(e.target.value)}>{!period.months.length && <option value="">No completed months available</option>}{period.months.map(month => <option key={month} value={month}>{monthLabel(month)}</option>)}</select></div>
    {period.resource.error && <div className="notice error" role="alert">{period.resource.error}</div>}{!active && <div className="notice error" role="alert">Recommendations are available for active businesses only.</div>}
    {period.resource.loading && <p className="workflow-loading" role="status">Loading reporting months…</p>}{!period.resource.loading && !period.resource.error && !period.month && <p className="management-help">No completed-month snapshots are available. Submit financial data, then reload the reporting months.</p>}
  </div></section>{active && period.month && <RecommendationRequest key={period.month} business={business} user={user} month={period.month} />}</div>;
}
