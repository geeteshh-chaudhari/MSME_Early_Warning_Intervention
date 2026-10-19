# Central API documentation

Open http://localhost:8081/swagger-ui.html after restarting the gateway and all five APIs. The selector contains MSME, Assessment, Warning and Intervention, Recommendation and Dashboard. Eureka must be running on port 8080 and the APIs registered before gateway specification routes can resolve.

## Sign in and try an endpoint

1. Select MSME and execute POST /api/auth/login with a synthetic demo account.
2. Copy accessToken from the successful response.
3. Select Authorize and paste the raw token without the Bearer prefix.
4. Select an endpoint allowed for that account. Execute sends a real API request.
5. When switching definitions, authorize again if needed. Authorization is not configured to persist across browser reloads.

Java and Python specifications use the same bearerAuth name and a relative server URL /, so central requests go to the gateway origin. Existing JWT signature, active-account, role and assignment checks continue to apply. The specification endpoints are public metadata and contain no database records or credentials.

## Specification locations

| Service | Direct specification | Gateway specification |
| --- | --- | --- |
| MSME | http://localhost:8082/v3/api-docs | http://localhost:8081/openapi/msme |
| Assessment | http://localhost:8083/v3/api-docs | http://localhost:8081/openapi/assessment |
| Warning and Intervention | http://localhost:8084/v3/api-docs | http://localhost:8081/openapi/warning |
| Recommendation | http://localhost:8085/openapi.json | http://localhost:8081/openapi/recommendation |
| Dashboard | http://localhost:8086/openapi.json | http://localhost:8081/openapi/dashboard |

Java specifications are generated from controllers and DTOs using springdoc-openapi 3.1.1. A documentation customizer adds readable summaries, functional tags, declared role scopes, login security override and creation status codes. Python specifications include existing request validation, bearer security, response-envelope schemas and documented error statuses. Response metadata does not change JSONResponse serialization or business calculations.

## Operational endpoints

The central catalog contains business API paths. Recommendation GET http://localhost:8085/health and dashboard GET http://localhost:8086/health remain public process checks. Dashboard GET http://localhost:8086/ and /static/** serve the existing browser UI. Java Actuator endpoints stay on their service ports under their existing exposure and security settings. These paths are not advertised as gateway business operations. Eureka's registry UI remains http://localhost:8080/.

## Troubleshooting

- A specification URL returning 503 usually means the API has not registered with Eureka, is unavailable, or cannot be reached from the gateway.
- A direct Java specification returning 401 means documentation permit rules were not loaded; restart the updated service. A stale invalid Authorization header can also be rejected by its JWT filter.
- No Authorize button means the wrong or an old specification was loaded.
- 401 on a protected API means the JWT is missing, invalid, expired or no longer matches the current account. 403 means the user's role or assignment does not allow that operation.
- A recommendation can still fail if Oracle or Ollama is unavailable. Swagger does not bypass dependencies or access checks.
- Download the JSON specification from any location above for use in an OpenAPI client or Postman.

The dependency supports Spring Boot 4.1.x. No separate Swagger UI is installed in business services. Python /docs and /redoc remain disabled. No business API payload, database migration, financial formula or workflow transition is changed by this setup.

## Verification

Verification passed: 2 gateway documentation integration tests, 1 MSME and 1 assessment OpenAPI test, 24 warning-service tests including H2 workflow/public-spec/security checks, 47 recommendation tests and 21 dashboard tests (96 tests total). All four changed Java modules compiled. Live Oracle, Ollama, Eureka discovery and running-service deployment were not exercised. Maven used temporary direct-Central settings because the configured corporate proxy could not resolve; global settings were not changed.

On this Windows JDK 25 test host, the gateway test needed `-DargLine=-Djdk.net.unixdomain.tmpdir=target` to use a short temporary socket path. This is a test invocation option, not a production configuration change.
