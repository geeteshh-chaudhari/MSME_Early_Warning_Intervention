import { useNotifications } from "../context/NotificationContext";
import { dateLabel } from "./workflowFormat";

export default function Notifications() {
  const { rows, error, open } = useNotifications();
  return <section className="panel"><div className="panel-heading"><div><h2>Notifications</h2>
    <p>This login session only. Refreshing or signing out clears this list.</p></div></div>
    {error && <p className="notice error" role="alert">Notifications unavailable: {error}</p>}
    {!rows.length && <p className="workflow-inset">No new notifications in this session.</p>}
    <ol className="workflow-timeline">{rows.map(row => <li key={row.id}><div>
      <button className="text-button" onClick={() => open(row)}>{row.businessName}: {row.message}</button>
      <small>{dateLabel(row.createdAt)}</small>
    </div></li>)}</ol>
  </section>;
}
