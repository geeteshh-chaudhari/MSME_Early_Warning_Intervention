"""Authenticated HTTP interface for the existing command-line recommendation agent."""
from contextlib import asynccontextmanager
from datetime import date, datetime
import json
import logging
import os
from threading import Lock

from fastapi import Depends, FastAPI, HTTPException, Header
from fastapi.responses import JSONResponse
from fastapi.exceptions import RequestValidationError
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from pydantic import BaseModel, ConfigDict, Field, field_validator

from .agent import OllamaClient, RecommendationAgent, dumps
from .api_security import OracleAccess, TokenVerifier
from .config import ROOT, Settings
from .database import OracleRepository
from .metrics import MetricTools, TOOL_DESCRIPTIONS

log = logging.getLogger(__name__)
UNAVAILABLE = "reccommendation service not available"


def saved_result(payload):
    """Any populated field is authoritative; never regenerate a nonempty record."""
    if payload is None or not payload.strip() or payload == UNAVAILABLE:
        return None
    try:
        value = json.loads(payload)
    except (ValueError, TypeError):
        value = {"summary": payload, "actions": [], "limitations": []}
    if not isinstance(value, dict):
        value = {"summary": payload, "actions": [], "limitations": []}
    return dict(value) if isinstance(value.get("recommendation"), dict) else {"recommendation": value}


class RecommendationRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    warning_id: int | None = Field(default=None, gt=0, strict=True, description="Exact selected warning; required for recommendations")
    msme_id: int = Field(gt=0, strict=True)
    as_of: str = Field(pattern=r"^\d{4}-\d{2}$", description="Completed reporting month, YYYY-MM")
    metrics_only: bool = Field(default=False, strict=True)
    question: str = Field(default="Interpret financial trends, seasonal context and propose RM review actions.",
                          min_length=1, max_length=2000)

    @field_validator("as_of")
    @classmethod
    def completed_month(cls, value):
        period = date.fromisoformat(value + "-01")
        if period >= date.today().replace(day=1):
            raise ValueError("Choose a completed reporting month before the current month.")
        return value


