
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

