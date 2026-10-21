import { useState } from "react";
import { useWorkflowResource } from "../hooks/useWorkflowResource";
import { ResourceState } from "./WorkflowUi";
import { listOf } from "./workflowFormat";

export default function ReadinessPanel({ business, user, snapshots }) {
  const months = [...new Set(snapshots.map(s => String(s.reportingPeriod).slice(0, 7)))].sort().reverse();
  const [selected, setSelected] = useState("");
  const month = selected || months[0];
  const readiness = useWorkflowResource(month ? `/assessment-data/msmes/${business.msmeId}/readiness/${month}-01` : null, user.accessToken);
  const configs = useWorkflowResource(`/assessment/msmes/${business.msmeId}/thresholds?size=1`, user.accessToken);
  const data = readiness.data;
  return <section className="panel"><div className="panel-heading"><div><h2>Assessment readiness</h2><p>Results are processed automatically. Saved assessments are never recalculated.</p></div>
    <select aria-label="Check reporting month readiness" value={month || ""} onChange={e => setSelected(e.target.value)}>{months.map(m => <option key={m}>{m}</option>)}</select></div>
    {!month ? <p className="workflow-inset management-help">Submit a completed reporting month to begin.</p> : <ResourceState resource={readiness}>{data && <div className="workflow-inset">
      {data.missingPeriods?.length > 0 ? <p>Assessment unavailable. Add financial data for: <strong>{data.missingPeriods.join(", ")}</strong>.</p>
        : !data.assessmentReady ? <p>Waiting for complete monthly history. Zero values are included in the assessment.</p>
        : configs.loading ? <p>Checking configuration…</p>
        : configs.error ? <p className="notice error">Configuration lookup unavailable: {configs.error}</p>
        : !listOf(configs.data).length ? <p>Financial history is complete. Waiting for Risk Admin to configure thresholds.</p>
        : <p>History and configuration are ready. The assessment is processed automatically; see Assessments and Notifications for its result.</p>}
    </div>}</ResourceState>}
  </section>;
}
