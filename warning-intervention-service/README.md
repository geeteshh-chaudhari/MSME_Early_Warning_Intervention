# Warning and Intervention Service

Service 3 implements the supplied Warning & Intervention diagram and maps the existing `Profile_Finance_DDL.sql`. The reference `msme-service` is unchanged.

## Development pattern

Matches `msme-service`: Maven executable JAR, Java 22 release target, Spring Boot 4.1.1, Spring Cloud 2025.1.3, Oracle `ojdbc17`, Spring Data JPA, Jakarta validation, JWT 0.13.0, Eureka, constructor injection, POJO DTOs, `ResponseEntity` controllers, transactional services and `exception/GlobalExceptionHandler`. No Lombok or new framework is introduced.

```text
src/main/java/com/msme/
  WarningInterventionServiceApplication.java
  config/          SecurityConfig
  controller/      WarningController, InterventionController
  dto/
    warning/       Warning requests, responses and history
    intervention/  Intervention requests and responses
    followup/      Monthly review requests and responses
  entity/          Owned entities and immutable upstream projections
  exception/       Domain exceptions, ErrorResponse, GlobalExceptionHandler
  repository/      Spring Data JPA repositories
  security/        Existing JWT and database-backed principal contract
  service/         CaseAccessService, WarningService, InterventionService, ResponseMapper
src/main/resources/application.properties
src/test/java/com/msme/
db/migration/V1__intervention_follow_up_date.sql
```

## Database setup

Use `database_user3` for Service 3. This service writes only `WARNING`, `INTERVENTION`, `FOLLOW_UP_REVIEW`, and `ACTIVITY_LOG`. It does not create users, financial snapshots or assessments. Hibernate DDL and Spring SQL initialization are disabled in the application configuration.

The supplied DDL lacks a due-date field. Review and manually apply `db/migration/V1__intervention_follow_up_date.sql` as the Service 3 owner. It adds `INTERVENTION.FOLLOW_UP_DATE DATE`, a day-only check and a due-date index. It does not change the original SQL file. Existing interventions may have null dates until rescheduled. No migration has been executed against your Oracle database.

Upstream reads currently use unqualified table names, matching the reference service's persistence approach. A DBA must provide SELECT grants and private synonyms in the Service 3 schema for `APP_USER`, `MSME`, `FINANCIAL_SNAPSHOT`, `RISK_ASSESSMENT`, and `ASSESSMENT_INDICATOR_RESULT`, pointing to their actual owners. The service must not receive INSERT/UPDATE/DELETE grants on upstream tables. Oracle grants must be direct grants where required by cross-schema constraints; cross-schema foreign keys need REFERENCES privileges and owner-qualified deployment DDL. The supplied multi-owner DDL does not establish these grants or synonyms. Do not create duplicate upstream tables in Service 3. No upstream HTTP assessment contract exists in the inspected checkout, so no HTTP contract was invented.

## Build and run

Use JDK 22 or newer and Maven 3.6.3 or newer. The project compiles with release 22, matching `msme-service`. This implementation was built using the installed JDK 25. The Maven wrapper is copied unchanged from the reference service and requests Maven 3.10.0; installed Maven can be used directly.

```powershell
cd warning-intervention-service
mvn clean verify
```

The sibling project `.env` already supplies the case-sensitive database keys. Set `JWT_SECRET` to the same Base64 signing secret configured by `msme-service`, with at least 32 decoded bytes. Set `JWT_ISSUER` to the existing issuer if different from `msme-early-warning`. This service validates the existing login tokens; it does not expose a second login endpoint. The key is deliberately not copied into source files.

```powershell
$env:JWT_SECRET = '<same signing secret used by msme-service>'
.\start.ps1
# Or supply a different existing configuration file:
.\start.ps1 -EnvFile 'C:\path\to\service.env'
```

`start.ps1` loads simple `KEY=value` lines into the Java process environment, supports quoted values, and never evaluates file contents as commands. By default it loads `../.env` relative to the service folder. An arbitrary `.env` is not loaded by Spring Boot itself. `.env.example` lists configuration with empty credentials. Do not commit populated configuration files.