def create_app(settings=None, verifier=None, access=None, repository=None, discovery=True):
    @asynccontextmanager
    async def lifespan(app):
        from dotenv import load_dotenv
        load_dotenv(ROOT / ".env", override=False)
        load_dotenv(ROOT.parent / ".env", override=False)
        app.state.settings = settings or Settings.load()
        app.state.verifier = verifier or TokenVerifier.from_environment()
        app.state.access = access or OracleAccess(app.state.settings, os.getenv("ORACLE_USER_SCHEMA", "").strip())
        app.state.repository = repository or OracleRepository(app.state.settings)
        app.state.work_lock = Lock()
        app.state.warning_locks = [Lock() for _ in range(64)]
        client = None
        if discovery:
            from py_eureka_client.eureka_client import EurekaClient
            client = EurekaClient(
                eureka_server=os.getenv("EUREKA_SERVER_URL", "http://localhost:8080/eureka/"),
                app_name="recommendation-service",
                instance_host=os.getenv("SERVICE_HOST", "localhost"),
                instance_port=int(os.getenv("SERVICE_PORT", "8085")),
                health_check_url="/health", status_page_url="/health",
                home_page_url="/", should_discover=False,
            )
        try:
            if client:
                await client.start()
            yield
        finally:
            if client:
                await client.stop()

    app = FastAPI(title="MSME Recommendation API", lifespan=lifespan,
                  version="1.0.0", docs_url=None, redoc_url=None, openapi_url="/openapi.json",
                  servers=[{"url": "/", "description": "Current origin / API Gateway"}],
                  root_path_in_servers=False)
    bearer = HTTPBearer(auto_error=False, scheme_name="bearerAuth",
                        description="MSME login accessToken. Paste the token without a Bearer prefix.")

    def authenticate(credentials: HTTPAuthorizationCredentials | None = Depends(bearer)):
        from .api_security import unauthorized
        if credentials is None or credentials.scheme.lower() != "bearer":
            raise unauthorized()
        identity = app.state.verifier.verify(credentials.credentials)
        if identity[1] not in ("RM", "RISK_ADMIN"):
            raise HTTPException(403, "Only the assigned RM or Risk Admin can access recommendations.")
        return identity

    @app.exception_handler(Exception)
    async def unexpected_error(request, exc):
        # Do not return or log SQL, credentials, tokens or financial input.
        log.error("Recommendation dependency failure: %s", type(exc).__name__)
        return JSONResponse(status_code=500, content={"detail": "An unexpected application error occurred."})

    @app.exception_handler(RequestValidationError)
    async def invalid_request(request, exc):
        return JSONResponse(status_code=400, content={"detail": "Invalid or missing request value."})

    @app.get("/health", include_in_schema=False)
    def health():
        return {"status": "UP", "service": "recommendation-service"}

    @app.post("/api/recommendations", tags=["Recommendations"],
              summary="Generate evidence-based MSME recommendations",
              description="Assigned RM or Risk Admin. Return the saved WARNING.RECOMMENDED_ACTION for the exact warning_id, business and completed reporting month. If empty or the unavailable marker is present, generate with the local model and save JSON before returning success. Mismatched warnings return 404; closed warnings return 409 before inference. Requests for the same warning wait and recheck the saved field. metrics_only skips saved advice, Ollama and writes. No case decisions are changed.",
              responses={200: {'description': 'Full generated advice, metrics-only evidence, or insufficient-data result. '
                      'Full numeric evidence is returned separately from qualitative prose.',
       'content': {'application/json': {'schema': {'type': 'object',
                                                   'required': ['status',
                                                                'msme_id',
                                                                'as_of_month',
                                                                'formula_version',
                                                                'evidence'],
                                                   'properties': {'status': {'type': 'string',
                                                                             'enum': ['generated',
                                                                                      'metrics_only',
                                                                                      'insufficient_data']},
                                                                  'msme_id': {'type': 'integer'},
                                                                  'as_of_month': {'type': 'string',
                                                                                  'format': 'date'},
                                                                  'formula_version': {'type': 'string'},
                                                                  'model': {'type': 'string'},
                                                                  'tool_trace': {'type': 'array',
                                                                                 'items': {'type': 'object',
                                                                                           'properties': {'step': {'type': 'integer'},
                                                                                                          'tool': {'type': 'string'}}}},
                                                                  'evidence': {'type': 'object',
                                                                               'additionalProperties': {'type': 'object'},
                                                                               'description': 'Five '
                                                                                              'tool '
                                                                                              'result '
                                                                                              'groups. '
                                                                                              'Numeric '
                                                                                              'evidence '
                                                                                              'includes '
                                                                                              'availability, '
                                                                                              'units, '
                                                                                              'formulas '
                                                                                              'and '
                                                                                              'comparison '
                                                                                              'metadata.'},
                                                                  'recommendation': {'type': 'object',
                                                                                     'properties': {'summary': {'type': 'string'},
                                                                                                    'actions': {'type': 'array',
                                                                                                                'maxItems': 6,
                                                                                                                'items': {'type': 'object',
                                                                                                                          'required': ['title',
                                                                                                                                       'interpretation',
                                                                                                                                       'suggested_action',
                                                                                                                                       'evidence_metric_ids'],
                                                                                                                          'properties': {'title': {'type': 'string'},
                                                                                                                                         'interpretation': {'type': 'string'},
                                                                                                                                         'suggested_action': {'type': 'string'},
                                                                                                                                         'evidence_metric_ids': {'type': 'array',
                                                                                                                                                                 'items': {'type': 'string'}}}}},
                                                                                                    'limitations': {'type': 'array',
                                                                                                                    'items': {'type': 'string'}}}}}}}}},
 401: {'description': 'Missing, invalid or expired JWT / inactive current account',
       'content': {'application/json': {'schema': {'type': 'object',
                                                   'properties': {'detail': {'type': 'string'}}}}}},
 403: {'description': 'Role, MSME assignment or active status does not permit access',
       'content': {'application/json': {'schema': {'type': 'object',
                                                   'properties': {'detail': {'type': 'string'}}}}}},
 404: {'description': 'No snapshot for the exact requested reporting month',
       'content': {'application/json': {'schema': {'type': 'object',
                                                   'properties': {'detail': {'type': 'string'}}}}}},
 409: {'description': 'The selected warning is closed or changed during generation.'},
 502: {'description': 'Model generation or response validation failed',
       'content': {'application/json': {'schema': {'type': 'object',
                                                   'properties': {'detail': {'type': 'string'}}}}}},
 503: {'description': 'Database, account lookup or dependency unavailable',
       'content': {'application/json': {'schema': {'type': 'object',
                                                   'properties': {'detail': {'type': 'string'}}}}}}})
    def recommend(body: RecommendationRequest, identity=Depends(authenticate)):
        user_id, role = identity
        if role != "__SERVICE__":
            try:
                app.state.access.authorize(user_id, role, body.msme_id)
            except HTTPException:
                raise
            except Exception:
                raise HTTPException(503, "Account/assignment lookup unavailable. Check database configuration and read grants.") from None
        period = date.fromisoformat(body.as_of + "-01")

        def tools_for_period():
            try:
                data = app.state.repository.load(body.msme_id, period)
            except Exception:
                raise HTTPException(503, "Financial history unavailable. Check database configuration and read grants.") from None
            def snapshot_month(snapshot):
                value = snapshot["reporting_period"]
                if isinstance(value, datetime):
                    value = value.date()
                return value.replace(day=1)
            if not any(snapshot_month(s) == period for s in data["snapshots"]):
                raise HTTPException(404, "No financial snapshot exists for the requested reporting month.")
            return MetricTools(data, period)

        # Evidence does not use Ollama and must not wait behind a generation.
        if body.metrics_only:
            tools = tools_for_period()
            result = {"status": "metrics_only", "msme_id": body.msme_id,
                      "as_of_month": period.isoformat(), "formula_version": "1.0",
                      "evidence": {name: tools.execute(name) for name in TOOL_DESCRIPTIONS}}
            return JSONResponse(json.loads(dumps(result)), headers={"Cache-Control": "no-store"})
        if body.warning_id is None:
            raise HTTPException(400, "Select a specific open warning before requesting recommendations.")

        def target():
            try:
                warning = app.state.repository.recommendation_for_warning(body.warning_id, body.msme_id, period)
            except Exception:
                raise HTTPException(503, "Warning lookup unavailable. Check database configuration and SELECT access.") from None
            if warning is None:
                raise HTTPException(404, "Warning does not match the selected business and reporting month.")
            if warning["warning_status"] in ("CLOSED", "CLOSED_REVIEWED"):
                raise HTTPException(409, "This warning is closed. Missing recommendations can only be generated for open warnings.")
            return warning

        def stored_response(warning):
            result = saved_result(warning["payload"])
            if result is None:
                return None
            result.update(status="generated", msme_id=body.msme_id, as_of_month=period.isoformat(),
                          recommendation_source="saved", warning_id=body.warning_id,
                          stored_payload=warning["payload"])
            result.setdefault("evidence", {})
            result.setdefault("formula_version", "1.0")
            return JSONResponse(json.loads(dumps(result)), headers={"Cache-Control": "no-store"})

        cached = stored_response(target())
        if cached is not None:
            return cached
        # Public and internal calls share these locks in the single API worker.
        # Recheck after waiting so simultaneous clicks never repeat generation.
        with app.state.warning_locks[body.warning_id % len(app.state.warning_locks)]:
            cached = stored_response(target())
            if cached is not None:
                return cached
            with app.state.work_lock:
                cached = stored_response(target())
                if cached is not None:
                    return cached
                tools = tools_for_period()
                def unavailable():
                    try:
                        app.state.repository.save_recommendation_for_warning(
                            body.warning_id, body.msme_id, period, UNAVAILABLE)
                    except Exception:
                        raise HTTPException(503, "Recommendation unavailable and its status could not be saved. Check database access.") from None
                try:
                    result = RecommendationAgent(tools, OllamaClient(app.state.settings)).run(body.question)
                except ValueError:
                    log.warning("Recommendation validation failed for warning %s; no generated JSON saved", body.warning_id)
                    unavailable()
                    raise HTTPException(502, "The model response failed validation. No generated recommendation was saved; "
                                        "the unavailable message is retained. Review LLM VALIDATION FAILED in the recommendation terminal, "
                                        "then retry this warning explicitly.") from None
                except RuntimeError as exc:
                    # OllamaClient raises fixed diagnostic messages, without credentials or raw HTTP bodies.
                    log.warning("Ollama generation failed for warning %s: %s", body.warning_id, exc)
                    unavailable()
                    raise HTTPException(502, "Ollama generation did not complete successfully. "
                                        "The unavailable message is retained. Check the recommendation terminal "
                                        "for connection, timeout or model-output errors, then retry this warning explicitly.") from None
                result.update(msme_id=body.msme_id, as_of_month=period.isoformat(), formula_version="1.0",
                              recommendation_source="generated", warning_id=body.warning_id)
                payload = dumps(result)
                if result.get("status") == "generated":
                    try:
                        saved = app.state.repository.save_recommendation_for_warning(
                            body.warning_id, body.msme_id, period, payload)
                    except Exception:
                        raise HTTPException(503, "Recommendation could not be saved. The unavailable message is retained; check WARNING UPDATE access.") from None
                    if not saved:
                        cached = stored_response(target())
                        if cached is not None:
                            return cached
                        raise HTTPException(409, "Warning changed while generation was running. Reload this case.")
                else:
                    unavailable()
                return JSONResponse(json.loads(payload), headers={"Cache-Control": "no-store"})

    @app.post("/internal/recommendations", include_in_schema=False)
    def internal_recommend(body: RecommendationRequest,
                           internal_token: str = Header(default="", alias="X-Internal-Token")):
        import secrets
        expected = os.getenv("INTERNAL_SERVICE_TOKEN", "")
        if len(expected) < 32 or not secrets.compare_digest(internal_token, expected):
            raise HTTPException(401, "Internal service authentication required.")
        return recommend(body, identity=(0, "__SERVICE__"))

    return app


app = create_app()
