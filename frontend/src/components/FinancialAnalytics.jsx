import { useState } from "react";
import { BarChart3, RefreshCw } from "lucide-react";
import { dashboardReportPath, insightsError } from "../api/insightsApi";
import { useReportingPeriod } from "../hooks/useReportingPeriod";
import { useWorkflowResource } from "../hooks/useWorkflowResource";
import { amountLabel } from "./workflowFormat";
import { finiteValue, metricNumber, metricValue, monthLabel } from "./insightsFormat";
import { ResourceState, WorkflowPagination, WorkflowStatus } from "./WorkflowUi";
import { InsightLineChart, SavedBreachMatrix } from "./InsightCharts";
import EvidenceExplorer from "./EvidenceExplorer";

export default function FinancialAnalytics({ business, user }) {
  const period = useReportingPeriod(user, business);
  const [revision, setRevision] = useState(0);
  const [windowSize, setWindowSize] = useState(12);
  const [historyPage, setHistoryPage] = useState(0);
  const active = business.isActive === "Y";
  const resource = useWorkflowResource(active && period.month ? dashboardReportPath(business.msmeId, period.month) : null, user.accessToken, revision);
  const report = resource.data;
  const series = report?.series || [];
  const windowRows = series.slice(-windowSize);
  const latest = series.at(-1)?.snapshot;
  const evidence = report?.evidence || {};
  const saved = report?.context?.assessment;
  const notes = [...new Set([...(report?.context?.limitations || []), ...(report?.notes || [])])];
  const currentMetric = (group, key) => evidence[group]?.[key];
  const monthlyRows = [...windowRows].reverse();
  const page = Math.min(historyPage, Math.max(0, Math.ceil(monthlyRows.length / 5) - 1));
  const years = [...new Set(series.filter(row => row.available).map(row => row.month.slice(0, 4)))];
  const seasonalRows = Array.from({ length: 12 }, (_, index) => ({ month: `2000-${String(index + 1).padStart(2, "0")}-01`, label: new Date(2000, index, 1).toLocaleDateString("en-IN", { month: "short" }), index: index + 1 }));
  const snapshotValue = (row, field) => row.available === false ? null : finiteValue(row.snapshot?.[field]);

  return <div className="workflow-stack">
    <section className="panel"><div className="panel-heading"><div><h2><BarChart3 size={19} /> Financial analytics</h2><p>Dashboard service · calculated metrics and up to 48 months of financial history</p></div><button className="button secondary" disabled={resource.loading || period.resource.loading} onClick={() => { period.reload(); setRevision(v => v + 1); }}><RefreshCw size={15} />Refresh analytics</button></div>
      <div className="workflow-inset"><div className="insights-filters"><div className="snapshot-field"><label htmlFor="analytics-month">Reporting month</label><select id="analytics-month" value={period.month} onChange={e => { period.setMonth(e.target.value); setHistoryPage(0); }} disabled={!active || period.resource.loading || !period.months.length}>{!period.months.length && <option value="">No completed months available</option>}{period.months.map(month => <option key={month} value={month}>{monthLabel(month)}</option>)}</select></div>
        <div className="snapshot-field"><label htmlFor="analytics-window">History window</label><select id="analytics-window" value={windowSize} onChange={e => { setWindowSize(Number(e.target.value)); setHistoryPage(0); }}>{[12, 24, 48].map(size => <option key={size} value={size}>{size} months</option>)}</select></div></div>
        <p className="management-help">Choose a completed reporting month with submitted data. Amounts retain their database units; missing observations are not treated as zero.</p>
        {!active && <div className="notice error" role="alert">Financial analytics is available for active businesses only.</div>}
        {period.resource.error && <div className="notice error" role="alert">{period.resource.error}</div>}
      </div>
    </section>
    {period.resource.loading && <div className="loading-state" role="status">Loading reporting months…</div>}
    {!period.resource.loading && !period.resource.error && !period.month && <div className="panel empty-state"><h3>No completed reporting months</h3><p>Submit financial data for a completed month, then refresh analytics.</p></div>}
    {active && period.month && <ResourceState resource={{ ...resource, error: resource.error ? insightsError({ message: resource.error, status: resource.errorStatus }, "dashboard") : null }}>{report && <>
      <div className="insights-report-meta"><span>Reporting {monthLabel(report.as_of)}</span><span>{report.context?.history_months_available ?? series.filter(row => row.available).length} snapshots available</span><span>Formula version {report.formula_version}</span></div>
      <div className="stats-grid">{[
        ["Monthly revenue", amountLabel(latest?.revenue), "Database amount units"],
        ["Revenue · year over year", metricValue(currentMetric("revenue_metrics", "revenue_yoy_growth_pct")), currentMetric("revenue_metrics", "revenue_yoy_growth_pct")?.reason || "Matching calendar month last year"],
        ["Overdue receivables", metricValue(currentMetric("liquidity_metrics", "overdue_ratio_pct")), "Share of total receivables"],
        ["Credit utilisation", metricValue(currentMetric("liquidity_metrics", "credit_utilisation_pct")), "Uses the current sanctioned limit"],
      ].map(([label, value, note]) => <article className="stat-card insight-stat" key={label}><div className="stat-top">{label}</div><strong>{value}</strong><p>{note}</p></article>)}</div>
      <div className="insights-chart-grid">
        <InsightLineChart title="Revenue history" description="Submitted revenue; gaps remain visible." rows={windowRows} lines={[{ label: "Revenue", get: row => snapshotValue(row, "revenue") }]} />
        <InsightLineChart title="Receivables & collections" description="Overdue receivables are a subset of total receivables." rows={windowRows} lines={[{ label: "Total receivables", get: row => snapshotValue(row, "total_receivables") }, { label: "Overdue receivables", get: row => snapshotValue(row, "overdue_receivables") }]} />
        <InsightLineChart title="Credit usage" description="The dashed reference is the current limit, not historical limits." rows={windowRows} lines={[{ label: "Credit used", get: row => snapshotValue(row, "credit_used") }, { label: "Current limit", dashed: true, get: () => finiteValue(report.credit_limit) }]} />
        <InsightLineChart title="Liquidity ratios" description="Calculated values supplied by the dashboard service." rows={windowRows} unit="%" lines={[{ label: "Overdue ratio", get: row => metricNumber(row.metrics?.["liquidity_metrics.overdue_ratio_pct"]) }, { label: "Credit utilisation", get: row => metricNumber(row.metrics?.["liquidity_metrics.credit_utilisation_pct"]) }]} />
      </div>
      <InsightLineChart title="Same-calendar-month revenue comparison" description="Exploratory year overlays. Differences do not establish seasonality or its cause." rows={seasonalRows} lines={years.map(year => ({ label: year, get: row => finiteValue(series.find(item => item.month.startsWith(`${year}-${String(row.index).padStart(2, "0")}`))?.snapshot?.revenue) }))} />
      <SavedBreachMatrix report={report} />
      <section className="panel"><div className="panel-heading"><div><h2>Saved assessment context</h2><p>Historical classification for this reporting month; analytics does not create a new risk score.</p></div>{saved && <WorkflowStatus value={saved.severity} />}</div><div className="workflow-inset">{saved ? <p className="management-help">Assessment #{saved.assessment_id} · Threshold #{saved.threshold_config_id} · Revenue decline {amountLabel(saved.revenue_decline_pct)}% · Overdue {amountLabel(saved.overdue_receivable_pct)}% · Credit {amountLabel(saved.credit_utilisation_pct)}%</p> : <p className="management-help">No saved assessment is available for this reporting month. No default thresholds are assumed.</p>}</div></section>
      <EvidenceExplorer key={period.month} evidence={evidence} series={windowRows} />
      <section className="panel"><div className="panel-heading"><div><h2>Monthly observations</h2><p>Latest first · 5 per page · selected {windowSize}-month window</p></div></div><div className="table-scroll"><table><thead><tr><th>Month</th><th>Revenue</th><th>Total receivables</th><th>Overdue</th><th>Credit used</th><th>Availability</th></tr></thead><tbody>{monthlyRows.slice(page * 5, page * 5 + 5).map(row => <tr key={row.month}><td>{monthLabel(row.month)}</td><td>{amountLabel(row.snapshot?.revenue)}</td><td>{amountLabel(row.snapshot?.total_receivables)}</td><td>{amountLabel(row.snapshot?.overdue_receivables)}</td><td>{amountLabel(row.snapshot?.credit_used)}</td><td>{row.available ? "Available" : "Unavailable"}</td></tr>)}</tbody></table></div><WorkflowPagination page={page} onPage={setHistoryPage} loading={false} count={monthlyRows.length} hasNext={(page + 1) * 5 < monthlyRows.length} /></section>
      <section className="panel"><div className="panel-heading"><div><h2>Data notes & limitations</h2><p>Interpret the metrics with their comparison periods and assumptions.</p></div></div><ul className="insights-notes">{notes.map(note => <li key={note}>{note}</li>)}</ul></section>
    </>}</ResourceState>}
  </div>;
}
