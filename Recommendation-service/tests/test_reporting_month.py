"""Exercise the repository SELECTs against assessments created after their reporting months."""
from datetime import date
import sqlite3
import sys
from types import SimpleNamespace
import unittest
from unittest.mock import patch

from recommendation.database import OracleRepository
from recommendation.metrics import MetricTools


class ReportingMonthTests(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(":memory:")
        self.db.executescript("""
            CREATE TABLE MSME (msme_id INTEGER, business_name TEXT, sector TEXT,
                sanctioned_credit_limit INTEGER, is_active TEXT);
            CREATE TABLE FINANCIAL_SNAPSHOT (snapshot_id INTEGER, msme_id INTEGER,
                reporting_period TEXT, revenue INTEGER, total_receivables INTEGER,
                overdue_receivables INTEGER, credit_used INTEGER);
            CREATE TABLE THRESHOLD_CONFIG (threshold_config_id INTEGER, msme_id INTEGER,
                revenue_decline_pct INTEGER, overdue_receivable_pct INTEGER,
                credit_utilisation_pct INTEGER);
            CREATE TABLE RISK_ASSESSMENT (assessment_id INTEGER, msme_id INTEGER,
                snapshot_id INTEGER, threshold_config_id INTEGER, severity TEXT,
                stress_pattern TEXT, assessment_date TEXT);
            CREATE TABLE ASSESSMENT_INDICATOR_RESULT (assessment_id INTEGER,
                indicator_code TEXT, calculated_value INTEGER, threshold_value_used INTEGER,
                breached_flag TEXT);
            INSERT INTO MSME VALUES (7, 'Example', 'Manufacturing', 100, 'Y');
            INSERT INTO MSME VALUES (8, 'Other business', 'Manufacturing', 100, 'Y');
            INSERT INTO THRESHOLD_CONFIG VALUES (24, 7, 14, 32, 75);
            INSERT INTO THRESHOLD_CONFIG VALUES (25, 8, 14, 32, 75);
        """)
        for identifier, business, period in [(1, 7, '2026-04-01'), (2, 7, '2026-05-01'),
                                               (3, 7, '2026-06-01'), (4, 8, '2026-05-01')]:
            self.db.execute("INSERT INTO FINANCIAL_SNAPSHOT VALUES (?, ?, ?, 75, 100, 80, 82)",
                            (identifier, business, period + ' 00:00:00'))
            self.db.execute("INSERT INTO RISK_ASSESSMENT VALUES (?, ?, ?, ?, 'HIGH_PRIORITY', NULL, ?)",
                            (identifier, business, identifier, 24 if business == 7 else 25,
                             '2026-10-24 15:00:00'))
            for code, value, threshold in [('REVENUE_DETERIORATION', 25, 14),
                                           ('OVERDUE_RECEIVABLES', 80, 32),
                                           ('CREDIT_UTILISATION', 82, 75)]:
                self.db.execute("INSERT INTO ASSESSMENT_INDICATOR_RESULT VALUES (?, ?, ?, ?, 'Y')",
                                (identifier, code, value, threshold))
        self.db.commit()

    def tearDown(self):
        self.db.close()

    def load(self, period):
        db = self.db

        class Cursor:
            def __enter__(self):
                self.cursor = db.cursor()
                return self

            def __exit__(self, *args):
                self.cursor.close()

            def execute(self, sql, params=None):
                if sql == 'SET TRANSACTION READ ONLY':
                    return
                normalized = {key: value.isoformat(sep=' ') if hasattr(value, 'hour') else value
                              for key, value in (params or {}).items()}
                self.cursor.execute(sql, normalized)
                self.description = self.cursor.description

            def fetchall(self):
                return self.cursor.fetchall()

        class Connection:
            def __enter__(self): return self
            def __exit__(self, *args): pass
            def cursor(self): return Cursor()
            def rollback(self): db.rollback()

        settings = SimpleNamespace(user='test', password='test', dsn='test',
                                   profile_schema='', assessment_schema='', call_timeout_ms=1000)
        with patch.dict(sys.modules, {'oracledb': SimpleNamespace(connect=lambda **kwargs: Connection())}):
            return OracleRepository(settings).load(7, period)

    def test_later_run_assessments_and_flags_are_included_by_reporting_month(self):
        data = self.load(date(2026, 5, 1))
        self.assertEqual([row['assessment_id'] for row in data['assessments']], [1, 2])
        self.assertEqual(len(data['indicators']), 6)
        self.assertEqual({row['assessment_id'] for row in data['indicators']}, {1, 2})
        self.assertTrue(all(row['breached_flag'] == 'Y' for row in data['indicators']))

    def test_reporting_cutoff_still_excludes_later_months(self):
        data = self.load(date(2026, 4, 1))
        self.assertEqual([row['assessment_id'] for row in data['assessments']], [1])
        self.assertEqual({row['assessment_id'] for row in data['indicators']}, {1})

    def test_saved_context_and_breach_history_use_reporting_month(self):
        tools = MetricTools(self.load(date(2026, 5, 1)), date(2026, 5, 1))
        self.assertEqual(tools.business_context()['assessment']['assessment_id'], 2)
        persistence = tools.breach_persistence()
        self.assertEqual(persistence['revenue_deterioration_consecutive_breach_months']['value'], 2)
        self.assertEqual(persistence['revenue_deterioration_breach_frequency_12m_pct']['value'], 100)


if __name__ == '__main__':
    unittest.main()
