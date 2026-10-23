from datetime import date, datetime
import json
import sys
import unittest
from unittest.mock import Mock, patch

from recommendation.config import Settings
from recommendation.database import OracleRepository


class RecommendationPersistenceTests(unittest.TestCase):
    def setUp(self):
        self.cursor = Mock()
        self.cursor.rowcount = 1
        self.connection = Mock()
        self.connection.__enter__ = Mock(return_value=self.connection)
        self.connection.__exit__ = Mock(return_value=False)
        self.connection.cursor.return_value.__enter__ = Mock(return_value=self.cursor)
        self.connection.cursor.return_value.__exit__ = Mock(return_value=False)
        self.driver = Mock()
        self.driver.connect.return_value = self.connection
        self.settings = Settings(user="test", password="test", dsn="test",
                                 profile_schema="PROFILE_OWNER", assessment_schema="ASSESSMENT_OWNER",
                                 warning_schema="WARNING_OWNER")
        self.repo = OracleRepository(self.settings)
        self.payload = json.dumps({"status": "generated", "recommendation": {
            "summary": "Review collections ₹", "actions": [{"suggested_action": "x" * 40000}]}})

    def save(self):
        with patch.dict(sys.modules, {"oracledb": self.driver}):
            self.repo.save_recommendation(7, date(2026, 9, 1), self.payload)

    def test_full_json_uses_clob_and_exact_business_month_then_commits(self):
        self.save()
        sql, binds = self.cursor.execute.call_args.args
        self.assertIn("UPDATE WARNING_OWNER.WARNING SET recommended_action = :payload", sql)
        self.assertIn("ASSESSMENT_OWNER.RISK_ASSESSMENT", sql)
        self.assertIn("PROFILE_OWNER.FINANCIAL_SNAPSHOT", sql)
        self.assertIn("s.msme_id = a.msme_id", sql)
        self.assertIn("FETCH FIRST 1 ROW ONLY", sql)
        self.assertIn("warning_status NOT IN ('CLOSED', 'CLOSED_REVIEWED')", sql)
        self.assertEqual(binds, {"payload": self.payload, "msme_id": 7,
                                 "start_at": datetime(2026, 9, 1), "end_at": datetime(2026, 10, 1)})
        self.cursor.setinputsizes.assert_called_once_with(payload=self.driver.DB_TYPE_CLOB)
        self.connection.commit.assert_called_once_with()
        self.connection.rollback.assert_not_called()

    def test_no_matching_open_warning_rolls_back(self):
        self.cursor.rowcount = 0
        with self.assertRaisesRegex(ValueError, "No open warning"):
            self.save()
        self.connection.commit.assert_not_called()
        self.connection.rollback.assert_called_once_with()

    def test_database_error_rolls_back(self):
        self.cursor.execute.side_effect = RuntimeError("write failed")
        with self.assertRaises(RuntimeError):
            self.save()
        self.connection.commit.assert_not_called()
        self.connection.rollback.assert_called_once_with()

    def test_default_schema_uses_current_owner_or_synonyms(self):
        self.repo = OracleRepository(Settings(user="test", password="test", dsn="test"))
        self.save()
        sql = self.cursor.execute.call_args.args[0]
        self.assertIn("UPDATE WARNING SET", sql)
        self.assertIn("JOIN RISK_ASSESSMENT", sql)
        self.assertIn("JOIN FINANCIAL_SNAPSHOT", sql)


    def test_lookup_reads_clob_before_connection_closes(self):
        lob = Mock()
        lob.read.return_value = self.payload
        self.cursor.fetchone.return_value = (138, "OPEN", lob)
        with patch.dict(sys.modules, {"oracledb": self.driver}):
            result = self.repo.recommendation_for_month(81, date(2026, 9, 1))
        self.assertEqual(result, {"warning_id": 138, "warning_status": "OPEN", "payload": self.payload})
        sql, binds = self.cursor.execute.call_args.args
        self.assertIn("WARNING_OWNER.WARNING", sql)
        self.assertIn("ORDER BY a.assessment_id DESC, w.warning_id DESC", sql)
        self.assertEqual(binds["msme_id"], 81)
        self.assertEqual(binds["start_at"], datetime(2026, 9, 1))
        self.assertEqual(binds["end_at"], datetime(2026, 10, 1))
        lob.read.assert_called_once_with()
        self.connection.rollback.assert_called_once_with()
        self.connection.commit.assert_not_called()

    def test_lookup_no_warning_returns_none(self):
        self.cursor.fetchone.return_value = None
        with patch.dict(sys.modules, {"oracledb": self.driver}):
            self.assertIsNone(self.repo.recommendation_for_month(5, date(2026, 9, 1)))
        self.connection.commit.assert_not_called()


if __name__ == "__main__":
    unittest.main()
