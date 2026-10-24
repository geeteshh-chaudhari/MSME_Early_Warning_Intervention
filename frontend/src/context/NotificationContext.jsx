import { createContext, useContext, useEffect, useState } from "react";
import { useNavigate } from "react-router";
import { useAuth } from "./AuthContext";
import { apiRequest } from "../api/client";

const NotificationContext = createContext({ rows: [], error: "", open: () => {} });
export function NotificationProvider({ children }) {
  const { user } = useAuth();
  return user ? <NotificationSession key={user.userId} user={user}>{children}</NotificationSession> : children;
}
function NotificationSession({ user, children }) {
  const [rows, setRows] = useState([]);
  const [toasts, setToasts] = useState([]);
  const [error, setError] = useState("");
  const navigate = useNavigate();
  useEffect(() => {
    const controller = new AbortController();
    let baseline = false;
    const seen = new Set();
    async function poll() {
      try {
        const events = await apiRequest("/notifications", { signal: controller.signal });
        if (controller.signal.aborted) return;
        const fresh = baseline ? events.filter(row => !seen.has(row.id)) : [];
        events.forEach(row => seen.add(row.id));
        // Compare recent IDs rather than a high-water mark: concurrent commits can arrive out of order.
        if (fresh.length) {
          setRows(previous => [...fresh, ...previous].filter((row, index, all) => all.findIndex(item => item.id === row.id) === index).slice(0, 100));
          setToasts(previous => [...fresh, ...previous].slice(0, 10));
          window.dispatchEvent(new CustomEvent("msme-data-changed", { detail: fresh }));
        }
        baseline = true; setError("");
      } catch (err) { if (!controller.signal.aborted) setError(err.message); }
      finally { if (!controller.signal.aborted) timer = setTimeout(poll, 5000); }
    }
    let timer;
    poll();
    return () => { controller.abort(); clearTimeout(timer); };
  }, [user.userId]);
  function open(row) {
    setToasts(previous => previous.filter(item => item.id !== row.id));
    navigate("/dashboard", { state: { notification: row } });
  }
  return <NotificationContext.Provider value={{ rows, error, open }}>
    {children}
    <div aria-live="polite" style={{ position: "fixed", right: 18, bottom: 18, zIndex: 1000, maxWidth: 400, maxHeight: "60vh", overflow: "auto" }}>
      {toasts.map(row => <div className="notice snapshot-success" key={row.id}>
        <button className="text-button" onClick={() => open(row)}>{row.businessName}: {row.message}</button>
        <button className="text-button" aria-label="Dismiss notification" onClick={() => setToasts(previous => previous.filter(item => item.id !== row.id))}>Dismiss</button>
      </div>)}
    </div>
  </NotificationContext.Provider>;
}
// eslint-disable-next-line react-refresh/only-export-components
export function useNotifications() { return useContext(NotificationContext); }
