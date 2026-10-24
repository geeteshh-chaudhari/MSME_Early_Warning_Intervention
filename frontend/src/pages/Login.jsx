import { useState } from "react";
import { Navigate } from "react-router";
import {
  ArrowRight,
  Building2,
  Check,
  Eye,
  EyeOff,
  LockKeyhole,
  Mail,
  ShieldCheck,
  TrendingUp,
} from "lucide-react";

import { useAuth } from "../context/AuthContext";

export default function Login() {
  const { user, login, restoring } = useAuth();

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  if (restoring) return <p role="status">Restoring your session...</p>;

  if (user) {
    return <Navigate to="/dashboard" replace />;
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setLoading(true);
    setError("");

    try {
      await login(email.trim(), password);
    } catch (error) {
      setError(
        error.status === 401
          ? "Incorrect email or password. Please try again."
          : error.message
      );
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="login-page">
      <section className="login-story">
        <div className="brand">
          <span className="brand-icon">
            <TrendingUp size={25} />
          </span>

          <div>
            MSME<span className="brand-light">pulse</span>
            <small>EARLY WARNING & INTERVENTION</small>
          </div>
        </div>

        <div className="story-copy">
          <div className="eyebrow">
            <span className="status-dot" />
            BETTER INSIGHTS. TIMELY ACTION.
          </div>

          <h1>
            A clearer view.
            <br />
            A stronger <em>future.</em>
          </h1>

          <p>
            Understand your business health, recognise early signals
            and take the next step with confidence.
          </p>

          <div className="login-visual">
            <div className="visual-heading">
              <span>BUSINESS HEALTH</span>
              <span className="badge success">Connected insights</span>
            </div>

            <h3>Small signals. Meaningful action.</h3>
            <p>Your financial story, brought together.</p>

            <svg
              viewBox="0 0 440 130"
              className="login-chart"
              aria-hidden="true"
            >
              <defs>
                <linearGradient id="loginFill" x1="0" y1="0" x2="0" y2="1">
                  <stop stopColor="#61dfb8" stopOpacity=".3" />
                  <stop offset="1" stopColor="#61dfb8" stopOpacity="0" />
                </linearGradient>
              </defs>

              <path
                d="M0 110 40 95 80 101 120 67 160 78 200 49 240 60 280 30 320 43 360 17 400 28 440 8V130H0Z"
                fill="url(#loginFill)"
              />

              <path
                d="M0 110 40 95 80 101 120 67 160 78 200 49 240 60 280 30 320 43 360 17 400 28 440 8"
                fill="none"
                stroke="#61dfb8"
                strokeWidth="3"
              />
            </svg>

            <div className="visual-footer">
              <span><Check size={15} /> Monitor</span>
              <span><Check size={15} /> Understand</span>
              <span><Check size={15} /> Act</span>
            </div>
          </div>

          <div className="story-caption">
            <ShieldCheck size={18} />
            One workspace. The right view for every role.
          </div>
        </div>

        <div className="story-bottom">
          Built for the businesses that move India forward.
        </div>
      </section>

      <section className="login-panel">
        <div className="login-security">
          <ShieldCheck size={16} />
          Your trusted business workspace
        </div>

        <div className="login-form-container">
          <div className="login-emblem">
            <Building2 size={29} />
          </div>

          <p className="eyebrow teal">WELCOME TO MSMEPULSE</p>
          <h2>Good to see you again.</h2>
          <p className="muted">
            Sign in to see what matters to your business.
          </p>

          <form onSubmit={handleSubmit} className="login-form">
            <label htmlFor="email">Email address</label>

            <div className="input-with-icon">
              <Mail size={19} />
              <input
                id="email"
                type="email"
                placeholder="you@company.com"
                autoComplete="username"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                required
              />
            </div>

            <label htmlFor="password">Password</label>

            <div className="input-with-icon">
              <LockKeyhole size={19} />
              <input
                id="password"
                type={showPassword ? "text" : "password"}
                placeholder="Enter your password"
                autoComplete="current-password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                required
              />

              <button
                type="button"
                className="icon-button"
                aria-label={showPassword ? "Hide password" : "Show password"}
                onClick={() => setShowPassword(!showPassword)}
              >
                {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
              </button>
            </div>

            <p className="login-hint">
              Your account determines your dashboard and permissions.
            </p>

            {error && (
              <div className="notice error" role="alert">
                {error}
              </div>
            )}

            <button
              type="submit"
              className="button primary login-submit"
              disabled={loading}
            >
              {loading ? "Signing in…" : "Sign in to workspace"}
              <ArrowRight size={18} />
            </button>
          </form>

          <div className="login-roles">
            <span>MSME Representative</span>
            <span>Relationship Manager</span>
            <span>Risk Admin</span>
          </div>

          <p className="login-help">
            Need access? Contact your system administrator.
          </p>
        </div>

        <p className="login-copyright">
          MSMEpulse © {new Date().getFullYear()}
        </p>
      </section>
    </div>
  );
}