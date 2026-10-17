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
