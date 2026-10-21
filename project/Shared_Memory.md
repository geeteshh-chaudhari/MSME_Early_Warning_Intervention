
# Shared Agent Memory

Updated: 13 October 2026. Progress reflects the files currently present in this checkout.

## Infrastructure & Initial DDL

Contributor: Atharva (attributed by the team).

Status: Registry server and API gateway scaffolding present; initial Oracle DDL defined.

- `RegistryServer/`: Eureka server, port `8080`, WAR packaging, servlet initializer.
- `api-gateway/`: reactive gateway, port `8081`, Eureka registration/fetching through `http://localhost:8080/eureka/`, Actuator health/info exposure.
- Both Maven projects declare Java 22, Spring Boot `4.1.1`, and Spring Cloud `2025.1.3`; each contains a `contextLoads` test.
- `Profile_Finance_DDL.sql`: 10 tables, 9 explicit foreign keys, and 9 explicit indexes covering all three business modules.

Source inspected; Maven tests, running services, and Oracle execution have not been verified. No gateway business routes are configured.

## MSME & Data Financial Micro-Service
Owner: Atharva
Status: Tables defined; business-service implementation not present in this checkout.

Recent changes:

- `app_user`, `msme`, and `financial_snapshot` definitions present in Atharva's DDL.
- Role/nullability constraints, positive credit limit, monthly snapshot uniqueness, and financial amount validation defined.

Dependencies:

- Shared profile/finance interfaces for assessment and warning/intervention modules remain undefined.


## Assessment Micro-Service
Owner: Neha
Status: Tables defined; business-service implementation not present in this checkout.

Recent changes:

- `threshold_config`, `risk_assessment`, and `assessment_indicator_result` definitions present in Atharva's initial DDL.

Dependencies:

- Profile/finance data and historical snapshots; calculation, severity, threshold-selection, and outgoing API rules remain undefined.

## Warning & Intervention Service
Owner: Geetesh
Status: Tables defined; business-service implementation not present in this checkout.

Recent changes:

- `warning`, `intervention`, `follow_up_review`, and `activity_log` definitions present in Atharva's initial DDL.

Dependencies:

- Assessment results, MSME/user identity, lifecycle rules, and shared APIs remain undefined.


## Cross-Module Decisions

- Atharva contributed the registry, gateway, and initial DDL; module ownership remains Atharva / Neha / Geetesh as recorded above.
- Existing SQL constraints are documented in Contracts.md; no implemented business API contract is present.


## Integration Blockers

- Business services and gateway business routes are absent.
- Several cross-module IDs lack foreign keys; integrity enforcement has not been agreed in the repository.
- Allowed statuses do not define workflow transitions; timestamp defaults do not implement automatic updates.
- Build, live discovery/routing, and Oracle execution remain unverified.


## Next Integration Steps

- Validate infrastructure and Oracle schema.
- Agree shared interfaces and implement the three business services.
- Configure and verify gateway routes after downstream services are available.
- Keep Tasks.md updated with actual implementation and verification evidence.



## Warning and Intervention implementation update 15 October 2026

The new `warning-intervention-service/` module contains the Service 3 implementation. Its `client/UpstreamClient` boundary currently has a reference-schema JDBC adapter; the other two business services are not yet created, so no upstream HTTP routes are asserted. Human identity is mapped by signed JWT issuer/subject to active APP_USER records; roles and assignments are read from trusted records. A scoped producer may create warnings. Representatives have separate restricted read endpoints.

The README records provisional workflow decisions, including atomic RM acceptance with an owned action/due date, one active intervention cycle, human-selected follow-up outcomes, and administrator closure. Proposed schema extensions are delivered as SQL files only. Existing root credentials were not copied or printed. Further tests were stopped at the user's request; final build, Oracle, IdP, gateway and Eureka integration remain unverified.


## Dashboard service update 17 October 2026

Added dashboard-service at user request as a separate web dashboard with a Python API. Start with start-dashboard.cmd and open http://127.0.0.1:8086. Reuses recommendation .env by default; no credentials were copied or displayed. Login tokens stay in browser memory. Revenue, receivables, credit, seasonal overlays, saved breaches and all 21 metric histories are displayed using shared deterministic tools, without Ollama calls. 17 fixture/API tests and JavaScript syntax passed; live Oracle/login and visual browser review are unverified. Existing service and gateway code were left unchanged.


## Compact desktop layout update 17 October 2026

All eight chart panels now share one four-by-two grid on desktop displays at least 1100px wide and 700px high. Summary cards and report controls remain above the grid. All 21 metrics remain available through the history selector and the All metrics dialog, which includes values, formulas and comparison periods. Data notes open in a separate accessible dialog. Smaller displays retain scrolling for readability. No API, authentication, Oracle query or financial formula changed. JavaScript syntax and HTML element/chart structure checks passed; visual browser verification was not performed.


## Dashboard Eureka integration 17 October 2026

Dashboard startup now registers dashboard-service with http://localhost:8080/eureka/, advertises DASHBOARD_PORT (8086 by default), sends heartbeats and deregisters on graceful shutdown. DASHBOARD_HOST defaults to localhost; home/status are / and process health is /health. The launcher freezes its listening port before shared configuration loading, keeping registration consistent. No registry-server, gateway, authentication or financial formula changes. All 20 isolated dashboard tests passed, including HTTP registration/deregistration against a test registry and custom-port configuration. The live registry is reachable and the current dashboard process responds on 8086, but registration requires restarting that existing process with the new code.


## Recommendation speed optimization 19 October 2026

