from contextlib import asynccontextmanager
from datetime import date
import json
import os
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import urlparse
from urllib.request import Request, ProxyHandler, build_opener

from dotenv import load_dotenv
from fastapi import Depends, FastAPI, HTTPException, Query
from fastapi.responses import FileResponse, JSONResponse
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, ConfigDict, Field

from . import ROOT
from .report import build_report
from recommendation.agent import dumps
from recommendation.api_security import OracleAccess, TokenVerifier, unauthorized
from recommendation.config import Settings
from recommendation.database import OracleRepository

STATIC = ROOT / "static"


class Login(BaseModel):
    model_config = ConfigDict(extra="forbid")
    email: str = Field(min_length=3, max_length=150)
    password: str = Field(min_length=1, max_length=255)


def login_to_msme(body, url):
    # Fixed server configuration, never a URL supplied by a browser request.
    parsed = urlparse(url)
    if parsed.scheme != "http" or parsed.hostname not in {"localhost", "127.0.0.1", "::1"} or parsed.username or parsed.password:
        raise HTTPException(503, "AUTH_LOGIN_URL must be a local HTTP login endpoint.")
    request = Request(url, data=body.model_dump_json().encode(), headers={"Content-Type": "application/json"})
    try:
        with build_opener(ProxyHandler({})).open(request, timeout=15) as response:
            payload = json.load(response)
        token = payload.get("accessToken") if isinstance(payload, dict) else None
        if not isinstance(token, str) or not token:
            raise HTTPException(502, "Login service returned an unexpected response.")
        return {"access_token": token}
    except HTTPError as ex:
        if ex.code in (400, 401, 403):
            raise HTTPException(401, "Login failed. Check your credentials and account status.") from None
        raise HTTPException(502, "Login service failed.") from None
    except (URLError, TimeoutError, ValueError, OSError):
        raise HTTPException(503, "Login service unavailable. Start msme-service on port 8082.") from None


