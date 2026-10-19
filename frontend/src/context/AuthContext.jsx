import { createContext, useCallback, useContext, useState } from "react";
import { apiRequest } from "../api/client";

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);

  const login = useCallback(async (email, password) => {
    const result = await apiRequest("/auth/login", {
      method: "POST",
      body: { email, password },
    });

    if (
      !result?.accessToken ||
      !["RM", "MSME_REP", "RISK_ADMIN"].includes(result.role)
    ) {
      throw new Error("Your account does not have a supported role.");
    }

    setUser(result);
  }, []);

  const logout = useCallback(() => {
    setUser(null);
  }, []);

  return (
    <AuthContext.Provider value={{ user, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth() {
  return useContext(AuthContext);
}