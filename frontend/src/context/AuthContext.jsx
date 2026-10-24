import { createContext, useCallback, useContext, useEffect, useState } from "react";
import { apiRequest } from "../api/client";

const AuthContext = createContext(null);
export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [restoring, setRestoring] = useState(true);
  const [sessionError, setSessionError] = useState("");
  useEffect(() => {
    const controller = new AbortController();
    apiRequest("/auth/session", { signal: controller.signal })
      .then(setUser)
      .catch(error => { if (error.name !== "AbortError" && error.status !== 401) setSessionError(error.message); })
      .finally(() => { if (!controller.signal.aborted) setRestoring(false); });
    return () => controller.abort();
  }, []);
  const login = useCallback(async (email, password) => {
    const result = await apiRequest("/auth/login", {
      method: "POST", headers: { "X-Auth-Mode": "cookie" }, body: { email, password },
    });
    if (!result?.userId || !["RM", "MSME_REP", "RISK_ADMIN"].includes(result.role))
      throw new Error("Your account does not have a supported role.");
    setSessionError(""); setUser(result);
  }, []);
  const logout = useCallback(async () => {
    try {
      await apiRequest("/auth/logout", { method: "POST" });
      setUser(null); setSessionError("");
    } catch (error) { setSessionError(`Sign out could not complete: ${error.message}`); }
  }, []);
  return <AuthContext.Provider value={{ user, login, logout, restoring, sessionError }}>
    {sessionError && <div className="notice error" role="alert">{sessionError}</div>}{children}
  </AuthContext.Provider>;
}
// eslint-disable-next-line react-refresh/only-export-components
export function useAuth() { return useContext(AuthContext); }
