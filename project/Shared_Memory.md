
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
