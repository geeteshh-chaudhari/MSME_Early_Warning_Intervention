# MSME recommendation agent

Python command-line agent for Oracle Database 26ai and local Ollama `qwen3:1.7b`.
Python runs all five allowlisted tools; the model interprets their results in an
advisory interpretation with evidence IDs. Python performs every calculation.
The CLI reads Oracle. The authenticated API also saves generated JSON in
`WARNING.RECOMMENDED_ACTION` before returning success.

## Authenticated REST API and Eureka

The CLI remains available. The API runs on **8085**, registers as
`recommendation-service` with Eureka on **8080**. Call the API directly on port
**8085**, or use the existing gateway /api/recommendations route from the frontend.

Install `requirements.txt`, then add the entries in `api.env.example` to your
existing service `.env`. Preserve your Oracle credentials. `JWT_SECRET` must be
the **same Base64 value** as `app.jwt.secret` in the login service; `JWT_ISSUER`
must match its issuer. A different signing secret will reject login tokens.
No secret is embedded in the Python code or copied automatically.

The Oracle account also needs SELECT access to `APP_USER` and `MSME` for current
account status and assignments; password hashes are never selected. Set
`ORACLE_USER_SCHEMA` if APP_USER belongs to another owner. Existing profile and
assessment schema settings still apply.

For generation, the Oracle account needs SELECT access to `WARNING` and UPDATE
access to its `RECOMMENDED_ACTION` CLOB column. Set `ORACLE_WARNING_SCHEMA` when
the warning table belongs to another owner; blank uses current-schema tables or
private synonyms. No new column or table is required. The existing column is
named `recommended_action`, not `recommendation_action`.

Each successful generation saves the complete response JSON (recommendation,
evidence and metadata) for the exact requested warning_id, MSME and reporting
month. The frontend selects an open warning explicitly. Each request reads that
warning first: any nonempty saved value is displayed without calling Ollama or
overwriting the column, including legacy placeholder or plain-text records.
Only empty fields can generate. Mismatches return 404; closed warnings return
409. Failed generation leaves the column empty for explicit retry. Concurrent
requests in the configured single worker wait and recheck the saved value.
The API does not create warnings or change their decisions.
Responses identify `recommendation_source` as `saved` or `generated` and include `warning_id`.
Evidence-only and insufficient-data responses do not overwrite recommendations.
Database failures return 503 instead of reporting a successful save.

Start RegistryServer and Ollama, then the API:

```powershell
# From Recommendation-service:
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe -m recommendation.serve
# Alternatively: .\start-api.ps1
```

Use the bearer token returned by the existing `POST http://localhost:8082/api/auth/login`.
In Postman select Authorization > Bearer Token and enter the token without a
second `Bearer` prefix. Send JSON to:

```text
POST http://localhost:8085/api/recommendations
Authorization: Bearer <login-token>
Content-Type: application/json
```

```json
{
  "msme_id": 1,
  "as_of": "2026-09",
  "metrics_only": false,
  "question": "Review revenue trends, seasonal context and collection actions."
}
```

Only `msme_id` and `as_of` are required. `as_of` is a completed reporting month
in `YYYY-MM` format, matching the CLI; it is not a snapshot ID or submission
timestamp. The requested month must contain a financial snapshot. Historical
metrics still read the existing history window ending at that month. Use
`metrics_only: true` to test Oracle and metrics without waiting for Ollama.
The response uses the CLI's recommendation/evidence structure and metadata.

Access follows the existing profile-service policy:

| Role | Allowed MSMEs |
| --- | --- |
| RISK_ADMIN | All active MSMEs |
| RM | Active MSMEs whose ASSIGNED_RM_ID matches the authenticated user |
| MSME_REP | The active MSME linked to the user's APP_USER.MSME_ID |

Signature, issuer, expiry, required claims and current account status/role are
checked on every request. Changed assignments take effect on the next request.
The direct `http://localhost:8085/api/recommendations` endpoint requires
authentication and assignment checks. Allow a suitable client timeout for local
model planning and generation.
Only one analysis runs at a time per process; concurrent calls receive 429.

`GET http://localhost:8085/health` is a public process-liveness endpoint only; it
does not assert Oracle or Ollama readiness. Swagger/OpenAPI pages are disabled.
Eureka heartbeats and deregistration follow the API process lifetime. Use the
provided launcher with one worker so the registered port matches the listener.
Default addresses assume all services run on the same machine.

