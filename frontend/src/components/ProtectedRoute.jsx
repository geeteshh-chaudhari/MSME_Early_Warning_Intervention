import { Navigate, Outlet } from "react-router";
import { useAuth } from "../context/AuthContext";

export default function ProtectedRoute() {
  const { user, restoring } = useAuth();
  if (restoring) return <p role="status">Restoring your session...</p>;

  return user ? <Outlet /> : <Navigate to="/login" replace />;
}