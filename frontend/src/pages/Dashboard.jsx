import { recommendationText } from "../components/workflowFormat";
import FinancialSnapshotForm from "../components/FinancialSnapshotForm";
import BusinessForm from "../components/BusinessForm";
import RepresentativeManagement from "../components/RepresentativeManagement";
import AssessmentWorkflow from "../components/AssessmentWorkflow";
import ThresholdManagement from "../components/ThresholdManagement";
import WarningWorkflow from "../components/WarningWorkflow";
import FinancialAnalytics from "../components/FinancialAnalytics";
import RecommendationWorkspace from "../components/RecommendationWorkspace";
import { WorkflowPagination } from "../components/WorkflowUi";
import { useEffect, useRef, useState } from "react";
import {
  Activity,
  BarChart3,
  ArrowRight,
  Building2,
  ChevronDown,
  CircleAlert,
  FileText,
  LayoutDashboard,
  LogOut,
  Pencil,
  Plus,
  Menu,
  RefreshCw,
  Search,
  ShieldCheck,
  Sparkles,
  TrendingUp,
  UserRound,
  Users,
  Wallet,
  X,
} from "lucide-react";

import { useAuth } from "../context/AuthContext";
import { apiRequest } from "../api/client";
import { deactivateBusiness } from "../api/businessApi";

const ROLE_CONFIG = {
  MSME_REP: {
    label: "MSME Representative",
    title: "Your business, at a glance.",
    description: "A clear view of your financial health and next steps.",
    portfolio: "My business",
    endpoint: "/msmes/me",
  },
  RM: {
    label: "Relationship Manager",
    title: "Your portfolio, in perspective.",
    description:
      "Stay close to your businesses and the signals that matter.",
    portfolio: "My portfolio",
    endpoint: "/msmes/my",
  },
  RISK_ADMIN: {
    label: "Risk Administrator",
    title: "A wider view. Better oversight.",
    description:
      "Monitor business coverage and review financial risk signals.",
    portfolio: "All businesses",
    endpoint: "/msmes",
  },
};

function number(value) {
  if (value == null || !Number.isFinite(Number(value))) return "—";

  return new Intl.NumberFormat("en-IN", {
    maximumFractionDigits: 0,
  }).format(Number(value));
}

function compact(value) {
  if (value == null || !Number.isFinite(Number(value))) return "—";

  return new Intl.NumberFormat("en-IN", {
    notation: "compact",
    maximumFractionDigits: 1,
  }).format(Number(value));
}

function readable(value) {
  return value ? String(value).replaceAll("_", " ") : "Not available";
}

function formatDate(value) {
  if (!value) return "—";

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) return "—";

  return date.toLocaleDateString("en-IN", {
    day: "numeric",
    month: "short",
    year: "numeric",
  });
}

function toList(data) {
  if (Array.isArray(data)) return data;
  if (Array.isArray(data?.content)) return data.content;
  return [];
}

function Badge({ value }) {
  const normalized = String(value || "").toUpperCase();

  const tone = ["LOW", "HEALTHY", "CLOSED", "CLOSED_REVIEWED", "ACTIVE", "APPROVED"].includes(normalized)
    ? "success"
    : ["HIGH", "HIGH_PRIORITY", "CRITICAL", "OPEN", "ESCALATED", "REJECTED"].includes(normalized)
      ? "danger"
      : "warning";

  return (
    <span className={`badge ${tone}`}>
      {readable(value)}
    </span>
  );
}

function StatCard({ icon, label, value, note, tone = "" }) {
  return (
    <article className="stat-card">
      <div className="stat-top">
        <span>{label}</span>
        <span className={`stat-icon ${tone}`}>{icon}</span>
      </div>

      <strong>{value}</strong>
      <p>{note}</p>
    </article>
  );
}

function Empty({ title, children }) {
  return (
    <div className="empty-state">
      <FileText size={29} />
      <h3>{title}</h3>
      <p>{children}</p>
    </div>
  );
}