Responses: 401 missing/invalid/expired token or inactive/changed-role user;
403 disallowed role, assignment or inactive MSME; 404 missing accessible MSME or
requested snapshot; 422 invalid request; 429 busy; 502 model/metric validation
failure; 409 no matching open warning; 503 database/dependency or save failure.
Financial snapshots are not changed.

For isolated tests without live Oracle/Ollama:

```powershell
.\.venv\Scripts\python.exe -m pip install -r requirements-dev.txt
.\.venv\Scripts\python.exe -B -m unittest discover -s tests -v
```

## CLI setup on Windows

Use Python 3.11 or newer. From this directory in PowerShell:

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
Copy-Item .env.example .env
```

Fill the placeholders in `.env`. This module explicitly loads its own `.env`,
regardless of shell working directory; existing environment variables take priority.
`ORACLE_DSN` uses `hostname:port/service_name`, for example
`localhost:1521/FREEPDB1` if that is your actual database service. The default
python-oracledb Thin connection needs no Oracle Instant Client for this setup.

For CLI reads, use an account with SELECT access to:

- Profile owner: `MSME`, `FINANCIAL_SNAPSHOT`.
- Assessment owner: `RISK_ASSESSMENT`, `THRESHOLD_CONFIG`, `ASSESSMENT_INDICATOR_RESULT`.

Set the two schema owner variables if these tables have different owners.
Leave them blank to use current-schema tables or existing private synonyms.
No access to user password hashes is needed. Every query binds the MSME ID and
period bounds. Reads run in one consistent read-only transaction. No DDL is run.

Assumption: your local model is served by Ollama on `http://localhost:11434`.
If Ollama is not running, start `ollama serve` in another terminal. Check the tag:

```powershell
ollama list
# Only if the model is not installed:
ollama pull qwen3:1.7b
```

## Run

```powershell
# Check data access and all metrics first; no LLM request:
.\.venv\Scripts\python.exe -m recommendation --msme-id 1 --as-of 2026-09 --metrics-only

# Calculate all tools in Python and generate a recommendation:
.\.venv\Scripts\python.exe -m recommendation --msme-id 1 --as-of 2026-09

# Optionally save to a new JSON file:
.\.venv\Scripts\python.exe -m recommendation --msme-id 1 --as-of 2026-09 --output reports/msme-1-sep.json
```

Without `--as-of`, the previous calendar month is selected. Current and future
months are rejected because their totals may be incomplete. Fetches cover at most
48 months through that reporting period. Missing requested-month data is not silently
replaced with an older snapshot. The output includes exact metric values, periods,
tool execution trace, formula version, availability and model-generated advice.
Existing output files are never overwritten. No automatic report persistence occurs.

## Metric tools

| Tool | Outputs |
| --- | --- |
| `business_context` | MSME metadata, current reporting-month assessment and its threshold values, data limitations |
| `revenue_metrics` | MoM, monthly YoY, complete-quarter QoQ and YoY, rolling three-month growth, trailing 12-month growth |
| `liquidity_metrics` | Overdue ratio, credit utilisation/headroom, monthly percentage-point changes, receivables YoY growth and its gap from revenue YoY growth |
| `seasonality_metrics` | Deviation from average revenue for the same calendar month in two or three prior years; raw 12-month coefficient of variation |
| `breach_persistence` | Consecutive breach months capped at 12, breach frequency with observed-month coverage, based on stored indicator flags |

Revenue growth is `(current / comparison - 1) * 100`; negative means decline.
Quarter revenue is the sum of three complete calendar months. A mid-quarter as-of
month uses the preceding complete quarter. Rolling three-month growth compares
non-overlapping three-month windows; trailing-year growth requires 24 consecutive
months. Snapshots store receivables and credit as balances, so these are not summed
across months. Zero denominators, missing months and insufficient history yield
`value: null`, `status: unavailable` and a reason. Duplicate months, negative financial
inputs and inconsistent overdue amounts fail explicitly.

