"""Authenticated HTTP interface for the existing command-line recommendation agent."""
from contextlib import asynccontextmanager
from datetime import date, datetime
import json
import logging
import os
from threading import Lock

from fastapi import Depends, FastAPI, HTTPException
from fastapi.responses import JSONResponse
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from pydantic import BaseModel, ConfigDict, Field, field_validator

from .agent import OllamaClient, RecommendationAgent, dumps
from .api_security import OracleAccess, TokenVerifier
from .config import ROOT, Settings
from .database import OracleRepository
from .metrics import MetricTools, TOOL_DESCRIPTIONS

log = logging.getLogger(__name__)


class RecommendationRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
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
        app.state.settings = settings or Settings.load()
        app.state.verifier = verifier or TokenVerifier.from_environment()
        app.state.access = access or OracleAccess(app.state.settings, os.getenv("ORACLE_USER_SCHEMA", "").strip())
        app.state.repository = repository or OracleRepository(app.state.settings)
        app.state.work_lock = Lock()
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
                  docs_url=None, redoc_url=None, openapi_url=None)
    bearer = HTTPBearer(auto_error=False)

    def authenticate(credentials: HTTPAuthorizationCredentials | None = Depends(bearer)):
        from .api_security import unauthorized
        if credentials is None or credentials.scheme.lower() != "bearer":
            raise unauthorized()
        return app.state.verifier.verify(credentials.credentials)

    @app.exception_handler(Exception)
    async def unexpected_error(request, exc):
        # Do not return or log SQL, credentials, tokens or financial input.
        log.error("Recommendation dependency failure: %s", type(exc).__name__)
        return JSONResponse(status_code=503, content={"detail": "Database or dependency unavailable. Check service configuration and read grants."})

    @app.get("/health")
    def health():
        return {"status": "UP", "service": "recommendation-service"}

    @app.post("/api/recommendations")
    def recommend(body: RecommendationRequest, identity=Depends(authenticate)):
        user_id, role = identity
        try:
            app.state.access.authorize(user_id, role, body.msme_id)
        except HTTPException:
            raise
        except Exception:
            raise HTTPException(503, "Account/assignment lookup unavailable. Check database configuration and read grants.") from None
        if not app.state.work_lock.acquire(blocking=False):
            raise HTTPException(429, "An analysis is already running. Retry after it completes.",
                                headers={"Retry-After": "10"})
        try:
            period = date.fromisoformat(body.as_of + "-01")
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
            tools = MetricTools(data, period)
            try:
                if body.metrics_only:
                    result = {"status": "metrics_only", "evidence": {
                        name: tools.execute(name) for name in TOOL_DESCRIPTIONS}}
                else:
                    result = RecommendationAgent(tools, OllamaClient(app.state.settings)).run(body.question)
            except (ValueError, RuntimeError):
                raise HTTPException(502, "Recommendation generation or validation failed. Retry, or use metrics_only to inspect the evidence.") from None
            result.update(msme_id=body.msme_id, as_of_month=period.isoformat(), formula_version="1.0")
            return JSONResponse(json.loads(dumps(result)), headers={"Cache-Control": "no-store"})
        finally:
            app.state.work_lock.release()

    return app


app = create_app()
