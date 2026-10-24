from dataclasses import dataclass
import os
from pathlib import Path
import re
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parent.parent


@dataclass(frozen=True)
class Settings:
    user: str
    password: str
    dsn: str
    profile_schema: str = ""
    assessment_schema: str = ""
    call_timeout_ms: int = 30000
    ollama_url: str = "http://localhost:11434"
    model: str = "qwen3:1.7b"
    ollama_timeout: int = 180

    @classmethod
    def load(cls):
        from dotenv import load_dotenv
        # Service-local values take precedence over shared repository-root defaults.
        load_dotenv(ROOT / ".env", override=False)
        load_dotenv(ROOT.parent / ".env", override=False)
        required = [os.getenv(k, "").strip() for k in
                    ("ORACLE_USER", "ORACLE_PASSWORD", "ORACLE_DSN")]
        if any(not x or "YOUR_" in x for x in required):
            raise ValueError("Fill ORACLE_USER, ORACLE_PASSWORD and ORACLE_DSN in the service or repository-root .env.")
        settings = cls(*required,
            profile_schema=os.getenv("ORACLE_PROFILE_SCHEMA", "").strip(),
            assessment_schema=os.getenv("ORACLE_ASSESSMENT_SCHEMA", "").strip(),
            call_timeout_ms=int(os.getenv("ORACLE_CALL_TIMEOUT_MS", "30000")),
            ollama_url=os.getenv("OLLAMA_BASE_URL", "http://localhost:11434").rstrip("/"),
            model=os.getenv("OLLAMA_MODEL", "qwen3:1.7b"),
            ollama_timeout=int(os.getenv("OLLAMA_TIMEOUT_SECONDS", "180")))
        for schema in (settings.profile_schema, settings.assessment_schema):
            if schema and not re.fullmatch(r"[A-Za-z][A-Za-z0-9_$#]*", schema):
                raise ValueError("Schema owners must be simple Oracle identifiers.")
        url = urlparse(settings.ollama_url)
        if (url.scheme != "http" or url.hostname not in ("localhost", "127.0.0.1", "::1")
                or url.username or url.password or url.path not in ("", "/")
                or url.query or url.fragment):
            raise ValueError("OLLAMA_BASE_URL must be a local HTTP Ollama address.")
        if settings.call_timeout_ms <= 0 or settings.ollama_timeout <= 0:
            raise ValueError("Timeouts must be positive.")
        return settings
