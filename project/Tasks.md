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


## Positive prose allowlist 20 October 2026

A later model response used mathematical bold and subscript numerals that the partial schema blacklist did not exclude. The schema now uses a positive allowlist of standard English letters, whitespace and ordinary punctuation for generated prose; Python independently enforces the same pattern and retains Unicode-aware numeric diagnostics. Citation IDs, business names in full evidence, numeric evidence, public JSON fields, the six-action limit, output token budget and retry count are unchanged. RISCOR explains the character policy and keeps the existing evidence/citation/financial-interpretation restrictions. All 50 regression tests passed; one checks every numeric code point recognized by the current Python Unicode database against the schema pattern, and another reproduces the mathematical/subscript example. No additional live model check was run, so live generation under this constraint remains unverified. This character constraint does not guarantee semantic correctness.


## Evidence-supported numeric prose 20 October 2026

At user request, supported numbers are again allowed in recommendation prose. The digit-free/English-only schema constraint was removed. A new numeric_prose helper normalizes Unicode decimal/compatibility digits in the four generated text fields while preserving exact citation IDs and full evidence. Numeric literals in actions must match that action's cited metric values; summary literals may match any available value. Validation supports decimal rounding, unit checks and explicit positive decline magnitudes for negative growth metrics, without permitting general sign reversal. Dates, unsupported notation, wrong values/units/citations and invented numeric deadlines still fail with field-specific repair feedback. No automatic citation substitution or extra model calls. Public fields, output budget, six-action cap, fast fixed-tool execution and one-repair limit are unchanged. All 59 regression tests passed; no live model generation was run. Semantic narrative correctness still requires RM review. Earlier qualitative-only documentation is superseded by this policy.

## Recommendation metric precision 21 October 2026

At user request, calculated metric values now use two decimal places with Decimal half-up rounding before model input and returned evidence. Calculations retain raw snapshot precision, including the receivables/revenue growth gap, which rounds only after subtracting unrounded growth ratios. RISCOR and the existing repair instruction request exactly two decimal places for supported numerical prose. JSON evidence remains numeric, so trailing zero formatting is not guaranteed by serialization. Response fields, citations, unit/sign checks, Unicode normalization, output budget and retry count are unchanged. All 63 isolated recommendation tests passed, including half-up ties, negative values, final-only gap rounding, identical model/evidence values and rejection of unsupported figures. Live Ollama generation was not exercised. Dashboard code is unchanged; it imports the shared metrics and will use the new precision after restart. Earlier four-decimal metric behavior is superseded.

## Recommendation prompt grounding refinement 21 October 2026

Refined RISCOR and the existing repair message following an invented suggested-action percentage and a percentage-point gap mislabeled as percent. The prompt directs the model to select supporting metrics before writing, copy two-decimal values, preserve units/period/sign, and cite each action's own evidence. It explicitly distinguishes zero percentage points from zero percent, removes invented numeric targets/deadlines in favor of concrete qualitative review actions, and forbids adding unrelated citations merely to justify a number. Existing advisory detail, schema, output budget, single repair, evidence, calculations, validation and authentication are unchanged. All 63 isolated recommendation tests passed. Live model adherence has not been verified; prompting does not guarantee successful generation. Restart the recommendation API to load the new prompt and repair instructions.

## Explicit recommendation metric definitions 21 October 2026

Added a metric-ID-specific glossary to RISCOR for all revenue and liquidity metrics. It distinguishes same-month YoY from trailing-twelve-month growth, completed calendar quarters from rolling windows, overdue share of total receivables from credit used as a share of sanctioned limit, and percentage-point changes/gaps from percent growth. It prescribes precise narrative labels and clarifies that outstanding receivables and credit headroom do not prove cash-flow health or borrowing suitability. This is a prompt-only change; calculations, API fields, evidence, validation, generation budget and retry behavior are unchanged. All 63 isolated recommendation tests pass. Live Ollama interpretation has not been verified; restart the recommendation API to load the prompt.

## Value-only recommendation validation 21 October 2026

At explicit user request, business-content validation now only compares prose numeric literals with available calculated tool values across all tools. Equality is exact after Unicode/number-format normalization: 45, 45.0 and 45.00 match; extra rounding or sign reversal does not. Action citations do not restrict numeric candidates. Removed citation membership/enumeration, unit, deadline, risk-label and decline-wording checks; the schema still retains the response fields, basic types and six-action limit. Citation arrays can be empty, and arbitrary citation strings are preserved. Number notation still must be parseable for comparison. Dates, years and reference boundaries have no special exemption and may fail when not equal to a metric value. Prompt financial guidance remains, but runtime does not enforce those meanings. Updated repair instructions, README and affected regression tests. All 63 isolated service tests passed, including exact equality, sign mismatch, unit independence, citation independence, supported deadlines and unmatched year/boundary cases. Financial calculations, full evidence, authentication, registry, model options and single repair are unchanged. No live Oracle or Ollama request was run. This supersedes earlier citation-scoped/unit-aware validation policies. Restart the recommendation API to load the changes.

