import { useCallback, useEffect, useRef, useState } from "react";
import { apiRequest } from "../api/client";

export function useWorkflowResource(path, token, revision = 0) {
  const [result, setResult] = useState(null);
  const key = `${path}:${revision}:${token}`;
  useEffect(() => {
    if (!path) return;
    const controller = new AbortController();
    apiRequest(path, { token, signal: controller.signal })
      .then(data => { if (!controller.signal.aborted) setResult({ key, data }); })
      .catch(error => { if (!controller.signal.aborted) setResult({ key, error: error.message }); });
    return () => controller.abort();
  }, [path, token, revision, key]);
  return !path ? { loading: false } : result?.key === key
    ? { ...result, loading: false }
    : { loading: true };
}

export function useWorkflowAction() {
  const [state, setState] = useState({ busy: false, error: "", success: "" });
  const inFlight = useRef(false);
  const mounted = useRef(true);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  const execute = useCallback(async (work, message) => {
    if (inFlight.current) return { ok: false };
    inFlight.current = true;
    setState({ busy: true, error: "", success: "" });
    try {
      const data = await work();
      if (!mounted.current) return { ok: false };
      setState({ busy: false, error: "", success: typeof message === "function" ? message(data) : message || "Saved successfully." });
      return { ok: true, data };
    } catch (error) {
      if (mounted.current) setState({ busy: false, error: error.message, success: "" });
      return { ok: false };
    } finally { inFlight.current = false; }
  }, []);
  return { ...state, execute };
}
