from datetime import date, datetime
from types import SimpleNamespace
from unittest import TestCase
from unittest.mock import Mock, patch
from recommendation.warning_store import WarningStore, UNAVAILABLE

class WarningStoreTests(TestCase):
    def setUp(self):
        self.settings = SimpleNamespace(user="unit", password="unit", dsn="unit", call_timeout_ms=1000,
                                        profile_schema="PROFILE", assessment_schema="ASSESSMENT")
        self.cursor = Mock()
        self.cursor.__enter__ = Mock(return_value=self.cursor)
        self.cursor.__exit__ = Mock(return_value=False)
        self.connection = Mock()
        self.connection.__enter__ = Mock(return_value=self.connection)
        self.connection.__exit__ = Mock(return_value=False)
        self.connection.cursor.return_value = self.cursor
        self.connect = patch("oracledb.connect", return_value=self.connection)
        self.connect.start(); self.addCleanup(self.connect.stop)
        with patch.dict("os.environ", {"ORACLE_WARNING_SCHEMA": "CASES"}):
            self.store = WarningStore(self.settings)

    def test_lookup_reads_exact_business_month_and_clob(self):
        payload = Mock(); payload.read.return_value = '{"status":"generated"}'
        self.cursor.fetchone.return_value = (1111, "OPEN", payload)
        result = self.store.get(1111, 5, date(2026,9,1))
        self.assertEqual(result["payload"], '{"status":"generated"}')
        sql, binds = self.cursor.execute.call_args.args
        self.assertIn("CASES.WARNING", sql)
        self.assertIn("w.warning_id=:warning_id", sql)
        self.assertEqual(binds["warning_id"], 1111); self.assertEqual(binds["msme_id"], 5)
        self.assertEqual(binds["start_at"], datetime(2026,9,1))
        self.assertEqual(binds["end_at"], datetime(2026,10,1))
        self.connection.rollback.assert_called_once(); self.connection.commit.assert_not_called()

    def test_conditional_clob_write_commits_exact_json_without_overwriting_success(self):
        import oracledb
        self.cursor.rowcount = 1
        payload = '{"status":"generated","recommendation":{"summary":"Advice","actions":[]}}'
        self.assertTrue(self.store.save_if_missing(1111,5,date(2026,9,1),payload))
        sql, binds = self.cursor.execute.call_args.args
        self.assertIn("DBMS_LOB.COMPARE", sql)
        self.assertIn("NOT IN ('CLOSED','CLOSED_REVIEWED')", sql)
        self.assertIn("w.warning_id=:warning_id", sql)
        self.assertEqual(binds["payload"], payload); self.assertEqual(binds["unavailable"], UNAVAILABLE)
        self.cursor.setinputsizes.assert_called_once_with(payload=oracledb.DB_TYPE_CLOB)
        self.connection.commit.assert_called_once()

    def test_conflicting_or_closed_warning_is_not_saved(self):
        self.cursor.rowcount = 0
        self.assertFalse(self.store.save_if_missing(1111,5,date(2026,9,1),"{}"))

    def test_save_error_rolls_back_and_is_propagated(self):
        self.cursor.execute.side_effect = RuntimeError("database unavailable")
        with self.assertRaises(RuntimeError): self.store.save_if_missing(1111,5,date(2026,9,1),"{}")
        self.connection.rollback.assert_called_once(); self.connection.commit.assert_not_called()
