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