| Setting | Value / default |
| --- | --- |
| `database_url` | Required Oracle JDBC URL |
| `database_user3`, `database_password3` | Required Service 3 account |
| `JWT_SECRET` | Required; same key as the existing login service |
| `JWT_ISSUER` | `msme-early-warning` |
| `JWT_EXPIRATION_MS` | `3600000`; retained for reference JWT service compatibility |
| `SERVICE_NAME` | `warning-intervention-service` |
| `SERVER_PORT` | `8083` |
| `EUREKA_SERVER_URL` | Existing development registry: `http://localhost:8080/eureka/` |

No gateway configuration is changed. APIs can be called directly on port 8083. `/actuator/health` is public; business endpoints require Bearer authentication. User ID comes from the verified JWT subject; current role, account status and MSME association are reloaded from `APP_USER`, following the reference service. Invalid, expired, disabled-user or stale-role tokens do not authenticate. RM reads and writes are limited to assigned MSMEs; Risk Admin has global read access; representatives may read only their own case and cannot view internal decision history or write workflow changes. Writes to inactive MSMEs are rejected.

## REST API

All requests use JSON and `Authorization: Bearer <token>`. List endpoints accept zero-based `page=0&size=20`, with size 1–100, and return Spring `Page` JSON sorted by descending ID. Entity IDs and timestamps are server-generated. No request accepts actor IDs, roles or status overrides. Responses use DTOs, excluding user/password data.

| Method and route | Role / behavior |
| --- | --- |
| `POST /api/warnings` | Assigned RM or Risk Admin; create from an existing assessment |
| `GET /api/warnings?msmeId=1&status=OPEN` | Authorized RM, Admin or representative; status filter optional |
| `GET /api/warnings/{id}` | Authorized case reader; includes Why Now and recommendation |
| `POST /api/warnings/{id}/decisions` | RM or Risk Admin, as specified below |
| `GET /api/warnings/{id}/history` | Assigned RM or Risk Admin; append-only decision/activity history |
| `POST /api/warnings/{id}/interventions` | Assigned RM; create action and due date, or revise after a stressed review |
| `GET /api/warnings/{id}/interventions` | Authorized case reader |
| `PATCH /api/interventions/{id}` | Assigned RM; progress notes and due date |
| `POST /api/interventions/{id}/follow-ups` | Assigned RM; monthly review, monitoring, escalation or case closure |
| `GET /api/interventions/{id}/follow-ups` | Authorized case reader |

Example request bodies:

```json
{"assessmentId":101}
```

```json
{"decision":"ACCEPT","reason":"Agree a collections plan with the customer"}
```

```json
{"actionText":"Review overdue invoices weekly","notes":"Customer contacted","followUpDate":"2026-11-15"}
```

```json
{"notes":"First collection meeting completed","followUpDate":"2026-11-20"}
```

```json
{"assessmentId":102,"outcome":"STILL_STRESSED","reviewNotes":"Receivables remain overdue; revise the plan","keepInterventionActive":true,"nextFollowUpDate":"2026-12-15"}
```

To close an improving or stabilising case, use `IMPROVING` or `STABILISING`, `keepInterventionActive:false` and omit `nextFollowUpDate`. Risk Admin closes an escalated case using `{"decision":"CLOSE","reason":"Resolved during risk review"}`. Example dates must be replaced when they fall in the past.

Successful creates return 201; reads and updates return 200. Errors use the reference service's `timestamp`, `status`, `error`, `message`, `path`, and optional `validationErrors` fields. Validation/malformed input returns 400, missing or invalid authentication 401, denied access 403, missing records 404, invalid transitions/duplicates/concurrency conflicts 409, and database availability errors 503. Security-filter responses follow the reference service's status-only 401/403 behavior. SQL and credentials are not returned to clients.

## Workflow defaults for review

These defaults fill gaps in the diagram; they are implementation choices, not additional approved upstream contracts.

