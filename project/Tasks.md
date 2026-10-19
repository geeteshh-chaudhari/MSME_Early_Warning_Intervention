# Current Tasks and Progress

Updated: 13 October 2026.

## Existing Deliverables

| Task | Contributor | Progress |
| --- | --- | --- |
| Registry server setup | Atharva | Scaffolding present, configured on port `8080` |
| API gateway setup | Atharva | Scaffolding present, configured on port `8081`; Eureka client and Actuator configured |
| Initial Oracle schema | Atharva | DDL present for all three domain groups: 10 tables and 9 explicit indexes |
| Project progress documentation | Documentation update | Current source state and ownership recorded |

These statuses confirm files and configuration are present. Builds, runtime integration, and SQL execution have not been verified.

## Remaining Work Visible from This Checkout

| Task | Owner / coordination | Status |
| --- | --- | --- |
| Implement profile/finance business service | Atharva | Code not present |
| Implement assessment business service | Neha | Code not present |
| Implement warning/intervention business service | Geetesh | Code not present |
| Define shared API contracts and service identifiers | All module owners | Not defined |
| Configure gateway business routes | Infrastructure and module owners | Not configured |
| Validate registry/gateway builds, startup, and registration | Infrastructure maintainer to confirm | Not verified |
| Validate Oracle DDL and agree cross-module reference enforcement | All module owners | Not verified / decision pending |

## Integration Blockers

- Business-service code and gateway routes are absent.
- API payloads and service-to-service communication are undefined.
- SQL defines status values but no business transition/calculation rules.
- Cross-module ID integrity and automatic timestamp updates need implementation decisions.

Update progress with evidence as work is added; table definitions alone do not establish service completion.


## Warning and Intervention implementation update 15 October 2026

Owner: Geetesh. The `warning-intervention-service/` source project is now present with protected REST APIs, Oracle JDBC persistence, warning/decision history, owned interventions, due dates, monthly reviews, and representative summaries. Java 25, Boot 4.1.1, Cloud 2025.1.3 and a Maven wrapper pinned to 3.9.1 are configured.

Final build and tests are unverified; further testing was stopped at the user's request. Live Oracle, identity-provider integration and Eureka registration have not been verified. No database migrations were applied.

Integration work remaining: agree MS1/MS2 interfaces or read grants/synonyms, select an identity provider and provision trusted identity mappings, review owner/due-date schema extensions and lifecycle defaults, configure the gateway route, and verify the designated development deployment. Existing infrastructure and root DDL are preserved.


## Dashboard service update 17 October 2026

Dashboard source, start-dashboard.cmd, API/static frontend, setup instructions and tests are complete. All 17 isolated Python tests passed; JavaScript syntax passed. These cover exact recommendation-tool parity, gaps, invalid data, report access denial and mocked login. Browser visual review was declined, and real Oracle/login end-to-end testing remains unverified. No dependencies beyond the existing recommendation environment are needed.


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
