"""Consistent financial reads and transactional recommendation persistence."""
from datetime import datetime, time
from decimal import Decimal
from .metrics import shift_month


class OracleRepository:
    def __init__(self, settings):
        self.settings = settings

    def recommendation_for_warning(self, warning_id, msme_id, as_of):
        """Resolve exactly the selected warning; never silently choose another case."""
        import oracledb
        cfg = self.settings
        def table(owner, name):
            return f"{owner}.{name}" if owner else name
        with oracledb.connect(user=cfg.user, password=cfg.password, dsn=cfg.dsn) as conn:
            conn.call_timeout = cfg.call_timeout_ms
            with conn.cursor() as cur:
                cur.execute("SET TRANSACTION READ ONLY")
                cur.execute(f"""SELECT w.warning_id, w.warning_status, w.recommended_action
                    FROM {table(cfg.warning_schema, 'WARNING')} w
                    JOIN {table(cfg.assessment_schema, 'RISK_ASSESSMENT')} a ON a.assessment_id=w.assessment_id
                    JOIN {table(cfg.profile_schema, 'FINANCIAL_SNAPSHOT')} s
                        ON s.snapshot_id=a.snapshot_id AND s.msme_id=a.msme_id
                    WHERE w.warning_id=:warning_id AND a.msme_id=:msme_id
                        AND s.reporting_period>=:start_at AND s.reporting_period<:end_at""",
                    {"warning_id": warning_id, "msme_id": msme_id,
                     "start_at": datetime.combine(as_of, time.min),
                     "end_at": datetime.combine(shift_month(as_of, 1), time.min)})
                row = cur.fetchone()
                result = None
                if row:
                    payload = row[2].read() if hasattr(row[2], "read") else row[2]
                    result = {"warning_id": int(row[0]), "warning_status": row[1], "payload": payload}
            conn.rollback()
        return result

    def save_recommendation_for_warning(self, warning_id, msme_id, as_of, payload):
        """Atomic save-if-empty for the exact open warning. Never overwrite history."""
        import oracledb
        cfg = self.settings
        def table(owner, name):
            return f"{owner}.{name}" if owner else name
        with oracledb.connect(user=cfg.user, password=cfg.password, dsn=cfg.dsn) as conn:
            conn.call_timeout = cfg.call_timeout_ms
            try:
                with conn.cursor() as cur:
                    cur.setinputsizes(payload=oracledb.DB_TYPE_CLOB)
                    cur.execute(f"""UPDATE {table(cfg.warning_schema, 'WARNING')} w
                        SET recommended_action=:payload
                        WHERE w.warning_id=:warning_id
                            AND w.warning_status NOT IN ('CLOSED','CLOSED_REVIEWED')
                            AND (w.recommended_action IS NULL OR DBMS_LOB.GETLENGTH(w.recommended_action)=0
                                OR DBMS_LOB.COMPARE(w.recommended_action, TO_CLOB(:unavailable))=0)
                            AND EXISTS (
                                SELECT 1 FROM {table(cfg.assessment_schema, 'RISK_ASSESSMENT')} a
                                JOIN {table(cfg.profile_schema, 'FINANCIAL_SNAPSHOT')} s
                                    ON s.snapshot_id=a.snapshot_id AND s.msme_id=a.msme_id
                                WHERE a.assessment_id=w.assessment_id AND a.msme_id=:msme_id
                                    AND s.reporting_period>=:start_at AND s.reporting_period<:end_at)""",
                        {"warning_id": warning_id, "msme_id": msme_id, "payload": payload,
                         "unavailable": "reccommendation service not available",
                         "start_at": datetime.combine(as_of, time.min),
                         "end_at": datetime.combine(shift_month(as_of, 1), time.min)})
                    saved = cur.rowcount == 1
                conn.commit()
                return saved
            except Exception:
                conn.rollback()
                raise

    def recommendation_for_month(self, msme_id, as_of):
        """Read the same warning targeted by save_recommendation, including its CLOB."""
        import oracledb
        cfg = self.settings
        def table(owner, name):
            return f"{owner}.{name}" if owner else name

        with oracledb.connect(user=cfg.user, password=cfg.password, dsn=cfg.dsn) as conn:
            conn.call_timeout = cfg.call_timeout_ms
            with conn.cursor() as cur:
                cur.execute("SET TRANSACTION READ ONLY")
                cur.execute(f"""SELECT w.warning_id, w.warning_status, w.recommended_action
                    FROM {table(cfg.warning_schema, 'WARNING')} w
                    JOIN {table(cfg.assessment_schema, 'RISK_ASSESSMENT')} a
                        ON a.assessment_id = w.assessment_id
                    JOIN {table(cfg.profile_schema, 'FINANCIAL_SNAPSHOT')} s
                        ON s.snapshot_id = a.snapshot_id AND s.msme_id = a.msme_id
                    WHERE a.msme_id = :msme_id
                        AND s.reporting_period >= :start_at AND s.reporting_period < :end_at
                    ORDER BY a.assessment_id DESC, w.warning_id DESC
                    FETCH FIRST 1 ROW ONLY""",
                    {"msme_id": msme_id, "start_at": datetime.combine(as_of, time.min),
                     "end_at": datetime.combine(shift_month(as_of, 1), time.min)})
                row = cur.fetchone()
                if row:
                    payload = row[2].read() if hasattr(row[2], "read") else row[2]
                    result = {"warning_id": int(row[0]), "warning_status": row[1], "payload": payload}
                else:
                    result = None
            conn.rollback()
        return result

    def save_recommendation(self, msme_id, as_of, payload):
        """Save response JSON on the latest open warning for this exact month.

        Do not create warnings: those belong to the assessment workflow.
        A separate connection keeps writes out of the history read transaction.
        """
        import oracledb
        cfg = self.settings
        def table(owner, name):
            return f"{owner}.{name}" if owner else name

        warning = table(cfg.warning_schema, "WARNING")
        assessment = table(cfg.assessment_schema, "RISK_ASSESSMENT")
        snapshot = table(cfg.profile_schema, "FINANCIAL_SNAPSHOT")
        with oracledb.connect(user=cfg.user, password=cfg.password, dsn=cfg.dsn) as conn:
            conn.call_timeout = cfg.call_timeout_ms
            try:
                with conn.cursor() as cur:
                    cur.setinputsizes(payload=oracledb.DB_TYPE_CLOB)
                    cur.execute(f"""UPDATE {warning} SET recommended_action = :payload
                        WHERE warning_id = (
                            SELECT w.warning_id FROM {warning} w
                            JOIN {assessment} a ON a.assessment_id = w.assessment_id
                            JOIN {snapshot} s ON s.snapshot_id = a.snapshot_id
                                AND s.msme_id = a.msme_id
                            WHERE a.msme_id = :msme_id
                                AND s.reporting_period >= :start_at
                                AND s.reporting_period < :end_at
                            ORDER BY a.assessment_id DESC, w.warning_id DESC
                            FETCH FIRST 1 ROW ONLY
                        ) AND warning_status NOT IN ('CLOSED', 'CLOSED_REVIEWED')""",
                        {"payload": payload, "msme_id": msme_id,
                         "start_at": datetime.combine(as_of, time.min),
                         "end_at": datetime.combine(shift_month(as_of, 1), time.min)})
                    if cur.rowcount != 1:
                        raise ValueError("No open warning exists for the requested reporting month.")
                conn.commit()
            except Exception:
                conn.rollback()
                raise

    def load(self, msme_id, as_of):
        import oracledb
        cfg = self.settings
        def table(name, assessment=False):
            owner = cfg.assessment_schema if assessment else cfg.profile_schema
            return f"{owner}.{name}" if owner else name

        start = datetime.combine(shift_month(as_of, -47), time.min)
        end = datetime.combine(shift_month(as_of, 1), time.min)
        binds = {"msme_id": msme_id, "start_at": start, "end_at": end}
        with oracledb.connect(user=cfg.user, password=cfg.password, dsn=cfg.dsn) as conn:
            conn.call_timeout = cfg.call_timeout_ms
            with conn.cursor() as cur:
                def number_to_decimal(cursor, metadata):
                    if metadata.type_code == oracledb.DB_TYPE_NUMBER:
                        return cursor.var(Decimal, arraysize=cursor.arraysize)

                cur.outputtypehandler = number_to_decimal
                cur.execute("SET TRANSACTION READ ONLY")

                def rows(sql, params):
                    cur.execute(sql, params)
                    keys = [d[0].lower() for d in cur.description]
                    return [{key: int(value) if key.endswith("_id") and value is not None else value
                             for key, value in zip(keys, row)} for row in cur.fetchall()]

                profile = rows(f"""SELECT msme_id, business_name, sector,
                    sanctioned_credit_limit, is_active FROM {table('MSME')}
                    WHERE msme_id = :msme_id""", {"msme_id": msme_id})
                if not profile:
                    raise ValueError("MSME ID not found in the configured profile schema.")
                snapshots = rows(f"""SELECT snapshot_id, reporting_period, revenue,
                    total_receivables, overdue_receivables, credit_used
                    FROM {table('FINANCIAL_SNAPSHOT')}
                    WHERE msme_id = :msme_id AND reporting_period >= :start_at
                    AND reporting_period < :end_at ORDER BY reporting_period""", binds)
                # Assessment and threshold must belong to this business and snapshot.
                assessments = rows(f"""SELECT a.assessment_id, a.snapshot_id,
                    a.threshold_config_id, a.severity, a.stress_pattern,
                    s.reporting_period, t.revenue_decline_pct,
                    t.overdue_receivable_pct, t.credit_utilisation_pct
                    FROM {table('RISK_ASSESSMENT', True)} a
                    JOIN {table('FINANCIAL_SNAPSHOT')} s ON s.snapshot_id = a.snapshot_id
                    AND s.msme_id = a.msme_id
                    JOIN {table('THRESHOLD_CONFIG', True)} t
                    ON t.threshold_config_id = a.threshold_config_id AND t.msme_id = a.msme_id
                    WHERE a.msme_id = :msme_id AND s.reporting_period >= :start_at
                    AND s.reporting_period < :end_at
                    ORDER BY s.reporting_period, a.assessment_id""", binds)
                indicators = rows(f"""SELECT i.assessment_id, i.indicator_code,
                    i.calculated_value, i.threshold_value_used, i.breached_flag,
                    s.reporting_period FROM {table('ASSESSMENT_INDICATOR_RESULT', True)} i
                    JOIN {table('RISK_ASSESSMENT', True)} a ON a.assessment_id = i.assessment_id
                    JOIN {table('FINANCIAL_SNAPSHOT')} s ON s.snapshot_id = a.snapshot_id
                    AND s.msme_id = a.msme_id
                    WHERE a.msme_id = :msme_id AND s.reporting_period >= :start_at
                    AND s.reporting_period < :end_at
                    ORDER BY s.reporting_period, i.indicator_code""", binds)
            # No DML or commits, including on successful reads.
            conn.rollback()
        return {"profile": profile[0], "snapshots": snapshots,
                "assessments": assessments, "indicators": indicators}
