import { useState } from "react";
import { flattenEvidence, metricGroups, metricLabel, metricNumber, metricValue } from "./insightsFormat";
import { humanize } from "./workflowFormat";
import { WorkflowPagination } from "./WorkflowUi";
import { InsightLineChart } from "./InsightCharts";

export default function EvidenceExplorer({ evidence, series, focusedMetricId = "" }) {
  const entries = flattenEvidence(evidence);
  const [group, setGroup] = useState("all");
  const [selectedId, setSelectedId] = useState(focusedMetricId);
  const [page, setPage] = useState(Math.max(0, Math.floor(entries.findIndex(entry => entry.id === focusedMetricId) / 5)));
  const filtered = entries.filter(entry => group === "all" || entry.group === group);
  const activePage = Math.min(page, Math.max(0, Math.ceil(filtered.length / 5) - 1));
  const visible = filtered.slice(activePage * 5, activePage * 5 + 5);
  const selected = filtered.find(entry => entry.id === selectedId) || visible[0];
  return <section className="evidence-explorer" aria-label="Metric evidence explorer">
    <section className="panel"><div className="panel-heading"><div><h2>Metric evidence library</h2><p>{entries.length} returned metrics · exact values, availability and calculation context</p></div><div className="snapshot-field"><label htmlFor="evidence-group">Metric group</label><select id="evidence-group" value={group} onChange={e => { setGroup(e.target.value); setPage(0); setSelectedId(""); }}><option value="all">All metric groups</option>{Object.entries(metricGroups).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></div></div>
      {entries.length ? <><div className="table-scroll"><table><thead><tr><th>Metric</th><th>Value</th><th>Group</th><th>Availability</th></tr></thead><tbody>{visible.map(entry => <tr key={entry.id} className={selected?.id === entry.id ? "selected-row" : ""}><td><button className="table-open evidence-metric-button" onClick={() => setSelectedId(entry.id)}>{metricLabel(entry.key)}</button></td><td className="strong">{metricValue(entry.item)}</td><td>{metricGroups[entry.group] || humanize(entry.group)}</td><td><span className={`badge ${metricNumber(entry.item) !== null ? "success" : "neutral"}`}>{metricNumber(entry.item) !== null ? "Available" : "Unavailable"}</span></td></tr>)}</tbody></table></div><WorkflowPagination page={activePage} onPage={setPage} loading={false} count={filtered.length} hasNext={(activePage + 1) * 5 < filtered.length} /></>
        : <div className="empty-state"><h3>No metric evidence returned</h3><p>The service did not return any numeric metric groups.</p></div>}
    </section>
    {selected && <section className="panel"><div className="panel-heading"><div><h2>{metricLabel(selected.key)}</h2><p className="evidence-id">{selected.id}</p></div><strong className="evidence-value">{metricValue(selected.item)}</strong></div>
      <div className="workflow-inset"><p className="management-help">{selected.item.meaning || selected.item.reason || "Calculated by the financial metric service."}</p><dl className="evidence-details">{Object.entries(selected.item).filter(([key, value]) => !["value", "status", "meaning"].includes(key) && value != null).map(([key, value]) => <div key={key}><dt>{humanize(key)}</dt><dd>{typeof value === "object" ? JSON.stringify(value) : String(value)}</dd></div>)}</dl></div>
      {series && <InsightLineChart title="Selected metric over time" description="Missing metrics produce gaps; values are supplied by the service." rows={series} compact unit={selected.item.unit === "percent" ? "%" : selected.item.unit === "percentage_points" ? " pp" : selected.item.unit === "months" ? " months" : ""} lines={[{ label: metricLabel(selected.key), get: row => metricNumber(row.metrics?.[selected.id]) }]} />}
    </section>}
  </section>;
}