Historical credit-utilisation changes use today's master credit limit because the
DDL has no credit-limit history. These calculations must be interpreted accordingly.
Seasonal deviation is exploratory and not detrended: matching-month comparisons do
not prove seasonality or identify causes. Raw volatility includes normal seasonality.
Saved indicator flags retain their own original thresholds; new metrics are not
assigned invented thresholds or combined into an unapproved credit risk score.

Assessment context is shown only for an assessment of the requested month created
before that month's end. Historical late-created assessments are excluded. If no
such assessment exists, snapshot metrics still work, but no assessment thresholds
or severity are assumed. Backdated calculations cannot reconstruct later edits to
snapshots or limits; the DDL does not provide full historical revision data.

## Agent and RISCOR

`prompts/riscor.md` uses **Role, Instructions, Steps, Context, Output, Restrictions**.
The model acts as an advisor supporting an RM, not an autonomous credit decision maker.
All five allowlisted tools execute once in Python in a fixed order; there are no
LLM tool-selection calls. The model receives compact metric evidence and writes
the recommendation in one normal generation call. Application code never evaluates
model code or generated SQL. The model input omits already-computed formulas and
null metadata, preserves interpretation context, and avoids repeating the output
schema and citation list already supplied through Ollama structured output.
Ollama receives metric summaries, not database credentials or raw snapshot histories.
The final JSON is validated, and every proposed action must cite an available metric.
Numeric prose is permitted when grounded in available metrics. Unicode digit forms
are normalized to ordinary digits in generated text. Action numbers must match
their own cited values; the summary may use any available metric value. Decimal
rounding is supported. Positive decline magnitudes may match negative growth
values only with explicit decline wording. Units are checked, including percent
versus percentage points and saved month counts. Invalid citations, unsupported
values, dates/fractions, and invented numeric review deadlines are rejected with
field-specific feedback. The same restriction against
newly invented low/moderate/high/severe stress labels are rejected.
Data-availability limitations are assembled by Python, not generated by the model.
One repair attempt is allowed for final output schema errors. Invalid output,
unknown references, malformed responses and service errors fail explicitly.
No metrics means no recommended actions. Prompt restrictions and evidence references
reduce hallucinations but cannot verify all claims in generated prose: review advice.

The CLI and Streamlit interface have no public API or JWT integration. Run them only
as an authorized local operator. Adding a shared API later requires authenticating
callers and enforcing MSME access.

## Tests

```powershell
python -m unittest discover -s tests -v
```

Tests use synthetic records and mocked Oracle/Ollama boundaries. They do not contact
your database, execute migrations, install dependencies or download a model.