def create_app(settings=None, verifier=None, access=None, repository=None, discovery=True):
    @asynccontextmanager
    async def lifespan(app):
        # Load local overrides first, then shared Settings.load uses recommendation .env.
        load_dotenv(ROOT / ".env", override=False)
        app.state.settings = settings or Settings.load()
        app.state.verifier = verifier or TokenVerifier.from_environment()
        app.state.access = access or OracleAccess(app.state.settings, os.getenv("ORACLE_USER_SCHEMA", "").strip())
        app.state.repository = repository or OracleRepository(app.state.settings)
        client = None
        if discovery:
            from py_eureka_client.eureka_client import EurekaClient
            client = EurekaClient(
                eureka_server=os.getenv("EUREKA_SERVER_URL", "http://localhost:8080/eureka/"),
                app_name="dashboard-service",
                instance_host=os.getenv("DASHBOARD_HOST", "localhost"),
                instance_port=int(os.getenv("DASHBOARD_PORT", "8086")),
                home_page_url="/", status_page_url="/", health_check_url="/health",
                should_discover=False,
            )
        try:
            if client:
                await client.start()
            yield
        finally:
            if client:
                await client.stop()

    app = FastAPI(title="MSME Financial Dashboard", lifespan=lifespan,
                  version="1.0.0", docs_url=None, redoc_url=None, openapi_url="/openapi.json",
                  servers=[{"url": "/", "description": "Current origin / API Gateway"}],
                  root_path_in_servers=False)
    bearer = HTTPBearer(auto_error=False, scheme_name="bearerAuth",
                        description="MSME login accessToken. Paste the token without a Bearer prefix.")

    @app.middleware("http")
    async def response_policy(request, call_next):
        response = await call_next(request)
        response.headers["Cache-Control"] = "no-store"
        response.headers["X-Content-Type-Options"] = "nosniff"
        response.headers["Referrer-Policy"] = "no-referrer"
        response.headers["Content-Security-Policy"] = "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'"
        return response

    def authenticate(credentials: HTTPAuthorizationCredentials | None = Depends(bearer)):
        if not credentials or credentials.scheme.lower() != "bearer":
            raise unauthorized()
        return app.state.verifier.verify(credentials.credentials)

    @app.get("/", include_in_schema=False)
    def index():
        return FileResponse(STATIC / "index.html")

    @app.get("/health", include_in_schema=False)
    def health():
        return {"status": "UP", "service": "dashboard-service"}

    @app.post("/api/dashboard/login", tags=["Dashboard login"],
              summary="Sign in using the MSME identity service",
              description="Public login proxy. Returns access_token; protected report requests use this value as a Bearer token.",
              responses={200: {'description': 'JWT issued by MSME service',
       'content': {'application/json': {'schema': {'type': 'object',
                                                   'required': ['access_token'],
                                                   'properties': {'access_token': {'type': 'string'}}}}}},
 401: {'description': 'Credentials rejected or current token/account invalid',
       'content': {'application/json': {'schema': {'type': 'object',
                                                   'properties': {'detail': {'type': 'string'}}}}}},
 502: {'description': 'Upstream login failed or returned an unexpected payload',
       'content': {'application/json': {'schema': {'type': 'object',
                                                   'properties': {'detail': {'type': 'string'}}}}}},
 503: {'description': 'Login dependency unavailable or misconfigured',
       'content': {'application/json': {'schema': {'type': 'object',
                                                   'properties': {'detail': {'type': 'string'}}}}}}})
    def login(body: Login):
        result = login_to_msme(body, os.getenv("AUTH_LOGIN_URL", "http://localhost:8082/api/auth/login"))
        app.state.verifier.verify(result["access_token"])
        return JSONResponse(result)

    @app.get("/api/dashboard/msmes/{msme_id}", tags=["Financial analytics"],
             summary="Get a monthly MSME financial report",
             description="Risk Admin: any active business; RM: assigned business; representative: linked business. Uses the same deterministic tools as recommendations, without calling the recommendation API or Ollama.",
             responses={200: {'description': 'Forty-eight monthly rows including gaps, all twenty-one shared metrics, '
                      'saved indicators and limitations. No LLM call.',
       'content': {'application/json': {'schema': {'type': 'object',
                                                   'required': ['msme_id',
                                                                'as_of',
                                                                'formula_version',
                                                                'context',
                                                                'credit_limit',
                                                                'evidence',
                                                                'series',
                                                                'saved_indicators',
                                                                'notes'],
                                                   'properties': {'msme_id': {'type': 'integer'},
                                                                  'as_of': {'type': 'string',
                                                                            'format': 'date'},
                                                                  'formula_version': {'type': 'string'},
                                                                  'context': {'type': 'object'},
                                                                  'credit_limit': {'type': 'number'},
                                                                  'evidence': {'type': 'object'},
                                                                  'series': {'type': 'array',
                                                                             'items': {'type': 'object',
                                                                                       'properties': {'month': {'type': 'string',
                                                                                                                'format': 'date'},
                                                                                                      'available': {'type': 'boolean'},
                                                                                                      'snapshot': {'type': ['object',
                                                                                                                            'null']},
                                                                                                      'metrics': {'type': 'object'}}}},
                                                                  'saved_indicators': {'type': 'array',
                                                                                       'items': {'type': 'object'}},
                                                                  'notes': {'type': 'array',
                                                                            'items': {'type': 'string'}}}}}}},
 400: {'description': 'Invalid business ID, month or incomplete reporting period',
       'content': {'application/json': {'schema': {'type': 'object',
                                                   'properties': {'detail': {'type': 'string'}}}}}},
 401: {'description': 'Missing, invalid or expired JWT / inactive current account',
       'content': {'application/json': {'schema': {'type': 'object',
                                                   'properties': {'detail': {'type': 'string'}}}}}},
 403: {'description': 'Business access denied or MSME inactive',
       'content': {'application/json': {'schema': {'type': 'object',
                                                   'properties': {'detail': {'type': 'string'}}}}}},
 404: {'description': 'No financial snapshot for requested month',
       'content': {'application/json': {'schema': {'type': 'object',
                                                   'properties': {'detail': {'type': 'string'}}}}}},
 503: {'description': 'Financial history or account/dependency lookup unavailable',
       'content': {'application/json': {'schema': {'type': 'object',
                                                   'properties': {'detail': {'type': 'string'}}}}}}})
    def report(msme_id: int, as_of: str = Query(pattern=r"^\d{4}-\d{2}$"), identity=Depends(authenticate)):
        if msme_id <= 0:
            raise HTTPException(422, "MSME ID must be positive.")
        try:
            period = date.fromisoformat(as_of + "-01")
            if period >= date.today().replace(day=1):
                raise ValueError()
        except ValueError:
            raise HTTPException(422, "Choose a valid completed reporting month, YYYY-MM.") from None
        try:
            app.state.access.authorize(*identity, msme_id)
            data = app.state.repository.load(msme_id, period)
        except HTTPException:
            raise
        except Exception:
            raise HTTPException(503, "Database lookup unavailable. Check configuration and read grants.") from None
        try:
            payload = build_report(data, period)
            return JSONResponse(json.loads(dumps(payload)))
        except LookupError:
            raise HTTPException(404, "No snapshot exists for the selected reporting month.") from None
        except (ValueError, ArithmeticError):
            raise HTTPException(422, "Financial records cannot be analysed. Check duplicate months, saved indicators and financial values.") from None

    app.mount("/static", StaticFiles(directory=STATIC), name="static")
    return app


app = create_app()
