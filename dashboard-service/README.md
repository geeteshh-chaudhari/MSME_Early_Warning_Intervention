# MSME financial dashboard

A separate Python API and browser dashboard on **http://127.0.0.1:8086**. It reads
Oracle using the recommendation service's existing repository and calculates all
21 metrics using the same `MetricTools` implementation. No LLM calls, database
writes, new risk scores or gateway routes are introduced. It registers as
`dashboard-service` with Eureka on port 8080.
The recommendation API keeps its existing behaviour.

## Run from CMD

Keep this folder beside `Recommendation-service`. Its existing virtual environment
already contains the API dependencies. Start the dashboard with:

```cmd
cd /d "C:\Users\Geetesh Chaudhari\Desktop\MSME_Early_Warning_Intervention\dashboard-service"
start-dashboard.cmd
```

Open **http://127.0.0.1:8086**. The default configuration reuses the recommendation
service's `.env` (Oracle and JWT settings). Nothing copies or displays credentials.
Optional overrides go in a dashboard-local `.env`; see `.env.example`. Existing
environment variables take priority. Do not change `SERVICE_PORT` to configure this
dashboard; use `DASHBOARD_PORT` instead.

For an independent environment:

```cmd
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe -m dashboard.serve
```

For a different checkout layout, set `RECOMMENDATION_SERVICE_PATH` in the shell
before starting Python. The sibling recommendation code must remain available;
do not duplicate or independently change its financial formulas.

## Use

Start `msme-service` on port 8082 and sign in with your existing account. The
dashboard sends credentials only to its same-origin login API, which forwards to
the configured local login endpoint. Passwords are not stored. You can instead
paste an existing login bearer token. Tokens stay in browser memory; a page reload
requires signing in again. They are not placed in URLs or local/session storage.
Sign out clears the loaded report and aborts the current browser request.

Enter an MSME ID and a completed reporting month, such as `2026-09`. The selected
month must have a snapshot. The data API verifies HS256 signature, issuer, expiry
and required claims using the existing verifier, then checks the current account,
role and assignment in Oracle using the existing access policy:

- RISK_ADMIN: active MSMEs.
- RM: active MSMEs assigned to that user.
- MSME_REP: the active MSME linked to that user.

The Oracle account needs the same profile/assessment read grants as recommendations,
plus `APP_USER` for current access checks. No password hashes are selected. No
financial query runs before authorization. This local dashboard binds to loopback;
it has not been configured for a public deployment.

## Charts and interpretation

- Revenue line chart and signed growth comparisons: MoM, YoY, completed-quarter
  QoQ and YoY, rolling three-month and trailing twelve-month growth.
- Total and overdue receivables lines. Overdue is a subset, so balances are not stacked.
- Credit used with a clearly labelled **current** sanctioned limit reference.
- Overdue ratio and credit utilisation trends on a percentage axis.
- Matching calendar month revenue overlays by year for exploratory seasonal context.
- Saved indicator breach matrix with distinct breached, not breached and unavailable states.
- A searchable-by-group metric library with all 21 values. Select any metric for
  its history chart and exact formula, comparison periods, units and limitations.
- Business context, saved assessment and thresholds are available in the export;
  a saved severity is shown as historical, not as a new score.

Chart windows can show 12, 24 or 48 months; the breach matrix shows the latest 12
months and the seasonal comparison uses the full available window. Points expose
exact values on hover/focus. JSON export contains the whole loaded report without
authentication tokens. Monetary amounts keep their original database units; no
currency is assumed. Missing values are gaps/dashes, not zero. All historical
utilisation uses the current limit because the schema has no historical limits.
Historical tool series use records available at the selected reporting cutoff;
they are not an audit reconstruction of when each prior assessment was recorded.

## HTTP interface

`POST /api/dashboard/login`: `{ "email": "...", "password": "..." }`; forwards
to the existing login service and returns `{ "access_token": "..." }`.

`GET /api/dashboard/msmes/{msme_id}?as_of=YYYY-MM`: requires
`Authorization: Bearer <login-token>`. Returns context, exact metric evidence,
48 monthly rows (including gaps), saved indicators and interpretation notes.
401/403 protect identity and assignment; 404 means no selected-month snapshot;
422 means invalid input or unanalysable data; 503 means a dependency is unavailable.

`GET /health` is public process liveness only. The HTML/CSS/JS shell is public;
financial data requires authorization. JSON and page responses use `no-store`.
The frontend uses only local assets and native SVG, so there are no external
charting CDNs or analytics. It does not depend on the recommendation API process
or Ollama being running.

## Tests

```cmd
.\.venv\Scripts\python.exe -m pip install -r requirements-dev.txt
.\.venv\Scripts\python.exe -B -m unittest discover -s tests -v
```

Tests use isolated financial fixtures and mocked database/login boundaries. They
check parity with recommendation tools, missing/zero/duplicate months, history
cutoffs, HTTP responses and access-denial behaviour. Real Oracle and login smoke
testing requires the locally configured running services.


## Compact desktop layout update 17 October 2026

All eight chart panels now share one four-by-two grid on desktop displays at least 1100px wide and 700px high. Summary cards and report controls remain above the grid. All 21 metrics remain available through the history selector and the All metrics dialog, which includes values, formulas and comparison periods. Data notes open in a separate accessible dialog. Smaller displays retain scrolling for readability. No API, authentication, Oracle query or financial formula changed. JavaScript syntax and HTML element/chart structure checks passed; visual browser verification was not performed.


## Eureka registration

Start RegistryServer on port 8080, then restart this dashboard using start-dashboard.cmd.
The service registers as DASHBOARD-SERVICE, advertises DASHBOARD_PORT (default 8086),
sends periodic heartbeats, and deregisters on graceful shutdown. The home/status URL
is / and the process-liveness URL is /health. Registration does not expose financial
data without the existing bearer token and assignment checks.

Optional settings are listed in eureka.env.example: EUREKA_SERVER_URL defaults to
http://localhost:8080/eureka/ and DASHBOARD_HOST defaults to localhost. DASHBOARD_PORT
controls both the launcher listener and advertised port; recommendation SERVICE_PORT
is not used. The launcher uses one worker. RegistryServer and API gateway files
need no changes. On an abrupt exit, Eureka may retain the instance until its lease
expires. The client retries failed registration through its heartbeat lifecycle.

Verification: all 20 isolated tests passed, including real HTTP registration and
deregistration against a test registry, custom port configuration and cleanup on
startup failure. Live registration with the already-running dashboard requires
restarting it with the updated source.