References: [python-oracledb connection handling](https://python-oracledb.readthedocs.io/en/latest/user_guide/connection_handling.html),
[Ollama chat API](https://docs.ollama.com/api/chat),
[Ollama structured outputs](https://docs.ollama.com/capabilities/structured-outputs).


## Speed optimization - 19 October 2026

The response envelope, recommendation/action fields, full evidence (including formulas),
tool trace entries and deterministic limitations retain their existing structure.
All five tools now always appear in evidence and execution trace. Model generation
options remain temperature 0, context 16384, output budget 1800 tokens, thinking off;
the maximum remains six actions. No output-length reduction or truncation was added.
Generated wording and actual character count remain model-dependent, as before.

Tests verify one normal generation call, at most one existing repair call, full
evidence parity, compact-payload metadata preservation and unchanged schema/validator.
On a synthetic full-history fixture, the final user-message JSON shrank from 8253
to 4886 characters (40.8 percent); this is input size, not a measured latency gain.
Live Oracle/Ollama request timing was not benchmarked. Restart the API to load the
new implementation; request JSON and credentials do not change.


## Qualitative output enforcement - 19 October 2026

The schema applies minLength 1 and a digit-free pattern to summary, title,
interpretation and suggested_action. Evidence citation IDs are excluded from the
prose restriction. Python independently checks each field and reports paths such
as actions[0].interpretation plus offending tokens. The existing repair call
receives this feedback and explicit instructions to rewrite prose qualitatively,
preserve supported detail, use relevant citations and avoid unsupported collection
or borrowing conclusions. No automatic numeric deletion, citation substitution or
additional repair calls were added. Semantic correctness is not guaranteed by
the digit constraint; the prompt's interpretation restrictions still apply.

Response fields, full numeric evidence, the six-action limit, 1800-token output
budget, compact model input and one-call fast path are unchanged. All 42 isolated
regression tests pass. The existing temporary LLM-response debug print is preserved.
Restart the API after applying the change.

A live local Qwen synthetic-schema check exceeded its 60-second test timeout;
live model output under the new constraint remains unverified.


## Unicode numeral and citation feedback fix 19 October 2026

A live rejected response used full-width numerals and unqualified revenue metric IDs. The structured-output prose pattern now also excludes full-width and Arabic-Indic decimal digits. Python normalizes text only for inspection and checks Unicode numeric characters, catching full-width, Arabic, circled and Roman numeral forms without silently rewriting the returned text. Numeric and citation failures are aggregated into one repair message. Unqualified IDs receive an exact qualified-ID suggestion only when a matching available suffix is unique; no citation is automatically changed. RISCOR explicitly prohibits alternate numeral glyphs and requires the tool prefix in every citation. Response fields, full evidence, output budget and single-retry limit remain unchanged. All 46 isolated tests passed, including the reported combination of full-width prose and missing prefixes. This does not guarantee semantic correctness or successful repair by the small model; rejected outputs still fail explicitly. No additional live model request was made for this fix.


## Positive prose allowlist 20 October 2026

A later model response used mathematical bold and subscript numerals that the partial schema blacklist did not exclude. The schema now uses a positive allowlist of standard English letters, whitespace and ordinary punctuation for generated prose; Python independently enforces the same pattern and retains Unicode-aware numeric diagnostics. Citation IDs, business names in full evidence, numeric evidence, public JSON fields, the six-action limit, output token budget and retry count are unchanged. RISCOR explains the character policy and keeps the existing evidence/citation/financial-interpretation restrictions. All 50 regression tests passed; one checks every numeric code point recognized by the current Python Unicode database against the schema pattern, and another reproduces the mathematical/subscript example. No additional live model check was run, so live generation under this constraint remains unverified. This character constraint does not guarantee semantic correctness.


## Current numeric-prose policy - 20 October 2026

This supersedes the earlier qualitative-only/character-allowlist experiments.
Numbers are allowed in all four generated prose fields when supported by the
appropriate metric evidence. The schema no longer bans digits or non-English
characters. The new numeric_prose.py helper normalizes compatibility digits
(mathematical, subscript, circled, full-width) and decimal Unicode digits to
ordinary 0-9 in prose, preserving full evidence and exact citation IDs. It does
not silently reinterpret Roman numerals or fractions as financial values.

The validator checks action literals against that action's available cited metric
values, while summary literals may match any available metric. It supports normal
decimal rounding (half-up), comma thousands separators and signed values. A positive
'decline of' magnitude may match a negative growth metric only when explicit local
decline wording preserves direction; it is not a blanket absolute-value match.
Percent, percentage-point and saved-month units are distinguished. Reporting dates,
metadata counts, threshold figures and invented deadlines are not automatically
accepted as metric values. Semantic correctness of the narrative or its citation
selection is not guaranteed by numeric value matching; RM review remains required.

All 59 regression tests pass. Response field structure, full evidence, six-action
limit, 1800-token output budget, compact model input and single-repair limit are
unchanged. Existing debug response printing now shows normalized prose. No live
model generation was run for this update. Restart the API to load the new policy.


### Metric precision (21 October 2026)
Calculated tool metric values are rounded once to two decimal places using Decimal
half-up rounding before they are sent to the LLM and returned as evidence. Raw
snapshot inputs retain their precision; the receivables/revenue growth gap uses
unrounded ratios. RISCOR and repair instructions request exactly two decimal
places in numeric prose. JSON evidence remains numeric (45.0 and 45.00 have the
same numeric value); trailing-zero presentation is requested in generated text.
The existing supported-value, unit, sign and citation validation remains active.


## Current value-only validation policy — 21 October 2026

This supersedes earlier citation-scoped, unit-aware and semantic validation rules.
Generated prose numbers are normalized and compared for exact numeric equality
with any available calculated metric from all tools. 45, 45.0 and 45.00 are equal;
18.4 is not equal to 18.41, and 14.7 is not equal to -14.7. No additional rounding,
unit, deadline, risk-label or citation-correctness checks are applied. JSON shape,
field types and the existing six-action limit remain required for API compatibility.
Citation arrays may be empty; they remain part of the response but do not determine
which metric values are eligible. The structured-output schema no longer restricts
citation strings to an enum. Unicode normalization, all evidence, calculations,
output budget, and one validation-repair attempt are preserved. Numbers in dates,
quarter labels or reference boundaries have no special exemption: if they do not
match tool values, they can still fail the value comparison. Wrong financial
meaning or units can now pass when the numeric value matches a tool value.


## Current sign-independent validation — 21 October 2026

Numeric matching now compares exact absolute magnitudes across available tool
metrics. Signs are ignored for every metric and prose field, without interpreting
growth/decline wording. A tool value of -14.43 matches 14.43, +14.43 or -14.43;
14.4 and 14.44 still fail. Decimal placement remains significant: 87 does not
match 0.87. Original signed tool evidence is preserved. Units/citations and
financial meaning remain outside the numeric check; basic JSON shape checks
remain. The main and repair prompts allow direction to be expressed in words
while requiring exact magnitudes. This supersedes earlier sign-matching policy.


## Leading-zero decimal parsing — 21 October 2026

The numeric parser accepts .87 as the decimal value 0.87, including signed and
Unicode-normalized forms. It does not scale integers: 87 still does not match
0.87. Exact magnitude matching, sign independence and original tool evidence are
unchanged. RISCOR and repair instructions explicitly request retaining the leading
zero in generated decimals. Parsing tolerance does not rewrite the returned prose.


## Reporting years and month/year durations — 21 October 2026

The numeric validator exempts recognizable calendar years 1900–2199, ISO reporting
dates, numeric month/year dates and integer month/year durations from financial
value matching. September 2026, 2026-09 and the last 12 months can be included
without matching numeric financial evidence. Figures with financial suffixes,
such as 2026%, remain checked. Date/window factual accuracy is left to the LLM
and supplied context; this is recognition, not metadata verification. The prompt
and repair instructions now permit these references. Exact financial magnitude
matching, ignored signs, response structure and full tool evidence remain unchanged.

## Shared environment and automatic integration ? 23 October 2026

The repository-root .env is loaded after a service-local .env, without replacing
existing shell variables. It contains the shared Oracle/JWT/schema settings,
OLLAMA_BASE_URL, OLLAMA_MODEL=qwen3:1.7b and INTERNAL_SERVICE_TOKEN. Do not commit it.
Start Ollama separately with `ollama serve` unless it is already running. From
this folder start the existing server with
`..\.venv\Scripts\python.exe -X utf8 -m recommendation.serve`.

MS3 invokes the hidden POST /internal/recommendations endpoint using the shared
X-Internal-Token and exact warning_id/msme_id/as_of. Only insertion of a new warning
schedules one automatic attempt; old warnings are not scanned. A process restart
does not replay interrupted attempts: use the selected warning's button if its
field is still empty. Model prompts, calculation formulas and validation rules
are preserved. Evidence selection now uses snapshot reporting periods even if
assessments were created in the next calendar month. Runtime testing is pending.

On Windows, double-click start-api.cmd to open CMD and start the recommendation API. It uses the service-local .venv or the repository-root .venv and pauses on startup failure. The existing start-api.ps1 is a PowerShell script; use the .cmd file for double-click startup. Start Ollama separately beforehand.

## Latest access and retry rule - 23 October 2026

The latest root README supersedes the earlier empty-field/RM-only policy above.
Assigned RM and Risk Admin may view/generate/retry for a selected open warning;
representatives cannot. New warnings contain either generated JSON or exactly
`reccommendation service not available`. That marker is retryable; successful
saved JSON is never replaced. Legacy null values remain accepted for compatibility.
Existing activity logs supply notifications, with UI state held only in memory.
91 isolated recommendation tests passed; no live Oracle/Ollama call was made.

## Recommendation diagnostics - 23 October 2026

Raw model output is labelled LLM RESPONSE (before validation). The terminal prints LLM VALIDATION FAILED with the attempt and validator feedback, or LLM VALIDATION PASSED. HTTP 502 now distinguishes validation rejection from Ollama generation/connection/timeout/output failures. The frontend preserves this explanation without adding the misleading Eureka availability hint to recommendation 502 errors. The existing unavailable marker, successful JSON persistence, validation policy and one repair attempt are unchanged. 93 isolated tests passed; no Oracle or model request was made.
