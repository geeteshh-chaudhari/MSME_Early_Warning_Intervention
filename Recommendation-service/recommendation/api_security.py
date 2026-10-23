"""Validate existing Java-service tokens and current Oracle access assignments."""
import base64
from dataclasses import dataclass
import os
import re

import jwt
from fastapi import HTTPException


def unauthorized():
    return HTTPException(401, "Invalid or expired bearer token.",
                         headers={"WWW-Authenticate": "Bearer"})


@dataclass(frozen=True)
class TokenVerifier:
    key: bytes
    issuer: str

    @classmethod
    def from_environment(cls):
        try:
            key = base64.b64decode(os.environ.get("JWT_SECRET", ""), validate=True)
        except ValueError:
            raise ValueError("JWT_SECRET must be the Base64 signing secret used by msme-service.") from None
        issuer = os.environ.get("JWT_ISSUER", "msme-early-warning").strip()
        if len(key) < 32 or not issuer:
            raise ValueError("Set JWT_SECRET to the same Base64 secret as msme-service (at least 32 decoded bytes).")
        return cls(key, issuer)

    def verify(self, token):
        try:
            claims = jwt.decode(token, self.key, algorithms=["HS256"], issuer=self.issuer,
                                options={"require": ["exp", "iat", "iss", "sub", "role"]})
            subject = claims["sub"]
            if not isinstance(subject, str) or not re.fullmatch(r"[1-9][0-9]*", subject):
                raise ValueError("Invalid subject")
            if not isinstance(claims["role"], str):
                raise ValueError("Invalid role")
            return int(subject), claims["role"]
        except (jwt.InvalidTokenError, ValueError, TypeError):
            raise unauthorized() from None


class OracleAccess:
    def __init__(self, settings, user_schema="", allow_representatives=False, allow_inactive_reads=False):
        self.allow_representatives = allow_representatives
        self.allow_inactive_reads = allow_inactive_reads
        self.settings = settings
        if user_schema and not re.fullmatch(r"[A-Za-z][A-Za-z0-9_$#]*", user_schema):
            raise ValueError("ORACLE_USER_SCHEMA must be a simple Oracle identifier.")
        self.user_table = f"{user_schema}.APP_USER" if user_schema else "APP_USER"
        self.msme_table = (f"{settings.profile_schema}.MSME"
                           if settings.profile_schema else "MSME")

    @staticmethod
    def enforce(user, business, user_id, token_role, msme_id, allow_representatives=False, allow_inactive_reads=False):
        if not user or str(user[2]).upper() != "ACTIVE" or user[0] != token_role:
            raise unauthorized()
        if token_role not in ("RM", "RISK_ADMIN") and not (allow_representatives and token_role == "MSME_REP"):
            raise HTTPException(403, "Role cannot access recommendations.")
        # Conceal existence from users without an assignment.
        if not business:
            raise HTTPException(403, "MSME unavailable or access denied.")
        if token_role == "RM" and business[0] != user_id:
            raise HTTPException(403, "You are not assigned to this MSME.")
        if token_role == "MSME_REP" and user[1] != msme_id:
            raise HTTPException(403, "You cannot access another MSME.")
        if business[1] != "Y" and not (allow_inactive_reads and token_role in ("RM", "RISK_ADMIN")):
            raise HTTPException(403, "MSME is inactive.")

    def authorize(self, user_id, token_role, msme_id):
        import oracledb
        cfg = self.settings
        with oracledb.connect(user=cfg.user, password=cfg.password, dsn=cfg.dsn) as conn:
            conn.call_timeout = cfg.call_timeout_ms
            with conn.cursor() as cur:
                cur.execute("SET TRANSACTION READ ONLY")
                cur.execute(f"SELECT role, msme_id, account_status FROM {self.user_table} "
                            "WHERE user_id = :user_id", {"user_id": user_id})
                user = cur.fetchone()
                if not user or str(user[2]).upper() != "ACTIVE" or user[0] != token_role:
                    raise unauthorized()
                cur.execute(f"SELECT assigned_rm_id, is_active FROM {self.msme_table} "
                            "WHERE msme_id = :msme_id", {"msme_id": msme_id})
                self.enforce(user, cur.fetchone(), user_id, token_role, msme_id, self.allow_representatives, self.allow_inactive_reads)
            conn.rollback()
