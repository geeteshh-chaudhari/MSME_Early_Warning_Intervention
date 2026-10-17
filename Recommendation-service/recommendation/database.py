"""Fixed SELECT queries, bound IDs/dates, one consistent read-only transaction."""
from datetime import datetime, time
from decimal import Decimal
from .metrics import shift_month


class OracleRepository:
    def __init__(self, settings):
        self.settings = settings

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
                    AND s.reporting_period < :end_at AND a.assessment_date < :end_at
                    ORDER BY s.reporting_period, a.assessment_id""", binds)
                indicators = rows(f"""SELECT i.assessment_id, i.indicator_code,
                    i.calculated_value, i.threshold_value_used, i.breached_flag,
                    s.reporting_period FROM {table('ASSESSMENT_INDICATOR_RESULT', True)} i
                    JOIN {table('RISK_ASSESSMENT', True)} a ON a.assessment_id = i.assessment_id
                    JOIN {table('FINANCIAL_SNAPSHOT')} s ON s.snapshot_id = a.snapshot_id
                    AND s.msme_id = a.msme_id
                    WHERE a.msme_id = :msme_id AND s.reporting_period >= :start_at
                    AND s.reporting_period < :end_at AND a.assessment_date < :end_at
                    ORDER BY s.reporting_period, i.indicator_code""", binds)
            # No DML or commits, including on successful reads.
            conn.rollback()
        return {"profile": profile[0], "snapshots": snapshots,
                "assessments": assessments, "indicators": indicators}
