import { amountLabel } from "./workflowFormat";
import { finiteValue, monthLabel } from "./insightsFormat";

const colors = ["#28886f", "#d49a4c", "#6d8db7", "#ab86b5", "#879953", "#b97279"];

export function InsightLineChart({ title, description, rows = [], lines, unit = "", compact = false }) {
  const width = 700, height = compact ? 210 : 250;
  const pad = { left: 65, right: 22, top: 25, bottom: 40 };
  const plotWidth = width - pad.left - pad.right, plotHeight = height - pad.top - pad.bottom;
  const values = lines.flatMap(line => rows.map(row => finiteValue(line.get(row)))).filter(value => value !== null);
  let min = Math.min(0, ...values), max = Math.max(0, ...values);
  if (min === max) max = min + 1;
  const span = max - min;
  max += span * 0.08;
  if (min < 0) min -= span * 0.06;
  const x = index => pad.left + (rows.length > 1 ? index * plotWidth / (rows.length - 1) : plotWidth / 2);
  const y = value => pad.top + (max - value) * plotHeight / (max - min);
  const formatAxis = value => new Intl.NumberFormat("en-IN", { notation: "compact", maximumFractionDigits: 1 }).format(value);
  const tickStep = Math.max(1, Math.ceil(rows.length / 5));
  return <section className="panel insight-chart-panel"><div className="panel-heading"><div><h2>{title}</h2><p>{description}</p></div></div>
    {!values.length ? <div className="empty-state"><h3>No comparable observations</h3><p>Missing observations remain unavailable.</p></div> : <>
      <div className="insight-chart-legend">{lines.map((line, index) => <span key={line.label}><i style={{ background: colors[index % colors.length] }} />{line.label}</span>)}<small>{unit || "Database amount units"}</small></div>
      <div className="insight-svg-wrap"><svg viewBox={`0 0 ${width} ${height}`} role="img" aria-label={title}><title>{title}. Gaps indicate unavailable observations. Focus a point for its exact value.</title>
        {Array.from({ length: 5 }, (_, index) => { const value = min + (max - min) * index / 4; return <g key={index}><line x1={pad.left} x2={width - pad.right} y1={y(value)} y2={y(value)} className="insight-gridline" /><text x={pad.left - 10} y={y(value) + 4} textAnchor="end" className="insight-axis">{formatAxis(value)}</text></g>; })}
        {rows.map((row, index) => (index % tickStep === 0 || index === rows.length - 1) && <text key={`${row.month}:${index}`} x={x(index)} y={height - 12} textAnchor="middle" className="insight-axis">{row.label || monthLabel(row.month)}</text>)}
        {lines.map((line, index) => {
          let path = "", connected = false;
          rows.forEach((row, i) => { const value = finiteValue(line.get(row)); if (value === null) { connected = false; return; } path += `${connected ? " L" : " M"}${x(i)} ${y(value)}`; connected = true; });
          return <g key={line.label}><path d={path} fill="none" stroke={colors[index % colors.length]} strokeWidth="2.5" strokeDasharray={line.dashed ? "6 5" : undefined} />
            {!line.dashed && rows.map((row, i) => { const value = finiteValue(line.get(row)); return value === null ? null : <circle key={`${row.month}:${i}`} cx={x(i)} cy={y(value)} r="4" fill={colors[index % colors.length]} tabIndex={0}><title>{row.label || monthLabel(row.month)} · {line.label}: {amountLabel(value)}{unit}</title></circle>; })}
          </g>;
        })}
      </svg></div>
    </>}
  </section>;
}

export function SavedBreachMatrix({ report }) {
  const rows = (report.series || []).slice(-12);
  const indicators = report.saved_indicators || [];
  return <section className="panel"><div className="panel-heading"><div><h2>Saved indicator breaches</h2><p>Historical assessment flags · most recent 12 months · not recalculated by this dashboard</p></div></div>
    <div className="table-scroll"><table className="breach-matrix"><thead><tr><th>Indicator</th>{rows.map(row => <th key={row.month}>{monthLabel(row.month)}</th>)}</tr></thead><tbody>
      {[["REVENUE_DETERIORATION", "Revenue"], ["OVERDUE_RECEIVABLES", "Overdue"], ["CREDIT_UTILISATION", "Credit"]].map(([code, label]) => <tr key={code}><th>{label}</th>{rows.map(row => {
        const entry = indicators.find(item => item.indicator_code === code && String(item.reporting_period).slice(0, 7) === row.month.slice(0, 7));
        const known = entry && finiteValue(entry.calculated_value) !== null && ["Y", "N"].includes(entry.breached_flag);
        const text = !known ? "Unavailable" : entry.breached_flag === "Y" ? "Breached" : "Within threshold";
        return <td key={row.month}><span className={`breach-cell ${!known ? "missing" : entry.breached_flag === "Y" ? "breached" : "clear"}`} title={`${monthLabel(row.month)}: ${text}`} aria-label={text}>{!known ? "—" : entry.breached_flag === "Y" ? "!" : "✓"}</span></td>;
      })}</tr>)}
    </tbody></table></div><p className="workflow-inset management-help">! Breached · ✓ Within its saved threshold · — Unavailable. New growth metrics do not inherit these thresholds.</p>
  </section>;
}
