import { useEffect, useState } from "react";
import { useAuth } from "../context/AuthContext";
import { apiRequest } from "../api/client";

export default function MsmePage() {
  const { user, logout } = useAuth();

  const [msmes, setMsmes] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;

    async function loadMsmes() {
      setLoading(true);
      setError("");

      const paths = {
        RISK_ADMIN: "/msmes",
        RM: "/msmes/my",
        MSME_REP: "/msmes/me",
      };

      try {
        const path = paths[user.role];

        if (!path) {
          throw new Error("Unsupported user role.");
        }

        const result = await apiRequest(path, {
          token: user.accessToken,
        });

        if (active) {
          // The representative endpoint returns one object.
          setMsmes(Array.isArray(result) ? result : result ? [result] : []);
        }
      } catch (error) {
        if (active) {
          if (error.status === 401) {
            logout();
          } else {
            setError(error.message);
          }
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    loadMsmes();

    return () => {
      active = false;
    };
  }, [user, logout]);

  return (
    <main>
      <h1>MSMEs</h1>
      <p>
        Welcome, {user.name} — {user.role}
      </p>

      <button onClick={logout}>Logout</button>

      {loading && <p>Loading...</p>}
      {error && <p role="alert">{error}</p>}

      {!loading && !error && msmes.length === 0 && (
        <p>No MSMEs available.</p>
      )}

      {!loading && !error && msmes.length > 0 && (
        <pre>{JSON.stringify(msmes, null, 2)}</pre>
      )}
    </main>
  );
}