# Current Contracts

Updated: 13 October 2026. Based on Atharva's existing infrastructure configuration and SQL DDL.

## Infrastructure Configuration

| Item | Current value |
| --- | --- |
| Registry application / port | `RegistryServer` / `8080` |
| Gateway application / port | `api-gateway` / `8081` |
| Gateway Eureka URL | `http://localhost:8080/eureka/` |
| Gateway registration / registry fetch | Both enabled |
| Exposed gateway management endpoints | `health`, `info` |

No business REST endpoints, DTOs, event payloads, or gateway business routes are defined in this checkout. SQL fields below are database definitions, not approved API payloads.

## Existing SQL Rules

| Area | Declared constraints |
| --- | --- |
| Users | Unique email and MSME ID; roles `RM`, `RISK_ADMIN`, `MSME_REP`; account status `ACTIVE` or `INACTIVE`; representatives require an MSME ID, other roles require it to be null |
| MSME | Positive sanctioned credit limit; assigned user required; active flag `Y` or `N` |
| Financial snapshots | One snapshot per MSME/reporting period; period must be the first day of the month at midnight; nonnegative amounts; overdue receivables cannot exceed total receivables |
| Thresholds | Unique MSME/version; defaults 15/30/70 for revenue decline, overdue receivables, credit utilisation; first two thresholds limited to 0-100, credit threshold only constrained to be nonnegative |
| Assessments | One assessment per snapshot; optional previous assessment |
| Indicators | Unique assessment/indicator pair; nullable calculated value; required threshold value and `Y`/`N` breach flag |
| Warnings | One warning per assessment; default status `OPEN` |
| Interventions | Required warning, creator, and action text; `Y`/`N` active flag; multiple interventions per warning allowed by the schema |
| Follow-up reviews | Unique intervention/assessment pair; required reviewer, outcome, and `Y`/`N` keep-active flag |
| Activity log | Actor type `USER` or `SYSTEM`; required MSME and action code; non-null metadata must satisfy `IS JSON` |

## Domain Values

- Threshold status: `PENDING`, `APPROVED`, `REJECTED`.
- Severity: `HEALTHY`, `WATCH`, `EARLY_STRESS`, `HIGH_PRIORITY`.
- Indicator: `REVENUE_DETERIORATION`, `OVERDUE_RECEIVABLES`, `CREDIT_UTILISATION`.
- Warning status: `OPEN`, `UNDER_REVIEW`, `ESCALATED`, `INTERVENTION_ACTIVE`, `CLOSED_REVIEWED`, `CLOSED`.
- Follow-up outcome: `IMPROVING`, `STABILISING`, `STILL_STRESSED`, `ESCALATED`.

Allowed values do not define lifecycle transitions or role permissions.

## Declared Relationships

- MSME assigned user -> app user; app user MSME -> MSME.
- Financial snapshot -> MSME and submitting app user.
- Risk assessment -> threshold configuration and previous assessment.
- Indicator result -> assessment.
- Intervention -> warning.
- Follow-up review -> intervention.

No foreign keys are declared for threshold MSME/proposer/reviewer IDs, assessment MSME/snapshot IDs, warning assessment ID, intervention creator/closer IDs, follow-up assessment/reviewer IDs, or activity MSME/actor/entity IDs. Role validation and same-MSME consistency are not guaranteed by the existing foreign keys.

Financial amounts use `NUMBER(18,2)`; indicator values use `NUMBER(12,4)`. API serialization, currency, timezone handling, assessment formulas, threshold selection, and service integration contracts remain undefined.


## Proposed Service 3 API update 15 October 2026

The initial module-local REST contract is documented in `warning-intervention-service/docs/openapi.json`, with lifecycle defaults and permissions in its README. This records a proposed Service 3 interface for owner review; it does not approve or change MS1/MS2 APIs, event contracts or gateway routing. No upstream HTTP contract is invented. The service currently uses an isolated reference-schema JDBC adapter requiring agreed read access. Identity-provider configuration and shared integration remain pending.


## Dashboard service update 17 October 2026

Dashboard module-local interfaces: GET / serves the public HTML shell; POST /api/dashboard/login forwards email/password to the configured local msme-service login; GET /api/dashboard/msmes/{msme_id}?as_of=YYYY-MM requires the existing bearer token and current Oracle role/assignment authorization. The response contains context, all 21 metric evidence values, 48 monthly rows including gaps, saved indicator history and limitations. GET /health is process liveness. No upstream API contracts were changed.


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
