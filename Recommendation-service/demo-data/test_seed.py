"""Validate the generated dataset against the supplied DDL in an in-memory DB.

This validates relational/data rules, not Oracle PL/SQL compilation or a live DB.
"""
from datetime import datetime
from decimal import Decimal
from pathlib import Path
import re
import sqlite3
import unittest

from generate_seed import (build_data, render_sql, TABLES, IDS, MONTHS, START,
                           month_add, rounded, PASSWORD_HASH, counts_sql)

DDL = Path(r"C:\Users\Geetesh Chaudhari\Desktop\MSME_Early_Warning_Intervention\Profile_Finance_DDL.sql")


def sqlite_fixture(data, load=True):
    conn = sqlite3.connect(":memory:")
    conn.execute("PRAGMA foreign_keys=ON")
    conn.create_function("TRUNC", 2, lambda value, unit: str(value)[:7]+"-01")
    ddl = DDL.read_text(encoding="utf-8")
    for statement in re.findall(r"CREATE TABLE\s+\w+\s*\([\s\S]*?\n\);", ddl, re.I):
        statement = re.sub(r"NUMBER\s+GENERATED ALWAYS AS IDENTITY\s+PRIMARY KEY", "INTEGER PRIMARY KEY", statement, flags=re.I)
        statement = statement.replace("DEFAULT SYSTIMESTAMP", "DEFAULT CURRENT_TIMESTAMP")
        statement = statement.replace("metadata_json IS JSON", "metadata_json IS NULL OR json_valid(metadata_json)")
        if re.match(r"CREATE TABLE app_user\b", statement, re.I):
            statement = statement[:-3]+",\nFOREIGN KEY (msme_id) REFERENCES msme(msme_id)\n);"
        if re.match(r"CREATE TABLE intervention\b", statement, re.I):
            statement = statement.replace("    CONSTRAINT chk_intervention_active", "    follow_up_date DATE,\n\n    CONSTRAINT chk_intervention_active")
        conn.execute(statement)
    if not load:
        return conn
    def insert(table, row):
        columns = [k for k in row if not k.startswith("_")]
        values = [str(row[k]) if isinstance(row[k], Decimal) else row[k].isoformat(sep=" ")
                  if isinstance(row[k], datetime) else row[k].isoformat()
                  if hasattr(row[k], "isoformat") else row[k] for k in columns]
        conn.execute(f"INSERT INTO {table} ({', '.join(columns)}) VALUES ({', '.join('?' for _ in columns)})", values)
    for row in data.rows["app_user"]:
        if row["msme_id"] is None:
            insert("app_user", row)
    for row in data.rows["msme"]:
        insert("msme", row)
    for row in data.rows["app_user"]:
        if row["msme_id"] is not None:
            insert("app_user", row)
    for table in TABLES[2:]:
        for row in data.rows[table]:
            insert(table, row)
    return conn


class SeedTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.data = build_data()
        cls.conn = sqlite_fixture(cls.data)
        cls.by_id = {table: {r[IDS[table]]: r for r in rows} for table, rows in cls.data.rows.items()}

    @classmethod
    def tearDownClass(cls):
        cls.conn.close()

    def test_every_table_has_at_least_fifty_new_rows(self):
        for table, rows in self.data.rows.items():
            with self.subTest(table=table):
                self.assertGreaterEqual(len(rows), 50)
                self.assertEqual(self.conn.execute(f"SELECT COUNT(*) FROM {table}").fetchone()[0], len(rows))
        self.assertEqual(self.conn.execute("PRAGMA foreign_key_check").fetchall(), [])

    def test_four_year_monthly_contiguity_per_business(self):
        for business in self.data.rows["msme"]:
            snapshots = [r for r in self.data.rows["financial_snapshot"] if r["msme_id"] == business["msme_id"]]
            self.assertEqual([r["reporting_period"] for r in snapshots], [month_add(START, i) for i in range(MONTHS)])
            assessments = [r for r in self.data.rows["risk_assessment"] if r["msme_id"] == business["msme_id"]]
            self.assertEqual(len(assessments), MONTHS)

    def test_assessment_chain_threshold_and_historical_timestamps(self):
        for a in self.data.rows["risk_assessment"]:
            s = self.by_id["financial_snapshot"][a["snapshot_id"]]
            t = self.by_id["threshold_config"][a["threshold_config_id"]]
            self.assertEqual(s["msme_id"], a["msme_id"])
            self.assertEqual(t["msme_id"], a["msme_id"])
            self.assertLessEqual(s["submitted_at"], a["assessment_date"])
            self.assertLessEqual(t["effective_from"], a["assessment_date"])
            self.assertEqual(a["assessment_date"].date().replace(day=1), s["reporting_period"])
            if a["previous_assessment_id"] is not None:
                prev = self.by_id["risk_assessment"][a["previous_assessment_id"]]
                self.assertEqual(prev["msme_id"], a["msme_id"])
                self.assertLess(prev["assessment_date"], a["assessment_date"])

    def test_indicators_match_financial_data_and_thresholds(self):
        indicators = {}
        for i in self.data.rows["assessment_indicator_result"]:
            a = self.by_id["risk_assessment"][i["assessment_id"]]
            s = self.by_id["financial_snapshot"][a["snapshot_id"]]
            m = self.by_id["msme"][a["msme_id"]]
            code = i["indicator_code"]
            indicators.setdefault(a["assessment_id"], []).append(i)
            if code == "OVERDUE_RECEIVABLES":
                expected = rounded(s["overdue_receivables"]/s["total_receivables"]*100, 4)
            elif code == "CREDIT_UTILISATION":
                expected = rounded(s["credit_used"]/m["sanctioned_credit_limit"]*100, 4)
            elif a["previous_assessment_id"]:
                prev_a = self.by_id["risk_assessment"][a["previous_assessment_id"]]
                prev_s = self.by_id["financial_snapshot"][prev_a["snapshot_id"]]
                expected = rounded((prev_s["revenue"]-s["revenue"])/prev_s["revenue"]*100, 4)
            else:
                expected = None
            self.assertEqual(i["calculated_value"], expected)
            self.assertEqual(i["breached_flag"], "Y" if expected is not None and expected > i["threshold_value_used"] else "N")
        for a in self.data.rows["risk_assessment"]:
            self.assertEqual(len(indicators[a["assessment_id"]]), 3)
            breaches = sum(r["breached_flag"] == "Y" for r in indicators[a["assessment_id"]])
            self.assertEqual(a["severity"], ("HEALTHY", "WATCH", "EARLY_STRESS", "HIGH_PRIORITY")[breaches])

    def test_followups_have_later_months_and_correct_ownership(self):
        last_period = {}
        outcomes = set()
        for f in self.data.rows["follow_up_review"]:
            intervention = self.by_id["intervention"][f["intervention_id"]]
            warning = self.by_id["warning"][intervention["warning_id"]]
            baseline = self.by_id["risk_assessment"][warning["assessment_id"]]
            current = self.by_id["risk_assessment"][f["assessment_id"]]
            period = self.by_id["financial_snapshot"][current["snapshot_id"]]["reporting_period"]
            base_period = self.by_id["financial_snapshot"][baseline["snapshot_id"]]["reporting_period"]
            business = self.by_id["msme"][current["msme_id"]]
            self.assertEqual(baseline["msme_id"], current["msme_id"])
            self.assertGreater(period, base_period)
            self.assertGreater(period, intervention["created_at"].date().replace(day=1))
            if intervention["intervention_id"] in last_period:
                self.assertGreater(period, last_period[intervention["intervention_id"]])
            last_period[intervention["intervention_id"]] = period
            self.assertEqual(f["reviewed_by_user_id"], business["assigned_rm_id"])
            self.assertLessEqual(current["assessment_date"], f["reviewed_at"])
            if intervention["closed_at"]:
                self.assertLessEqual(f["reviewed_at"], intervention["closed_at"])
            if f["keep_intervention_active"] == "N":
                self.assertIn(f["outcome"], ("IMPROVING", "STABILISING"))
            outcomes.add(f["outcome"])
        self.assertEqual(outcomes, {"IMPROVING", "STABILISING", "STILL_STRESSED", "ESCALATED"})

    def test_closed_cases_and_active_interventions_are_consistent(self):
        for w in self.data.rows["warning"]:
            children = [r for r in self.data.rows["intervention"] if r["warning_id"] == w["warning_id"]]
            active = [r for r in children if r["is_active"] == "Y"]
            if w["warning_status"] == "CLOSED":
                self.assertIsNotNone(w["closed_at"])
                self.assertEqual(active, [])
            else:
                self.assertEqual(len(active), 1)
            for child in children:
                if child["is_active"] == "N":
                    self.assertIsNotNone(child["closed_at"])
                    self.assertIsNotNone(child["closed_by_user_id"])

    def test_time_based_workflow_tables_span_at_least_three_years(self):
        fields = {"threshold_config": "proposed_at", "financial_snapshot": "submitted_at",
            "risk_assessment": "assessment_date", "warning": "created_at",
            "intervention": "created_at", "follow_up_review": "reviewed_at", "activity_log": "created_at"}
        for table, field in fields.items():
            values = [r[field] for r in self.data.rows[table]]
            with self.subTest(table=table):
                self.assertGreaterEqual((max(values)-min(values)).days, 365*3)
                self.assertLess(max(values), datetime(2026, 10, 1))

    def test_sql_is_additive_and_does_not_insert_identity_values(self):
        sql = render_sql(self.data)
        self.assertNotRegex(sql.upper(), r"\b(DELETE FROM|TRUNCATE TABLE|DROP TABLE|ALTER TABLE)\b")
        for table, columns in re.findall(r"INSERT INTO (\w+) \(([^)]+)\)", sql):
            self.assertNotIn(IDS[table], [c.strip() for c in columns.split(",")])
        self.assertIn("WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK", sql)
        self.assertEqual(len(re.findall(r"^COMMIT;", sql, re.M)), 1)
        self.assertIn("RETURNING snapshot_id INTO v_snapshots", sql)
        self.assertEqual(len(PASSWORD_HASH), 60)

    def test_scoped_count_queries_match_generated_rows(self):
        for table, query in counts_sql():
            self.assertEqual(self.conn.execute(query).fetchone()[0], len(self.data.rows[table]))

    def test_rendered_inserts_work_with_existing_rows_and_shifted_identity_ids(self):
        # Execute the actual rendered INSERT values after resolving only PL/SQL
        # variables and literals. Deliberately shift generated identities so fixed
        # ID assumptions or wrong foreign-key maps fail independently of the model.
        conn = sqlite_fixture(self.data, load=False)
        conn.execute("INSERT INTO app_user (user_id,name,email,password_hash,role,account_status,created_at,updated_at) VALUES (50000,'Existing','existing@example.test','existing','RM','ACTIVE','2020-01-01','2020-01-01')")
        conn.execute("INSERT INTO msme (msme_id,business_name,sector,contact_name,sanctioned_credit_limit,assigned_rm_id,is_active,created_at,updated_at) VALUES (50000,'Existing business','Retail','Existing',100,50000,'Y','2020-01-01','2020-01-01')")
        env = {}
        token = re.compile(r"DECLARE\n  TYPE id_map[^\n]*|SELECT user_id INTO (v_\w+) FROM app_user WHERE email = '([^']+)';|INSERT INTO (\w+) \(([^)]+)\)\n    VALUES \((.*?)\)(?: RETURNING \w+ INTO ([^;\n]+))?;\n", re.S)
        def split_values(text):
            result, start, quoted, depth, index = [], 0, False, 0, 0
            while index < len(text):
                char = text[index]
                if char == "'":
                    if quoted and index+1 < len(text) and text[index+1] == "'":
                        index += 2
                        continue
                    quoted = not quoted
                elif not quoted:
                    depth += (char == "(") - (char == ")")
                    if char == "," and depth == 0:
                        result.append(text[start:index].strip())
                        start = index+1
                index += 1
            result.append(text[start:].strip())
            return result
        def decode(value):
            if value == "NULL": return None
            if value.startswith("v_"): return env[value]
            if value.startswith("DATE "): return value[6:-1]
            if value.startswith("TIMESTAMP "): return value[11:-1]
            if value.startswith("'"): return value[1:-1].replace("''", "'")
            return str(Decimal(value))
        for match in token.finditer(render_sql(self.data)):
            if match.group(0).startswith("DECLARE"):
                env.clear()
            elif match.group(1):
                env[match.group(1)] = conn.execute("SELECT user_id FROM app_user WHERE email=?", (match.group(2),)).fetchone()[0]
            else:
                table, columns, expressions, returning = match.group(3, 4, 5, 6)
                values = [decode(v) for v in split_values(expressions)]
                placeholders = ", ".join("?" for _ in values)
                cursor = conn.execute(f"INSERT INTO {table} ({columns}) VALUES ({placeholders})", values)
                if returning:
                    env[returning] = cursor.lastrowid
        self.assertEqual(conn.execute("PRAGMA foreign_key_check").fetchall(), [])
        for table, query in counts_sql():
            self.assertEqual(conn.execute(query).fetchone()[0], len(self.data.rows[table]))
        self.assertEqual(conn.execute("SELECT business_name FROM msme WHERE msme_id=50000").fetchone()[0], "Existing business")
        conn.close()


if __name__ == "__main__":
    unittest.main()