function RevenueChart({ snapshots }) {
  const rows = [...snapshots]
    .sort((a, b) =>
      String(a.reportingPeriod).localeCompare(String(b.reportingPeriod))
    )
    .slice(-8);

  if (!rows.length) {
    return (
      <Empty title="Your financial story starts here">
        Revenue trends will appear when financial snapshots are available.
      </Empty>
    );
  }

  const max = Math.max(
    ...rows.map((row) => Number(row.revenue) || 0),
    1
  );

  return (
    <>
      <div className="chart-legend">
        <span>
          <i /> Monthly revenue
        </span>
        <span>Reported database units</span>
      </div>

      <div className="revenue-chart">
        <div className="chart-y">
          <span>{compact(max)}</span>
          <span>{compact(max / 2)}</span>
          <span>0</span>
        </div>

        <div className="chart-bars">
          {rows.map((row) => (
            <div
              className="bar-column"
              key={row.snapshotId ?? row.reportingPeriod}
            >
              <div className="bar-track">
                <div
                  className="revenue-bar"
                  style={{
                    height: `${
                      ((Number(row.revenue) || 0) / max) * 100
                    }%`,
                  }}
                  title={`${formatDate(row.reportingPeriod)}: ${number(
                    row.revenue
                  )}`}
                >
                  <span>{compact(row.revenue)}</span>
                </div>
              </div>

              <small>
                {new Date(row.reportingPeriod).toLocaleDateString(
                  "en-IN",
                  {
                    month: "short",
                    year: "2-digit",
                  }
                )}
              </small>
            </div>
          ))}
        </div>
      </div>
    </>
  );
}

function WarningList({ warnings, error }) {
  if (error) {
    return (
      <Empty title="Warnings unavailable">
        {error}
      </Empty>
    );
  }

  if (!warnings.length) {
    return (
      <Empty title="No warnings to display">
        Warnings will appear here when raised for this business.
      </Empty>
    );
  }

  return (
    <div className="warning-list">
      {warnings.map((warning) => (
        <article
          className="warning-item"
          key={warning.warningId}
        >
          <div className="warning-symbol">
            <CircleAlert size={20} />
          </div>

          <div className="warning-body">
            <div className="warning-title">
              <h3>{readable(warning.primaryDriver)}</h3>
              <Badge value={warning.warningStatus} />
            </div>

            <p>
              {warning.whyNowText ||
                "Review this warning with your relationship manager."}
            </p>

            {warning.recommendedAction && (
              <div className="recommended-action">
                <ArrowRight size={15} />
                {recommendationText(warning.recommendedAction)}
              </div>
            )}

            <small>
              {formatDate(warning.createdAt)} · Warning #
              {warning.warningId}
            </small>
          </div>
        </article>
      ))}
    </div>
  );
}

