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