* Non-healthy stored assessments (`WATCH`, `EARLY_STRESS`, `HIGH_PRIORITY`) with at least one breached indicator can create warnings. A warning is unique per assessment. No new risk score or assessment calculation runs here. Duplicate creation returns 409.
* Why Now preserves every breached indicator's stored calculated value, threshold and explanation. The primary driver is the first breached code in lexical order: credit utilisation, overdue receivables, then revenue deterioration. Its recommendation is an advisory working-capital, collections or revenue recovery action. This deterministic default can be changed when assessment-service recommendation rules are agreed.
* Every decision requires a reason. RM `ACCEPT` moves `OPEN`/`UNDER_REVIEW` to `UNDER_REVIEW`; recording an action and date then moves to `INTERVENTION_ACTIVE`. Until that action is recorded the case remains visibly under review. The creating RM is the intervention owner; no separate owner-assignment API is invented.
* RM `REJECT` moves `OPEN`/`UNDER_REVIEW` to `CLOSED_REVIEWED`. RM `ESCALATE` moves an open, under-review or intervention-active case to `ESCALATED`.
* Risk Admin `RETURN` or `CONFIRM` accepts only escalated cases. It returns to `INTERVENTION_ACTIVE` when an active intervention exists, otherwise `UNDER_REVIEW`. `CONFIRM` accepts the escalation at the current tier. Risk Admin `CLOSE` immediately closes the escalated warning and all active interventions.
* New interventions require `UNDER_REVIEW`. Revising an active intervention requires its latest review to be `STILL_STRESSED`; the old intervention is closed, its history remains, and a new intervention starts a further cycle. Revision does not rewrite past reviews or reopen a closed warning.
* Follow-ups are recorded manually against actual assessments; no background scheduler invents outcomes. Evidence must belong to the warning's MSME and a reporting month later than the warning baseline, intervention creation month, and all previous reviews of that intervention. At most one review per reporting month is allowed. Future evidence is rejected. Missed months are permitted when data is unavailable; no synthetic review is inserted.
* Outcomes are the DDL's `IMPROVING`, `STABILISING`, `STILL_STRESSED`, `ESCALATED`, selected by the reviewing RM using stored evidence. Continued monitoring requires a next date. Only improving/stabilising outcomes may close; stressed cases remain active for revision and escalated outcomes go to Risk Admin. Closure sets timestamps and actor IDs. Closed cases reject further writes.
* Case changes and `ACTIVITY_LOG` inserts are committed together. Parent warning rows are locked for workflow changes, and database uniqueness protects competing warning creation. History contains actor, reason, timestamp, assessment and prior/next statuses; intervention/review IDs and dates appear in activity descriptions. This service exposes no history update/delete endpoint.

## Verification and integration limits

Verified on 15 October 2026: `mvn verify` completed successfully using Maven 3.9.16 and JDK 25.0.4.1, with release target 22. All 22 tests passed (zero failures, errors or skips), and `target/warning-intervention-service-1.jar` was packaged. Maven's configured corporate proxy did not resolve, so this verification used temporary empty user/global Maven settings to download the missing JWT dependencies directly. Your Maven settings were not modified. Source-file SHA-256 checks confirmed the reference `msme-service` was unchanged.

Tests use an isolated H2 database in Oracle mode with the actual Spring services, method security, JPA transactions and row locking. They cover decision paths, evidence, ownership, reasons, duplicate prevention, monthly follow-ups, revisions, case closure, validation, JWT parsing, concurrency and audit rollback. H2 is a test dependency only. Tests never contact Oracle or Eureka or execute the supplied DDL.

Live Oracle grants/synonyms, application of the due-date migration, matching JWT configuration, Oracle connectivity and Eureka registration must be verified in the designated development environment. Optional notifications are not implemented because the diagram marks them optional and the DDL defines no notification table.

Dependency compatibility was checked against the official [Spring Cloud release table](https://spring.io/projects/spring-cloud/), [Spring Boot system requirements](https://docs.spring.io/spring-boot/4.1/system-requirements.html), and [Oracle JDBC download/certification information](https://www.oracle.com/database/technologies/appdev/jdbc-downloads.html). Runtime certification of the exact Oracle/JDK combination remains an environment-level check.