## Copy tool values without LLM rounding 21 October 2026

Updated RISCOR and the agent repair message to copy each tool value exactly as displayed, preserving sign and decimal representation rather than forcing two displayed decimal places. Removed conflicting instructions to pad zeros or reformat to two decimals; examples clarify that 82.9 remains 82.9 and 18.41 remains 18.41. Python tool calculations still round final metric values to two decimal places; value-only validation and response fields are unchanged. All 63 isolated regression tests passed. Live model adherence was not exercised. Restart the recommendation API to load the updated instructions.

## Sign-independent numeric magnitude validation 21 October 2026

At user request, numeric validation compares exact absolute Decimal magnitudes, ignoring plus/minus signs for every available metric and generated prose field. A tool value of -14.43 matches 14.43, +14.43 and -14.43; different magnitudes or decimal positions remain invalid. It does not inspect growth/decline wording. Full signed tool evidence is preserved. Main/repair prompts now permit direction in words while requesting unchanged magnitudes. JSON shape, value-only citation/unit policy, formulas, model settings, authentication and single repair are unchanged. All 65 isolated tests pass, including Unicode signs, both sign directions, zero, magnitude differences, decimal placement and evidence immutability. Live Ollama behavior was not tested. Earlier sign-sensitive equality policies are superseded. Restart the recommendation API to load the changes.

## Leading-zero decimal recognition 21 October 2026

At user request, the prose number parser now recognizes leading-dot decimals such as .87 as 0.87, including signed and Unicode-normalized variants. It never divides integer 87 by 100. Added RISCOR and repair guidance to retain leading zeros. Exact magnitude matching, ignored signs, response structure and signed tool evidence are unchanged; returned prose is not reformatted by this parsing change. All 68 isolated tests pass, including legitimate leading-dot values, rejection of 87 versus 0.87, malformed decimals, scientific notation and a one-call agent result. Live Ollama generation was not tested. Restart the recommendation API to load the changes.

## Reporting years and month/year durations in prose 21 October 2026

At user request, reporting years 1900–2199, ISO dates, numeric month/year dates and integer month/year durations are recognized separately and exempted from financial numeric comparison. September 2026, 2026-09 and last 12 months no longer require matching financial values. Financial suffixes such as 2026% remain checked. Main/repair prompts now permit reporting periods and windows grounded in supplied context. This does not validate temporal factual accuracy or authorize arbitrary financial numbers. Signed full evidence, magnitude equality, ignored signs, leading-dot parsing, JSON fields and model options are unchanged. All 72 isolated tests passed, and the exact pasted response from attachment 825e8e7d-21ee-4c39-a187-493c00f65a53 passed against its reported tool values without an Ollama call. No live Oracle/Ollama request was made. Earlier blanket date/year prose restrictions are superseded. Restart the recommendation API to load the changes.

## 23 October 2026 ? manual assessment / recommendation wiring

Implemented manual RM assessment-to-warning dispatch, new-warning-only recommendation attempts, exact warning selection/save-if-empty, root environment loading and dashboard launcher repair. Reporting-period Python evidence selection corrected. Existing UI layout retained. Verification pending: the user will start services separately and perform runtime testing. Closure-policy clarification remains separate; current closure behavior was preserved. No database changes or live tests performed.

Recommendation Windows launcher: added start-api.cmd matching dashboard startup behavior, with root .venv fallback and visible errors. Service launch not performed.

## 23 October 2026 - completed agreed workflow changes

Completed representative visibility/privacy, Admin recommendation retry, unavailable marker, consistent RM closure, reporting-period chronology/backfill link repair/green flag, immediate versioned thresholds, global in-memory notification view/navigation/refresh, inactive-business checks, cookie session restoration/CSRF, and consistent error handling. Restored previously agreed completed-month/zero-value rules found missing during isolated checks. SQL, credentials and legacy multiple-intervention records left to the user. Checks passed: 157 selected Java tests, 91 recommendation tests, 21 dashboard tests; frontend production build and lint. Pending user work: restart services individually and test live Oracle/Ollama integration; neither dependency was contacted by these checks.

Recommendation 502 diagnostics completed: distinguish validation from model-runtime failures in the API/UI, and label raw/validated output in the terminal. 93 mocked recommendation tests and the changed frontend module lint passed. Actual failed request cause awaits the user retry/validation feedback; no Oracle/Ollama calls made.

## 23 October 2026 - restored agent integration only

User restored Recommendation-service from main and prohibited agent changes. Restored the missing authenticated internal endpoint, exact warning read/generate/save-once behavior, root environment loading and dashboard access-call compatibility around the restored generator. warning_store.py isolates persistence from the financial reader. No edits to agent.py, prompts, metrics.py, numeric_prose.py or database.py; hashes preserved. 86 recommendation tests and 21 dashboard tests passed with mocks/local fixtures, including concurrent generation and closure races. No Oracle/Ollama calls or schema/credential changes. Restart the recommendation API; live integration remains for the user.
