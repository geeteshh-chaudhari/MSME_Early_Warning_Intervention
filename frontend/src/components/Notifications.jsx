import { useEffect, useRef, useState } from "react";
import { apiRequest } from "../api/client";
import { dateLabel, humanize } from "./workflowFormat";

export default function Notifications({ user }) {
  const [rows, setRows] = useState([]);
  const [error, setError] = useState("");
  const [toast, setToast] = useState("");
  const last = useRef(null);
  useEffect(() => {
    const controller = new AbortController();
    async function load() {
      try {
        const result = await apiRequest("/notifications", { token: user.accessToken, signal: controller.signal });
        if (controller.signal.aborted) return;
        const newer = result.filter(row => row.id > (last.current ?? 0));
        const urgent = newer.find(row => row.event.startsWith("ZERO_REVENUE"));
        const latest = urgent || (last.current != null ? newer[0] : null);
        if (latest) setToast(`${latest.businessName}: ${latest.message}`);
        last.current = result[0]?.id || 0; setRows(result); setError("");
      } catch (e) { if (!controller.signal.aborted) setError(e.message); }
    }
    load(); const timer = setInterval(load, 5000);
    return () => { clearInterval(timer); controller.abort(); };
  }, [user.accessToken]);
  return <section className="panel workflow-notifications">
    {toast && <div className="notice snapshot-success" role="status">{toast}<button className="text-button" onClick={() => setToast("")}>Dismiss</button></div>}
    <details><summary>Notifications · {rows.length} recent events</summary>
      {error && <p className="notice error" role="alert">Notifications unavailable: {error}</p>}
      {!rows.length && !error && <p className="management-help">No notifications yet.</p>}
      <ol className="workflow-timeline">{rows.map(row => <li key={row.id}><span className="workflow-timeline-dot" /><div><strong>{row.businessName} · {humanize(row.event)}</strong><small>{dateLabel(row.createdAt)}</small><p>{row.message}</p></div></li>)}</ol>
    </details>
  </section>;
}