function BusinessWorkspace({ business, user, tab }) {
  const [resource, setResource] = useState(null);
  const [financialPage, setFinancialPage] = useState(0);

  useEffect(() => {
    const controller = new AbortController();

    const endpoints = [
       [
    "snapshots",
    user.role === "MSME_REP"
      ? "/snapshots/my"
      : `/snapshots/msme/${business.msmeId}`,
  ],

  ...(user.role !== "MSME_REP"
    ? [
        [
          "assessments",
          `/assessment/msmes/${business.msmeId}/assessments/latest`,
        ],
      ]
    : []),

  [
    "warnings",
    `/warnings?msmeId=${business.msmeId}&size=100`,
  ],
    ];

    Promise.allSettled(
      endpoints.map(async ([name, path]) => {
        try {
          const response = await apiRequest(path, {
            token: user.accessToken,
            signal: controller.signal,
          });
          return { name, data: name === "assessments" ? (response ? [response] : []) : toList(response) };
        } catch (error) {
          if (name === "assessments" && error.status === 404) return { name, data: [] };
          throw error;
        }
      })
    ).then((results) => {
      if (controller.signal.aborted) return;

      const next = {
        snapshots: [],
        assessments: [],
        warnings: [],
        errors: {},
      };

      results.forEach((result, index) => {
        const name = endpoints[index][0];

        if (result.status === "fulfilled") {
          next[result.value.name] = result.value.data;
        } else {
          // A failure in one service must not clear the login
          // or hide data successfully returned by another service.
          next.errors[name] =
            result.reason?.status === 401
              ? `The ${name} service rejected your login token.`
              : result.reason?.message ||
                `Unable to load ${name}.`;
        }
      });

      setResource(next);
    });

    return () => controller.abort();
  }, [business.msmeId, user]);

  if (!resource) {
    return (
      <div className="loading-state" role="status">
        <RefreshCw className="spin" size={22} />
        Loading business insights…
      </div>
    );
  }

  const { snapshots, assessments, warnings, errors } = resource;

  function handleSnapshotSaved(snapshot) {
    setFinancialPage(0);
    setResource((previous) => {
      const nextErrors = { ...previous.errors };
      delete nextErrors.snapshots;

      return {
        ...previous,
        snapshots: [
          ...previous.snapshots.filter(
            (item) =>
              String(item.reportingPeriod).slice(0, 7) !==
              String(snapshot.reportingPeriod).slice(0, 7)
          ),
          snapshot,
        ],
        errors: nextErrors,
      };
    });
  }

  const sortedSnapshots = [...snapshots].sort((a, b) =>
    String(b.reportingPeriod).localeCompare(String(a.reportingPeriod))
  );

  const sortedAssessments = [...assessments].sort((a, b) =>
    String(b.assessmentDate).localeCompare(String(a.assessmentDate))
  );

  const latest = sortedSnapshots[0];
  const financialPageCount = Math.max(1, Math.ceil(sortedSnapshots.length / 5));
  const activeFinancialPage = Math.min(financialPage, financialPageCount - 1);
  const visibleSnapshots = sortedSnapshots.slice(activeFinancialPage * 5, activeFinancialPage * 5 + 5);
  const assessment = sortedAssessments[0];

  const openWarnings = warnings.filter(
    (warning) =>
      !["CLOSED", "CLOSED_REVIEWED"].includes(warning.warningStatus)
  );

  function assessmentSaved(saved) {
    setResource(previous => {
      const nextErrors = { ...previous.errors };
      delete nextErrors.assessments;
      return { ...previous, errors: nextErrors, assessments: [...previous.assessments.filter(item => item.assessmentId !== saved.assessmentId), saved] };
    });
  }

  function warningSaved(saved) {
    setResource(previous => {
      const nextErrors = { ...previous.errors };
      delete nextErrors.warnings;
      return { ...previous, errors: nextErrors, warnings: [saved, ...previous.warnings.filter(item => item.warningId !== saved.warningId)] };
    });
  }

  if (tab === "assessments" && user.role !== "MSME_REP") {
    return <AssessmentWorkflow business={business} user={user} snapshots={snapshots} onAssessmentSaved={assessmentSaved} onWarningSaved={warningSaved} />;
  }
  if (tab === "thresholds" && user.role !== "MSME_REP") {
    return <ThresholdManagement business={business} user={user} />;
  }
  if (tab === "warnings") {
    return <WarningWorkflow business={business} user={user} onWarningSaved={warningSaved} />;
  }

  return (
    <>
      {tab === "financials" && user.role === "MSME_REP" && (
        <FinancialSnapshotForm
          business={business}
          token={user.accessToken}
          existingSnapshots={snapshots}
          onSaved={handleSnapshotSaved}
        />
      )}

      {Object.keys(errors).length > 0 && (
        <div className="notice error" role="alert">
          {Object.entries(errors).map(([name, message]) => (
            <p key={name}>
              <strong>{readable(name)}:</strong> {message}
            </p>
          ))}
        </div>
      )}

      {tab === "overview" && (
        <>
          {user.role === "MSME_REP" && (
            <div className="stats-grid">
              <StatCard
                icon={<Wallet size={20} />}
                label="Latest revenue"
                value={compact(latest?.revenue)}
                note={
                  latest
                    ? formatDate(latest.reportingPeriod)
                    : "Awaiting financial data"
                }
              />

              <StatCard
                icon={<Activity size={20} />}
                label="Credit used"
                value={compact(latest?.creditUsed)}
                note={`Sanctioned limit: ${compact(
                  business.sanctionedCreditLimit
                )}`}
                tone="blue"
              />

              <StatCard
                icon={<CircleAlert size={20} />}
                label="Overdue receivables"
                value={compact(latest?.overdueReceivables)}
                note="From the latest submitted snapshot"
                tone="amber"
              />

              <StatCard
                icon={<Wallet size={20} />}
                label="Total receivables"
                value={compact(latest?.totalReceivables)}
                note="From the latest submitted snapshot"
                tone="violet"
              />
            </div>
          )}

          <div
            className="dashboard-columns"
            style={
              user.role === "MSME_REP"
                ? { gridTemplateColumns: "1fr" }
                : undefined
            }
          >
            <section className="panel">
              <div className="panel-heading">
                <div>
                  <h2>Revenue performance</h2>
                  <p>
                    Recent financial snapshots for this business
                  </p>
                </div>

                <span className="subtle-label">
                  UP TO 8 PERIODS
                </span>
              </div>

              {errors.snapshots ? (
                <Empty title="Financial data unavailable">
                  {errors.snapshots}
                </Empty>
              ) : (
                <RevenueChart snapshots={snapshots} />
              )}
            </section>

            {user.role !== "MSME_REP" && (
              <section className="panel">
              <div className="panel-heading">
                <div>
                  <h2>Risk overview</h2>
                  <p>Latest saved business assessment</p>
                </div>
                <ShieldCheck size={21} className="teal" />
              </div>

              <div className="risk-summary">
                <div className="risk-orbit">
                  <ShieldCheck size={38} />
                </div>

                <h3>
                  {errors.assessments
                    ? "Assessment unavailable"
                    : assessment
                      ? readable(assessment.severity)
                      : "Awaiting assessment"}
                </h3>

                <p>
                  {errors.assessments
                    ? "Financial data remains available independently."
                    : assessment
                      ? `Stress pattern: ${readable(
                          assessment.stressPattern
                        )}`
                      : "Saved assessment results will appear here."}
                </p>
              </div>

              <div className="risk-footer">
                <span>Unclosed warnings loaded</span>
                <strong>
                  {errors.warnings ? "—" : openWarnings.length}
                </strong>
              </div>
              </section>
            )}
          </div>

          <section className="panel">
            <div className="panel-heading">
              <div>
                <h2>Signals to follow up</h2>
                <p>
                  Recent warnings for {business.businessName}
                </p>
              </div>

              {!errors.warnings && (
                <span className="badge neutral">
                  {warnings.length} loaded
                </span>
              )}
            </div>

            <WarningList
              warnings={warnings.slice(0, 3)}
              error={errors.warnings}
            />
          </section>
        </>
      )}

      {tab === "financials" && (
        <section className="panel">
          <div className="panel-heading">
            <div>
              <h2>Financial history</h2>
              <p>
                Latest records first · 5 per page · Original database units
              </p>
            </div>

            {!errors.snapshots && (
              <span className="badge neutral">
                {snapshots.length} snapshots
              </span>
            )}
          </div>

          {errors.snapshots ? (
            <Empty title="Financial data unavailable">
              {errors.snapshots}
            </Empty>
          ) : !snapshots.length ? (
            <Empty title="No financial snapshots to display">
              Submitted financial records will appear here.
            </Empty>
          ) : (
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>Reporting period</th>
                    <th>Revenue</th>
                    <th>Receivables</th>
                    <th>Overdue</th>
                    <th>Credit used</th>
                  </tr>
                </thead>

                <tbody>
                  {visibleSnapshots.map((snapshot) => (
                    <tr key={snapshot.snapshotId}>
                      <td>
                        {formatDate(snapshot.reportingPeriod)}
                      </td>
                      <td className="strong">
                        {number(snapshot.revenue)}
                      </td>
                      <td>
                        {number(snapshot.totalReceivables)}
                      </td>
                      <td>
                        {number(snapshot.overdueReceivables)}
                      </td>
                      <td>{number(snapshot.creditUsed)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          {!errors.snapshots && snapshots.length > 0 && (
            <WorkflowPagination
              page={activeFinancialPage}
              onPage={setFinancialPage}
              loading={false}
              count={snapshots.length}
              hasNext={activeFinancialPage + 1 < financialPageCount}
            />
          )}
        </section>
      )}

    </>
  );
}

export default function Dashboard() {
  const { user, logout } = useAuth();
  const config = ROLE_CONFIG[user.role];

  const [tab, setTab] = useState("overview");
  const [businesses, setBusinesses] = useState([]);
  const [selectedId, setSelectedId] = useState("");
  const [search, setSearch] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [revision, setRevision] = useState(0);
  const [mobileMenu, setMobileMenu] = useState(false);
  const [businessEditor, setBusinessEditor] = useState(null);
  const [deactivation, setDeactivation] = useState(null);
  const [deactivating, setDeactivating] = useState(false);
  const [managementError, setManagementError] = useState("");
  const [managementMessage, setManagementMessage] = useState("");
  const deactivationInFlight = useRef(false);

  useEffect(() => {
    const controller = new AbortController();

    if (!config) return;

    apiRequest(config.endpoint, {
      token: user.accessToken,
      signal: controller.signal,
    })
      .then((data) => {
        if (controller.signal.aborted) return;

        const rows = Array.isArray(data)
          ? data
          : data
            ? [data]
            : [];

        setBusinesses(rows);

        setSelectedId((previous) =>
          rows.some(
            (row) => String(row.msmeId) === String(previous)
          )
            ? previous
            : String(rows[0]?.msmeId || "")
        );

        setError("");
        setLoading(false);
      })
      .catch((error) => {
        if (controller.signal.aborted) return;

        // The primary MSME service validates the login session.
        // Secondary service failures are handled separately above.
        if (error.status === 401) {
          logout();
        } else {
          setError(error.message);
          setLoading(false);
        }
      });

    return () => controller.abort();
  }, [user, config, logout, revision]);

  if (!config) {
    return (
      <div className="empty-state">
        <h3>Unsupported account role</h3>
        <p>Contact your administrator to check your account.</p>
        <button
          className="button secondary"
          onClick={logout}
        >
          Sign out
        </button>
      </div>
    );
  }

  const selected = businesses.find(
    (business) => String(business.msmeId) === selectedId
  );

  const filtered = businesses.filter((business) =>
    [
      business.businessName,
      business.sector,
      business.contactName,
    ]
      .join(" ")
      .toLowerCase()
      .includes(search.toLowerCase())
  );

  const totalCredit = businesses.reduce(
    (sum, business) =>
      sum + (Number(business.sanctionedCreditLimit) || 0),
    0
  );

  const activeCount = businesses.filter(
    (business) => business.isActive === "Y"
  ).length;

  const rmCount = new Set(
    businesses
      .map((business) => business.assignedRmId)
      .filter((id) => id != null)
  ).size;

  const sectorCount = new Set(
    businesses.map((business) => business.sector).filter(Boolean)
  ).size;

  const navItems = [
    {
      id: "overview",
      label: "Overview",
      icon: LayoutDashboard,
    },
    {
      id: "portfolio",
      label: config.portfolio,
      icon: Building2,
    },
    {
      id: "financials",
      label: "Financial history",
      icon: Wallet,
    },
    {
      id: "analytics",
      label: "Financial analytics",
      icon: BarChart3,
    },
    {
      id: "recommendations",
      label: "Recommendations",
      icon: Sparkles,
    },
    {
      id: "assessments",
      label: "Assessments",
      icon: Activity,
    },
    {
      id: "warnings",
      label: "Warnings",
      icon: CircleAlert,
    },
    {
      id: "thresholds",
      label: "Thresholds",
      icon: ShieldCheck,
    },
    {
      id: "profile",
      label: "My profile",
      icon: UserRound,
    },
  ].filter(
  (item) =>
    !["assessments", "thresholds"].includes(item.id) || user.role !== "MSME_REP"
);

  const currentLabel = navItems.find(
    (item) => item.id === tab
  )?.label;

  function navigate(nextTab) {
    setTab(nextTab);
    setMobileMenu(false);
  }

  function refresh() {
    setLoading(true);
    setError("");
    setRevision((previous) => previous + 1);
  }

  function businessSaved(savedBusiness) {
    setBusinesses(previous => previous.some(b => b.msmeId === savedBusiness.msmeId)
      ? previous.map(b => b.msmeId === savedBusiness.msmeId ? savedBusiness : b)
      : [...previous, savedBusiness]);
    setBusinessEditor(null);
    setManagementError("");
    setManagementMessage(`${savedBusiness.businessName} saved successfully.`);
    if (!selectedId) setSelectedId(String(savedBusiness.msmeId));
  }

  async function confirmDeactivation() {
    if (!deactivation || deactivationInFlight.current) return;
    deactivationInFlight.current = true;
    setDeactivating(true);
    setManagementError("");
    try {
      const updated = await deactivateBusiness(user.accessToken, deactivation.msmeId);
      businessSaved(updated);
      setManagementMessage(`${updated.businessName} and its associated representative account have been deactivated.`);
      setDeactivation(null);
    } catch (err) {
      setManagementError(err.status === 401 ? "Your session has expired. Sign out and log in again." : err.message);
    } finally {
      deactivationInFlight.current = false;
      setDeactivating(false);
    }
  }

  return (
    <div className="app-shell">
      {mobileMenu && (
        <button
          className="sidebar-overlay"
          aria-label="Close navigation"
          onClick={() => setMobileMenu(false)}
        />
      )}

      <aside
        className={`sidebar ${mobileMenu ? "is-open" : ""}`}
      >
        <div className="brand">
          <span className="brand-icon">
            <TrendingUp size={24} />
          </span>

          <div>
            MSME<span className="brand-light">pulse</span>
            <small>BUSINESS HEALTH PLATFORM</small>
          </div>
        </div>

        <div className="workspace-label">
          <span className="status-dot" />
          {config.label}
        </div>

        <p className="nav-caption">WORKSPACE</p>

        <nav aria-label="Main navigation">
          {navItems.map(({ id, label, icon: NavIcon }) => (
            <button
              key={id}
              className={`nav-item ${
                tab === id ? "active" : ""
              }`}
              onClick={() => navigate(id)}
              aria-current={tab === id ? "page" : undefined}
            >
              <NavIcon size={19} />
              {label}

              {tab === id && (
                <span className="nav-active-dot" />
              )}
            </button>
          ))}
        </nav>

        <div className="sidebar-insight">
          <ShieldCheck size={25} />
          <h3>Stay a step ahead.</h3>
          <p>
            Review financial signals regularly to support
            timely action.
          </p>
        </div>

        <div className="sidebar-account">
          <div className="avatar">
            {user.name?.charAt(0) || "U"}
          </div>

          <div>
            <strong>{user.name}</strong>
            <small>{config.label}</small>
          </div>

          <button
            className="icon-button"
            onClick={logout}
            aria-label="Sign out"
          >
            <LogOut size={18} />
          </button>
        </div>
      </aside>

      <div className="main-shell">
        <header className="topbar">
          <div className="topbar-left">
            <button
              className="icon-button mobile-menu-button"
              aria-label="Toggle navigation"
              aria-expanded={mobileMenu}
              onClick={() => setMobileMenu(!mobileMenu)}
            >
              {mobileMenu ? (
                <X size={22} />
              ) : (
                <Menu size={22} />
              )}
            </button>

            <span className="breadcrumb">Workspace</span>
            <span className="breadcrumb-slash">/</span>
            <strong>{currentLabel}</strong>
          </div>

          <div className="topbar-right">
            <span className="today">
              {new Date().toLocaleDateString("en-IN", {
                day: "numeric",
                month: "short",
                year: "numeric",
              })}
            </span>

            <span className="avatar avatar-small">
              {user.name?.charAt(0) || "U"}
            </span>
          </div>
        </header>

        <main className="dashboard-main">
          <div className="page-heading">
            <div>
              <p className="eyebrow teal">
                {tab === "overview"
                  ? `WELCOME BACK, ${
                      user.name?.split(" ")[0]?.toUpperCase() ||
                      "THERE"
                    }`
                  : config.label.toUpperCase()}
              </p>

              <h1>
                {tab === "overview"
                  ? config.title
                  : currentLabel}
              </h1>

              <p className="muted">{config.description}</p>
            </div>

            <button
              className="button secondary"
              onClick={refresh}
              disabled={loading}
              aria-label="Refresh dashboard data"
            >
              <RefreshCw
                size={16}
                className={loading ? "spin" : ""}
              />
              Refresh data
            </button>
          </div>

          {error && (
            <div className="notice error" role="alert">
              {error}
            </div>
          )}

          {loading ? (
            <div className="loading-state" role="status">
              <RefreshCw className="spin" size={23} />
              Loading your workspace…
            </div>
          ) : tab === "profile" ? (
            <section className="panel profile-panel">
              <div className="profile-heading">
                <div className="avatar avatar-large">
                  {user.name?.charAt(0) || "U"}
                </div>

                <div>
                  <h2>{user.name}</h2>
                  <p className="muted">{config.label}</p>
                </div>
              </div>

              <div className="profile-grid">
                <div>
                  <small>Email address</small>
                  <strong>{user.email}</strong>
                </div>

                <div>
                  <small>User ID</small>
                  <strong>#{user.userId}</strong>
                </div>

                <div>
                  <small>Account role</small>
                  <strong>{config.label}</strong>
                </div>

                <div>
                  <small>Business ID</small>
                  <strong>
                    {user.msmeId ||
                      "Not linked to one business"}
                  </strong>
                </div>
              </div>

              <button
                className="button secondary"
                onClick={logout}
              >
                <LogOut size={17} />
                Sign out
              </button>
            </section>
          ) : (
            <>
              {tab === "portfolio" && user.role === "RM" && (
                <>
                  {managementMessage && <div className="notice snapshot-success" role="status">{managementMessage}</div>}
                  {managementError && <div className="notice error" role="alert">{managementError}</div>}
                  {businessEditor && <BusinessForm
                    key={businessEditor.business?.msmeId || "new"}
                    token={user.accessToken}
                    business={businessEditor.business}
                    onSaved={businessSaved}
                    onCancel={() => setBusinessEditor(null)}
                  />}
                  {deactivation && <section className="panel management-confirm" aria-labelledby="deactivate-title">
                    <h2 id="deactivate-title">Deactivate {deactivation.businessName}?</h2>
                    <p>This makes the business inactive and disables its associated representative account. Existing records are kept.</p>
                    <div className="snapshot-actions">
                      <button className="button secondary" disabled={deactivating} onClick={() => setDeactivation(null)}>Cancel</button>
                      <button className="button danger-button" disabled={deactivating} onClick={confirmDeactivation}>{deactivating ? "Deactivating…" : "Confirm deactivation"}</button>
                    </div>
                  </section>}
                </>
              )}
              {tab === "overview" &&
                user.role !== "MSME_REP" && (
                  <div className="stats-grid">
                    <StatCard
                      icon={<Building2 size={20} />}
                      label={
                        user.role === "RM"
                          ? "Assigned businesses"
                          : "Total businesses"
                      }
                      value={businesses.length}
                      note="Within your permitted portfolio"
                    />

                    <StatCard
                      icon={<Wallet size={20} />}
                      label="Sanctioned credit"
                      value={compact(totalCredit)}
                      note="Total limit · database units"
                      tone="blue"
                    />

                    <StatCard
                      icon={<ShieldCheck size={20} />}
                      label="Active businesses"
                      value={activeCount}
                      note={`${
                        businesses.length - activeCount
                      } inactive`}
                      tone="amber"
                    />

                    <StatCard
                      icon={<Users size={20} />}
                      label={
                        user.role === "RISK_ADMIN"
                          ? "Assigned RMs"
                          : "Sectors covered"
                      }
                      value={
                        user.role === "RISK_ADMIN"
                          ? rmCount
                          : sectorCount
                      }
                      note={
                        user.role === "RISK_ADMIN"
                          ? "Distinct relationship managers"
                          : "Across your assigned businesses"
                      }
                      tone="violet"
                    />
                  </div>
                )}

              {(tab === "portfolio" ||
                (tab === "overview" && user.role !== "RM")) &&
                user.role !== "MSME_REP" && (
                  <section className="panel">
                    <div className="panel-heading">
                      <div>
                        <h2>{config.portfolio}</h2>
                        <p>
                          Select a business to explore its
                          financial health
                        </p>
                      </div>

                      <div className="management-toolbar">
                      {user.role === "RM" && tab === "portfolio" && (
                        <button className="button primary" disabled={!!businessEditor || !!deactivation} onClick={() => {
                          setManagementMessage(""); setManagementError(""); setBusinessEditor({});
                        }}><Plus size={15} />Add business</button>
                      )}
                      <div className="search-box">
                        <Search size={17} />

                        <input
                          aria-label="Search businesses"
                          placeholder="Search businesses…"
                          value={search}
                          onChange={(event) =>
                            setSearch(event.target.value)
                          }
                        />
                      </div>
                      </div>
                    </div>

                    <div className="table-scroll">
                      <table>
                        <thead>
                          <tr>
                            <th>Business</th>
                            <th>Sector</th>
                            <th>Credit limit</th>
                            <th>Status</th>
                            <th>
                              <span className="sr-only">
                                Open business
                              </span>
                            </th>
                          </tr>
                        </thead>

                        <tbody>
                          {filtered.map((business) => (
                            <tr
                              key={business.msmeId}
                              className={
                                selectedId ===
                                String(business.msmeId)
                                  ? "selected-row"
                                  : ""
                              }
                            >
                              <td>
                                <div className="business-cell">
                                  <span className="business-avatar">
                                    {business.businessName?.charAt(
                                      0
                                    ) || "B"}
                                  </span>

                                  <div>
                                    <strong>
                                      {business.businessName}
                                    </strong>
                                    <small>
                                      {business.contactName} · #
                                      {business.msmeId}
                                    </small>
                                  </div>
                                </div>
                              </td>

                              <td>{business.sector}</td>

                              <td className="strong">
                                {number(
                                  business.sanctionedCreditLimit
                                )}
                              </td>

                              <td>
                                <Badge
                                  value={
                                    business.isActive === "Y"
                                      ? "ACTIVE"
                                      : "INACTIVE"
                                  }
                                />
                              </td>

                              <td>
                                <div className="management-row-actions">
                                <button
                                  className="table-open"
                                  aria-label={`View ${business.businessName}`}
                                  onClick={() => {
                                    setSelectedId(
                                      String(business.msmeId)
                                    );
                                    setTab("overview");
                                  }}
                                >
                                  View
                                  <ArrowRight size={15} />
                                </button>
                                {user.role === "RM" && tab === "portfolio" && business.isActive === "Y" && <>
                                  <button className="table-open" disabled={!!businessEditor || !!deactivation} aria-label={`Edit ${business.businessName}`} onClick={() => {
                                    setManagementMessage(""); setManagementError(""); setBusinessEditor({ business });
                                  }}><Pencil size={14} />Edit</button>
                                  <button className="table-open danger-text" disabled={!!businessEditor || !!deactivation} aria-label={`Deactivate ${business.businessName}`} onClick={() => {
                                    setManagementMessage(""); setManagementError(""); setDeactivation(business);
                                  }}>Deactivate</button>
                                </>}
                                </div>
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>

                    {!filtered.length && (
                      <Empty title="No matching businesses">
                        Try another search or check your
                        business assignments.
                      </Empty>
                    )}
                  </section>
                )}

              {tab === "portfolio" && user.role === "RM" && (
                <RepresentativeManagement token={user.accessToken} businesses={businesses} />
              )}

              {selected && tab !== "portfolio" && (
                <>
                  <div className="business-context">
                    <div>
                      <span className="context-icon">
                        <Building2 size={21} />
                      </span>

                      <div>
                        <small>BUSINESS INSIGHTS</small>
                        <strong>
                          {selected.businessName}
                        </strong>
                      </div>
                    </div>

                    {user.role !== "MSME_REP" ? (
                      <div className="business-switcher">
                      <label htmlFor="selected-business">Select business</label>
                      <div className="business-select">
                        <select
                          id="selected-business"
                          aria-label="Select business"
                          value={selectedId}
                          onChange={(event) =>
                            setSelectedId(event.target.value)
                          }
                        >
                          {businesses.map((business) => (
                            <option
                              key={business.msmeId}
                              value={business.msmeId}
                            >
                              {business.businessName}
                            </option>
                          ))}
                        </select>

                        <ChevronDown size={20} />
                      </div>
                      </div>
                    ) : (
                      <Badge
                        value={
                          selected.isActive === "Y"
                            ? "ACTIVE"
                            : "INACTIVE"
                        }
                      />
                    )}
                  </div>

                  {tab === "analytics" ? <FinancialAnalytics
                    key={`${selectedId}:${revision}:analytics`}
                    business={selected}
                    user={user}
                  /> : tab === "recommendations" ? <RecommendationWorkspace
                    key={`${selectedId}:${revision}:recommendations`}
                    business={selected}
                    user={user}
                  /> : <BusinessWorkspace
                    key={`${selectedId}:${revision}`}
                    business={selected}
                    user={user}
                    tab={tab}
                  />}
                </>
              )}

              {selected &&
                tab === "portfolio" &&
                user.role === "MSME_REP" && (
                  <section className="panel profile-panel">
                    <h2>{selected.businessName}</h2>

                    <p className="muted">
                      Your registered business profile
                    </p>

                    <div className="profile-grid">
                      <div>
                        <small>Sector</small>
                        <strong>{selected.sector}</strong>
                      </div>

                      <div>
                        <small>Contact person</small>
                        <strong>
                          {selected.contactName}
                        </strong>
                      </div>

                      <div>
                        <small>Sanctioned credit limit</small>
                        <strong>
                          {number(
                            selected.sanctionedCreditLimit
                          )}
                        </strong>
                      </div>

                      <div>
                        <small>Assigned RM ID</small>
                        <strong>
                          {selected.assignedRmId || "—"}
                        </strong>
                      </div>

                      <div>
                        <small>Consent status</small>
                        <strong>
                          {readable(selected.consentStatus)}
                        </strong>
                      </div>

                      <div>
                        <small>Business status</small>
                        <Badge
                          value={
                            selected.isActive === "Y"
                              ? "ACTIVE"
                              : "INACTIVE"
                          }
                        />
                      </div>
                    </div>
                  </section>
                )}

              {!businesses.length && !error && (
                <Empty title="No businesses linked yet">
                  Contact your administrator to check your
                  business assignment.
                </Empty>
              )}
            </>
          )}

          <footer className="dashboard-footer">
            <span>
              MSMEpulse · Early Warning & Intervention
            </span>

            <span>
              <ShieldCheck size={13} />
              Access based on your account role
            </span>
          </footer>
        </main>
      </div>
    </div>
  );
}
