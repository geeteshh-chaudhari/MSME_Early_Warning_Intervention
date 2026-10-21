# Current Architecture

Updated: 13 October 2026. Registry, gateway, and initial DDL contributed by Atharva.

## Infrastructure Present

| Component | Implementation |
| --- | --- |
| Registry server | Application name `RegistryServer`; port `8080`; `@EnableEurekaServer`; Spring MVC; WAR packaging and servlet initializer |
| API gateway | Application name `api-gateway`; port `8081`; reactive Spring Cloud Gateway; Eureka client; JAR packaging |
| Discovery | Gateway registers with Eureka and fetches its registry at `http://localhost:8080/eureka/` |
| Management | Gateway exposes Actuator `health` and `info` endpoints by configuration |

Both services declare Java 22, Spring Boot `4.1.1`, and Spring Cloud `2025.1.3` in their Maven files.

No business routes or discovery-locator settings are configured in the gateway. The registry does not explicitly disable its own Eureka registration or registry fetching. Runtime behavior remains unverified.

## Database Definitions Present

`Profile_Finance_DDL.sql` uses Oracle types, identity columns, timestamp defaults, and JSON validation. Its footer groups tables as follows:

| Module | Owner | Tables |
| --- | --- | --- |
| Profile & Finance | Atharva | `app_user`, `msme`, `financial_snapshot` |
| Assessment | Neha | `threshold_config`, `risk_assessment`, `assessment_indicator_result` |
| Warning & Intervention | Geetesh | `warning`, `intervention`, `follow_up_review`, `activity_log` |

The file defines 10 tables, 9 explicit foreign keys, and 9 explicit indexes. It creates both user and MSME tables before adding the user-to-MSME foreign key. Separate schemas, grants, and migration tooling are not defined.

Several cross-module IDs have no foreign keys; their integrity enforcement is not established. `updated_at` columns have insert defaults, with no update triggers provided.

## Integration Status

The domain names and schema describe a profile/finance -> assessment -> warning/intervention workflow. Business-service implementations, service-to-service APIs, gateway routes, and workflow logic are not present in this checkout. The DDL has not been executed against Oracle during this review.


## Service 3 implementation update 15 October 2026

`warning-intervention-service/` now provides a Java 25 Spring Boot 4.1.1 executable-JAR source project using Spring JDBC, configurable OAuth2 JWT resource-server security and the existing Eureka client configuration. Default port is 8083; the registry remains at the existing development URL on port 8080. No gateway route is changed.

The module owns warning/intervention/review/audit persistence and proposes an AUTH_IDENTITY mapping plus intervention owner and due-date columns. The current reference-schema adapter needs explicit upstream grants/synonyms and retains the root DDL's cross-schema coupling. The adapter can be replaced when MS1/MS2 service contracts are agreed. SQL extensions are not executed automatically. Runtime integration and the final build remain unverified.


## Dashboard service update 17 October 2026

A separate dashboard-service now serves a local web dashboard and Python API on loopback port 8086. It imports the existing Recommendation-service MetricTools, Oracle repository and JWT/account/assignment checks; it introduces no new financial formulas, writes, gateway routes or Eureka registration. The sibling recommendation code and its configuration remain runtime dependencies.


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
