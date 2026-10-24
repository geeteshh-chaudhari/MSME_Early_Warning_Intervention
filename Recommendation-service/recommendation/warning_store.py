"""Warning persistence adapter, independent of the restored agent and financial tools."""
from datetime import datetime, time
import os
import re

from .metrics import shift_month

UNAVAILABLE = "reccommendation service not available"


class WarningStore:
    def __init__(self, settings):
        self.settings = settings
        self.warning = self._table(os.getenv("ORACLE_WARNING_SCHEMA", "").strip(), "WARNING")
        self.assessment = self._table(getattr(settings, "assessment_schema", ""), "RISK_ASSESSMENT")
        self.snapshot = self._table(getattr(settings, "profile_schema", ""), "FINANCIAL_SNAPSHOT")

    @staticmethod
    def _table(owner, name):
        if owner and not re.fullmatch(r"[A-Za-z][A-Za-z0-9_$#]*", owner):
            raise ValueError("Schema owners must be simple Oracle identifiers.")
        return f"{owner}.{name}" if owner else name

    @staticmethod
    def _binds(warning_id, msme_id, period):
        return {"warning_id": warning_id, "msme_id": msme_id,
                "start_at": datetime.combine(period, time.min),
                "end_at": datetime.combine(shift_month(period, 1), time.min)}

    def get(self, warning_id, msme_id, period):
        import oracledb
        cfg = self.settings
        with oracledb.connect(user=cfg.user, password=cfg.password, dsn=cfg.dsn) as conn:
            conn.call_timeout = cfg.call_timeout_ms
            with conn.cursor() as cur:
                cur.execute("SET TRANSACTION READ ONLY")
                cur.execute(f"""SELECT w.warning_id, w.warning_status, w.recommended_action
                    FROM {self.warning} w
                    JOIN {self.assessment} a ON a.assessment_id=w.assessment_id
                    JOIN {self.snapshot} s ON s.snapshot_id=a.snapshot_id AND s.msme_id=a.msme_id
                    WHERE w.warning_id=:warning_id AND a.msme_id=:msme_id
                      AND s.reporting_period>=:start_at AND s.reporting_period<:end_at""",
                    self._binds(warning_id, msme_id, period))
                row = cur.fetchone()
                if row:
                    payload = row[2].read() if hasattr(row[2], "read") else row[2]
                    result = {"warning_id": int(row[0]), "warning_status": row[1], "payload": payload}
                else:
                    result = None
            conn.rollback()
            return result

    def save_if_missing(self, warning_id, msme_id, period, payload):
        """Commit JSON only for this open warning while its result is still retryable."""
        import oracledb
        cfg = self.settings
        with oracledb.connect(user=cfg.user, password=cfg.password, dsn=cfg.dsn) as conn:
            conn.call_timeout = cfg.call_timeout_ms
            try:
                with conn.cursor() as cur:
                    cur.setinputsizes(payload=oracledb.DB_TYPE_CLOB)
                    cur.execute(f"""UPDATE {self.warning} w SET recommended_action=:payload
                        WHERE w.warning_id=:warning_id AND w.warning_status NOT IN ('CLOSED','CLOSED_REVIEWED')
                          AND (w.recommended_action IS NULL OR DBMS_LOB.GETLENGTH(w.recommended_action)=0
                               OR DBMS_LOB.COMPARE(w.recommended_action,TO_CLOB(:unavailable))=0)
                          AND EXISTS (SELECT 1 FROM {self.assessment} a
                              JOIN {self.snapshot} s ON s.snapshot_id=a.snapshot_id AND s.msme_id=a.msme_id
                              WHERE a.assessment_id=w.assessment_id AND a.msme_id=:msme_id
                                AND s.reporting_period>=:start_at AND s.reporting_period<:end_at)""",
                        dict(self._binds(warning_id, msme_id, period), payload=payload, unavailable=UNAVAILABLE))
                    saved = cur.rowcount == 1
                conn.commit()
                return saved
            except Exception:
                conn.rollback()
                raise