Python now executes all five existing allowlisted tools directly, eliminating model tool-selection calls. One normal model generation call remains, with the existing single repair attempt unchanged. Only the model-bound payload is compacted: computed formula strings and null metadata are omitted, and the response schema/citation list is no longer repeated in user messages because Ollama receives the same structured-output schema separately. Returned evidence retains full formulas, values, availability, metadata and limitations. API request/response field structure, validator, up-to-six actions and 1800-token generation budget are unchanged; wording and actual output character count remain model-dependent. No financial calculations, authentication, database queries, registry or dashboard logic were changed. All 39 recommendation tests passed. The synthetic fixture final user-message JSON shrank from 8253 to 4886 characters (40.8 percent); live request latency was not benchmarked. Restart the recommendation API to load the changes.


## Recommendation qualitative-prose validation 19 October 2026

The existing qualitative-only prompt policy is now enforced in structured output and Python validation: summary, title, interpretation and suggested_action accept nonempty digit-free strings. Exact numeric evidence and citation IDs (including digits) remain unchanged. Validation reports all offending field paths and numeric tokens; the existing single repair attempt receives precise rewrite/citation/interpretation guidance. The response field structure, six-action limit, 1800-token output budget and one-call normal path remain intact. Existing diagnostic LLM-response printing was preserved. All 42 regression tests pass, including matching-but-disallowed numeric prose, dates, citation IDs with digits, detailed repair feedback, full evidence preservation and untrimmed output. A synthetic live Qwen test timed out after 60 seconds, so live-model verification remains pending. No API, Oracle, credentials, registry or dashboard changes.


## Unicode numeral and citation feedback fix 19 October 2026

A live rejected response used full-width numerals and unqualified revenue metric IDs. The structured-output prose pattern now also excludes full-width and Arabic-Indic decimal digits. Python normalizes text only for inspection and checks Unicode numeric characters, catching full-width, Arabic, circled and Roman numeral forms without silently rewriting the returned text. Numeric and citation failures are aggregated into one repair message. Unqualified IDs receive an exact qualified-ID suggestion only when a matching available suffix is unique; no citation is automatically changed. RISCOR explicitly prohibits alternate numeral glyphs and requires the tool prefix in every citation. Response fields, full evidence, output budget and single-retry limit remain unchanged. All 46 isolated tests passed, including the reported combination of full-width prose and missing prefixes. This does not guarantee semantic correctness or successful repair by the small model; rejected outputs still fail explicitly. No additional live model request was made for this fix.


## Central API documentation update 19 October 2026

Added a central Swagger UI at http://localhost:8081/swagger-ui.html with five service definitions. Gateway /openapi/{msme,assessment,warning,recommendation,dashboard} routes proxy generated service specifications. Java business services expose /v3/api-docs with springdoc 3.1.1; Python APIs expose /openapi.json. Only documentation metadata is public; business authentication, role/assignment rules and payloads remain unchanged. Operational health checks and dashboard HTML/static routes are outside the central business catalog. Usage and verification details are in API_DOCUMENTATION.md. Source edits preserve existing unrelated working-tree changes. Verification passed: 2 gateway documentation integration tests, 1 MSME and 1 assessment OpenAPI test, 24 warning-service tests including H2 workflow/public-spec/security checks, 47 recommendation tests and 21 dashboard tests (96 tests total). All four changed Java modules compiled. Live Oracle, Ollama, Eureka discovery and running-service deployment were not exercised. Maven used temporary direct-Central settings because the configured corporate proxy could not resolve; global settings were not changed.


## Positive prose allowlist 20 October 2026

A later model response used mathematical bold and subscript numerals that the partial schema blacklist did not exclude. The schema now uses a positive allowlist of standard English letters, whitespace and ordinary punctuation for generated prose; Python independently enforces the same pattern and retains Unicode-aware numeric diagnostics. Citation IDs, business names in full evidence, numeric evidence, public JSON fields, the six-action limit, output token budget and retry count are unchanged. RISCOR explains the character policy and keeps the existing evidence/citation/financial-interpretation restrictions. All 50 regression tests passed; one checks every numeric code point recognized by the current Python Unicode database against the schema pattern, and another reproduces the mathematical/subscript example. No additional live model check was run, so live generation under this constraint remains unverified. This character constraint does not guarantee semantic correctness.


## Evidence-supported numeric prose 20 October 2026

At user request, supported numbers are again allowed in recommendation prose. The digit-free/English-only schema constraint was removed. A new numeric_prose helper normalizes Unicode decimal/compatibility digits in the four generated text fields while preserving exact citation IDs and full evidence. Numeric literals in actions must match that action's cited metric values; summary literals may match any available value. Validation supports decimal rounding, unit checks and explicit positive decline magnitudes for negative growth metrics, without permitting general sign reversal. Dates, unsupported notation, wrong values/units/citations and invented numeric deadlines still fail with field-specific repair feedback. No automatic citation substitution or extra model calls. Public fields, output budget, six-action cap, fast fixed-tool execution and one-repair limit are unchanged. All 59 regression tests passed; no live model generation was run. Semantic narrative correctness still requires RM review. Earlier qualitative-only documentation is superseded by this policy.

## Recommendation metric precision 21 October 2026

At user request, calculated metric values now use two decimal places with Decimal half-up rounding before model input and returned evidence. Calculations retain raw snapshot precision, including the receivables/revenue growth gap, which rounds only after subtracting unrounded growth ratios. RISCOR and the existing repair instruction request exactly two decimal places for supported numerical prose. JSON evidence remains numeric, so trailing zero formatting is not guaranteed by serialization. Response fields, citations, unit/sign checks, Unicode normalization, output budget and retry count are unchanged. All 63 isolated recommendation tests passed, including half-up ties, negative values, final-only gap rounding, identical model/evidence values and rejection of unsupported figures. Live Ollama generation was not exercised. Dashboard code is unchanged; it imports the shared metrics and will use the new precision after restart. Earlier four-decimal metric behavior is superseded.
