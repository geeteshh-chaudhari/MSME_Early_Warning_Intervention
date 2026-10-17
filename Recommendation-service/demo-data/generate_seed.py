"""Generate an additive Oracle demo batch; never connects to a database.

Python 3.11+, standard library only. All numbers, companies and people are synthetic.
"""
from collections import Counter
from calendar import monthrange
from datetime import date, datetime, timedelta, time
from decimal import Decimal, ROUND_HALF_UP
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parent
BATCH = "SEED26"
START = date(2022, 10, 1)
MONTHS = 48
BUSINESSES = 50
# Verified with Spring Security BCrypt.checkpw; plaintext is SeedDemo@2026!.
PASSWORD_HASH = "$2a$12$mvaKEsCz6jNtb2ZaJkeN7OHIjapiR5bcFzJAGa.gF91.tN1u/Q57m"
TABLES = ("app_user", "msme", "threshold_config", "financial_snapshot",
          "risk_assessment", "assessment_indicator_result", "warning",
          "intervention", "follow_up_review", "activity_log")
IDS = dict(zip(TABLES, ("user_id", "msme_id", "threshold_config_id", "snapshot_id",
    "assessment_id", "indicator_result_id", "warning_id", "intervention_id",
    "followup_id", "activity_id")))
SCENARIOS = ("steady_growth", "seasonal", "declining", "collections_stress", "recovery")


def month_add(value, offset):
    year, month = divmod(value.year*12 + value.month-1 + offset, 12)
    return date(year, month+1, 1)


def rounded(value, places=2):
    return Decimal(str(value)).quantize(Decimal(10) ** -places, rounding=ROUND_HALF_UP)


def at_month_end(period, days_before=1, hour=9):
    last = period.replace(day=monthrange(period.year, period.month)[1])
    return datetime.combine(last-timedelta(days=days_before), time(hour))


class SeedData:
    def __init__(self):
        self.rows = {table: [] for table in TABLES}
        self.manifest = []

    def add(self, table, business=0, **values):
        row = {IDS[table]: len(self.rows[table])+1, **values, "_business": business}
        self.rows[table].append(row)
        return row

    def audit(self, business, actor, action, entity_type, entity_id, when, description,
              scenario, **metadata):
        return self.add("activity_log", business, msme_id=business,
            actor_user_id=actor, actor_type="USER" if actor else "SYSTEM",
            action_code=action, related_entity_type=entity_type,
            related_entity_id=entity_id, description=description, created_at=when,
            metadata_json=json.dumps({"batch": BATCH, "business_code": f"{BATCH}-{business:03}",
                "scenario": scenario, "synthetic": True, **metadata}, separators=(",", ":")))


def build_data():
    data = SeedData()
    created = datetime(2022, 9, 15, 9)
    for i in range(1, 11):
        data.add("app_user", name=f"Seed RM {i:02}", email=f"seed26.rm{i:02}@example.test",
            password_hash=PASSWORD_HASH, role="RM", msme_id=None,
            account_status="ACTIVE", created_at=created, updated_at=created)
    for i in range(1, 3):
        data.add("app_user", name=f"Seed Risk Admin {i:02}", email=f"seed26.admin{i:02}@example.test",
            password_hash=PASSWORD_HASH, role="RISK_ADMIN", msme_id=None,
            account_status="ACTIVE", created_at=created, updated_at=created)
    for business in range(1, BUSINESSES+1):
        rm, admin = 1+(business-1)%10, 11+(business-1)%2
        scenario_index = (business-1)%5
        scenario = SCENARIOS[scenario_index]
        limit = rounded(1500000 + business*75000)
        business_name = f"SEED26 - Demo Business {business:03}"
        data.add("msme", business, business_name=business_name,
            sector=("Manufacturing", "Retail", "Textiles", "Food Processing", "Logistics")[(business*3+business//5)%5],
            contact_name=f"Seed Contact {business:03}", sanctioned_credit_limit=limit,
            consent_status="GRANTED", assigned_rm_id=rm, is_active="Y",
            created_at=created+timedelta(days=1), updated_at=created+timedelta(days=1))
        rep = data.add("app_user", business, name=f"Seed Representative {business:03}",
            email=f"seed26.rep{business:03}@example.test", password_hash=PASSWORD_HASH,
            role="MSME_REP", msme_id=business, account_status="ACTIVE",
            created_at=created+timedelta(days=2), updated_at=created+timedelta(days=2))["user_id"]
        thresholds = []
        for year in range(4):
            effective = datetime.combine(month_add(START, year*12), time())
            thresholds.append(data.add("threshold_config", business, msme_id=business,
                version_no=year+1, revenue_decline_pct=Decimal(13+business%3),
                overdue_receivable_pct=Decimal(30+business%3*2),
                credit_utilisation_pct=Decimal(70+business%2*5), status="APPROVED",
                proposed_by_user_id=rm, proposed_at=effective-timedelta(days=10),
                reviewed_by_user_id=admin, reviewed_at=effective-timedelta(days=9),
                review_reason="Synthetic annual threshold version; demo policy only.",
                effective_from=effective))
        data.manifest.append({"business_code": f"{BATCH}-{business:03}", "business_name": business_name,
            "scenario": scenario, "rm_email": f"seed26.rm{rm:02}@example.test",
            "representative_email": f"seed26.rep{business:03}@example.test"})
        previous_revenue = previous_assessment = None
        active_warning = active_intervention = None
        previous_breach_count = 0
        for offset in range(MONTHS):
            period = month_add(START, offset)
            seasonal = math.cos(2*math.pi*(period.month-11)/12)
            noise = 1+0.015*math.sin(business*7+offset*2)
            if scenario_index == 0:
                trend, amplitude, overdue_ratio, utilisation = 1+0.003*offset, .04, .12, .45
            elif scenario_index == 1:
                trend, amplitude, overdue_ratio, utilisation = 1+0.001*offset, .36, .18, .58
            elif scenario_index == 2:
                trend, amplitude = 1-.009*offset, .06
                overdue_ratio, utilisation = .18+.004*offset, .50+.007*offset
                if offset%12 == 7:
                    trend *= .70
            elif scenario_index == 3:
                trend, amplitude = 1+.003*offset, .08
                overdue_ratio, utilisation = .42+.003*offset, .80+.003*offset
                if offset%12 == 7:
                    trend *= .70
                    utilisation = 1.05
            else:
                trend, amplitude = 1-.008*min(offset, 18)+.016*max(0, offset-18), .10
                overdue_ratio = max(.12, .48-.014*max(0, offset-12))
                utilisation = max(.45, .90-.020*max(0, offset-12))
            revenue = rounded((700000+business*27000)*trend*(1+amplitude*seasonal)*noise)
            receivables = rounded(revenue*Decimal(str(.50+.04*(business%4))))
            overdue = rounded(receivables*Decimal(str(overdue_ratio)))
            credit = rounded(limit*Decimal(str(utilisation)))
            submitted_at = at_month_end(period, 2, 8)
            assessed_at = at_month_end(period, 1, 9)
            snapshot = data.add("financial_snapshot", business, msme_id=business,
                reporting_period=period, revenue=revenue, total_receivables=receivables,
                overdue_receivables=overdue, credit_used=credit,
                submitted_by_user_id=rep, submitted_at=submitted_at,
                created_at=submitted_at, updated_at=submitted_at)
            threshold = thresholds[offset//12]
            decline = rounded((previous_revenue-revenue)/previous_revenue*100, 4) if previous_revenue else None
            overdue_pct = rounded(overdue/receivables*100, 4)
            credit_pct = rounded(credit/limit*100, 4)
            indicators = [
                ("REVENUE_DETERIORATION", decline, threshold["revenue_decline_pct"]),
                ("OVERDUE_RECEIVABLES", overdue_pct, threshold["overdue_receivable_pct"]),
                ("CREDIT_UTILISATION", credit_pct, threshold["credit_utilisation_pct"])]
            breached = [code for code, value, threshold_value in indicators if value is not None and value > threshold_value]
            count = len(breached)
            severity = ("HEALTHY", "WATCH", "EARLY_STRESS", "HIGH_PRIORITY")[count]
            assessment = data.add("risk_assessment", business, msme_id=business,
                snapshot_id=snapshot["snapshot_id"], threshold_config_id=threshold["threshold_config_id"],
                assessment_date=assessed_at, severity=severity,
                stress_pattern="MULTI_INDICATOR" if count>1 else breached[0] if count else None,
                previous_assessment_id=previous_assessment, created_at=assessed_at)
            assessment_id = assessment["assessment_id"]
            for code, value, threshold_value in indicators:
                data.add("assessment_indicator_result", business, assessment_id=assessment_id,
                    indicator_code=code, calculated_value=value, threshold_value_used=threshold_value,
                    breached_flag="Y" if code in breached else "N",
                    explanation_text=(f"Synthetic {code}; value={value}; threshold={threshold_value}; "
                        "breach uses >. Revenue decline uses previous calendar month; "
                        "first-month decline is unavailable. These are demo rules."))
            data.audit(business, rep, "SNAPSHOT_SUBMITTED", "FINANCIAL_SNAPSHOT", snapshot["snapshot_id"],
                submitted_at, "Synthetic monthly financial submission.", scenario, reporting_month=period.isoformat())
            data.audit(business, None, "ASSESSMENT_CREATED", "RISK_ASSESSMENT", assessment_id,
                assessed_at, "Synthetic assessment: severity based on number of breached indicators.", scenario,
                reporting_month=period.isoformat(), severity=severity)
            if active_warning is not None:
                review_time = assessed_at+timedelta(hours=3)
                closes = count == 0
                outcome = ("STABILISING" if closes else "ESCALATED" if count == 3
                           else "IMPROVING" if count < previous_breach_count else "STILL_STRESSED")
                review = data.add("follow_up_review", business,
                    intervention_id=active_intervention["intervention_id"], assessment_id=assessment_id,
                    reviewed_by_user_id=rm, outcome=outcome,
                    review_notes="Synthetic monthly RM review using this business's later assessment.",
                    reviewed_at=review_time, keep_intervention_active="N" if closes else "Y")
                data.audit(business, rm, "FOLLOW_UP_"+outcome, "FOLLOW_UP_REVIEW", review["followup_id"],
                    review_time, "Recorded synthetic follow-up review.", scenario, outcome=outcome)
                if closes:
                    active_warning["warning_status"] = "CLOSED"
                    active_warning["closed_at"] = review_time
                    active_intervention.update(is_active="N", closed_at=review_time, closed_by_user_id=rm)
                    data.audit(business, rm, "WARNING_CLOSED", "WARNING", active_warning["warning_id"],
                        review_time, "Closed after a stabilising review with no current indicator breaches.", scenario)
                    active_warning = active_intervention = None
                else:
                    active_intervention["follow_up_date"] = at_month_end(month_add(period, 1), 0, 0).date()
                    if outcome == "ESCALATED":
                        # Show the escalation and admin confirmation in history; monitoring resumes.
                        data.audit(business, admin, "ADMIN_CONFIRM", "WARNING", active_warning["warning_id"],
                            review_time+timedelta(minutes=10), "Risk Admin confirmed escalation; active monitoring resumes.", scenario,
                            previous_status="ESCALATED", next_status="INTERVENTION_ACTIVE")
                    elif outcome == "STILL_STRESSED" and offset%6 == 0:
                        revised_time = review_time+timedelta(minutes=20)
                        active_intervention.update(is_active="N", closed_at=revised_time, closed_by_user_id=rm)
                        active_intervention = data.add("intervention", business,
                            warning_id=active_warning["warning_id"], created_by_user_id=rm,
                            action_text="Revised demo plan: review collections, revenue drivers and facility usage.",
                            notes="Revision follows a STILL_STRESSED review; prior history is retained.",
                            is_active="Y", created_at=revised_time, closed_at=None, closed_by_user_id=None,
                            follow_up_date=at_month_end(month_add(period, 1), 0, 0).date())
                        data.audit(business, rm, "INTERVENTION_REVISED", "INTERVENTION", active_intervention["intervention_id"],
                            revised_time, "Revised active intervention after stressed monthly evidence.", scenario)
            elif breached:
                warning_time = assessed_at+timedelta(hours=1)
                primary = sorted(breached)[0]
                action = {"CREDIT_UTILISATION": "Review credit utilisation and agree a working-capital monitoring plan.",
                    "OVERDUE_RECEIVABLES": "Review overdue invoices and agree a collection schedule.",
                    "REVENUE_DETERIORATION": "Review revenue decline and agree a recovery plan."}[primary]
                active_warning = data.add("warning", business, assessment_id=assessment_id,
                    warning_status="INTERVENTION_ACTIVE", primary_driver=primary,
                    why_now_text=f"Synthetic {period:%Y-%m} warning: breached {', '.join(breached)}.",
                    recommended_action=action, created_at=warning_time, closed_at=None)
                data.audit(business, rm, "WARNING_CREATED", "WARNING", active_warning["warning_id"],
                    warning_time, "Created warning from non-healthy synthetic assessment.", scenario,
                    previous_status=None, next_status="OPEN")
                data.audit(business, rm, "WARNING_ACCEPTED", "WARNING", active_warning["warning_id"],
                    warning_time+timedelta(minutes=10), "RM accepted synthetic warning with an action plan.", scenario,
                    previous_status="OPEN", next_status="UNDER_REVIEW")
                active_intervention = data.add("intervention", business,
                    warning_id=active_warning["warning_id"], created_by_user_id=rm,
                    action_text=action, notes="Synthetic intervention owned by the assigned RM.",
                    is_active="Y", created_at=warning_time+timedelta(minutes=20),
                    closed_at=None, closed_by_user_id=None,
                    follow_up_date=at_month_end(month_add(period, 1), 0, 0).date())
                data.audit(business, rm, "INTERVENTION_CREATED", "INTERVENTION", active_intervention["intervention_id"],
                    active_intervention["created_at"], "Recorded synthetic action and follow-up date.", scenario,
                    previous_status="UNDER_REVIEW", next_status="INTERVENTION_ACTIVE")
            previous_revenue, previous_assessment, previous_breach_count = revenue, assessment_id, count
    return data


def literal(value):
    if value is None:
        return "NULL"
    if isinstance(value, datetime):
        return "TIMESTAMP '"+value.strftime("%Y-%m-%d %H:%M:%S")+"'"
    if isinstance(value, date):
        return "DATE '"+value.isoformat()+"'"
    if isinstance(value, (int, Decimal)):
        return str(value)
    return "'"+str(value).replace("'", "''")+"'"


ARRAYS = {"threshold_config": "v_thresholds", "financial_snapshot": "v_snapshots",
    "risk_assessment": "v_assessments", "assessment_indicator_result": "v_indicators",
    "warning": "v_warnings", "intervention": "v_interventions", "follow_up_review": "v_reviews"}
FK_TABLES = {"threshold_config_id": "threshold_config", "snapshot_id": "financial_snapshot",
    "assessment_id": "risk_assessment", "previous_assessment_id": "risk_assessment",
    "warning_id": "warning", "intervention_id": "intervention"}
USER_FKS = {"assigned_rm_id", "submitted_by_user_id", "proposed_by_user_id",
    "reviewed_by_user_id", "created_by_user_id", "closed_by_user_id", "actor_user_id"}


def insert_sql(table, row, business=0, rm=None, admin=None, rep=None):
    columns, values = [], []
    for column, value in row.items():
        if column in (IDS[table], "_business", "effective_from"):
            continue
        columns.append(column)
        if value is None:
            values.append("NULL")
        elif column == "msme_id" and business:
            if value != business:
                raise ValueError("Cross-business MSME reference.")
            values.append("v_msme")
        elif column in USER_FKS:
            mapping = {rm: "v_rm", admin: "v_admin", rep: "v_rep"}
            if value not in mapping:
                raise ValueError("Unknown user reference.")
            values.append(mapping[value])
        elif column in FK_TABLES:
            values.append(f"{ARRAYS[FK_TABLES[column]]}({value})")
        elif column == "related_entity_id":
            target = row["related_entity_type"].lower()
            values.append(f"{ARRAYS[target]}({value})")
        else:
            values.append(literal(value))
    sql = f"  INSERT INTO {table} ({', '.join(columns)})\n    VALUES ({', '.join(values)})"
    if table == "msme":
        sql += " RETURNING msme_id INTO v_msme"
    elif table == "app_user" and business:
        sql += " RETURNING user_id INTO v_rep"
    elif table in ARRAYS:
        sql += f" RETURNING {IDS[table]} INTO {ARRAYS[table]}({row[IDS[table]]})"
    return sql+";\n"


def counts_sql():
    parts = []
    for table in TABLES:
        if table == "app_user":
            condition = "email LIKE 'seed26.%@example.test'"
        elif table == "msme":
            condition = "business_name LIKE 'SEED26 - Demo Business %'"
        elif table in ("threshold_config", "financial_snapshot", "risk_assessment", "activity_log"):
            condition = "msme_id IN (SELECT msme_id FROM msme WHERE business_name LIKE 'SEED26 - Demo Business %')"
        elif table in ("assessment_indicator_result", "warning"):
            condition = "assessment_id IN (SELECT assessment_id FROM risk_assessment WHERE msme_id IN (SELECT msme_id FROM msme WHERE business_name LIKE 'SEED26 - Demo Business %'))"
        elif table == "intervention":
            condition = "warning_id IN (SELECT warning_id FROM warning WHERE assessment_id IN (SELECT assessment_id FROM risk_assessment WHERE msme_id IN (SELECT msme_id FROM msme WHERE business_name LIKE 'SEED26 - Demo Business %')))"
        else:
            condition = "intervention_id IN (SELECT intervention_id FROM intervention WHERE warning_id IN (SELECT warning_id FROM warning WHERE assessment_id IN (SELECT assessment_id FROM risk_assessment WHERE msme_id IN (SELECT msme_id FROM msme WHERE business_name LIKE 'SEED26 - Demo Business %'))))"
        parts.append((table, f"SELECT COUNT(*) FROM {table} WHERE {condition}"))
    return parts


def render_sql(data):
    output = ["""-- Additive SEED26 synthetic demo batch. Generated; do not edit IDs manually.
-- Run as the owner of all ten tables in a NEW, dedicated demo database session.
-- SQLcl/SQL*Plus: @seed_demo_48_months.sql
-- SQL Developer / Oracle SQL extension: Run Script, not a single-statement execution.
-- Inserts are committed only after the final count/relationship assertions pass.
-- Existing data is not updated or deleted. A repeated batch aborts before inserts.
SET DEFINE OFF
SET SERVEROUTPUT ON
SET AUTOCOMMIT OFF
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK

DECLARE
  v_count NUMBER;
BEGIN
  SELECT COUNT(*) INTO v_count FROM app_user WHERE email LIKE 'seed26.%@example.test';
  IF v_count > 0 THEN
    RAISE_APPLICATION_ERROR(-20001, 'SEED26 users already exist; batch not rerunnable. No rows inserted.');
  END IF;
  SELECT COUNT(*) INTO v_count FROM msme WHERE business_name LIKE 'SEED26 - Demo Business %';
  IF v_count > 0 THEN
    RAISE_APPLICATION_ERROR(-20002, 'SEED26 businesses already exist. No rows inserted.');
  END IF;
  SELECT COUNT(*) INTO v_count FROM user_tab_columns
    WHERE table_name = 'INTERVENTION' AND column_name = 'FOLLOW_UP_DATE';
  IF v_count <> 1 THEN
    RAISE_APPLICATION_ERROR(-20003, 'Required INTERVENTION.FOLLOW_UP_DATE column is missing.');
  END IF;
END;
/

BEGIN
"""]
    for row in data.rows["app_user"]:
        if row["_business"] == 0:
            output.append(insert_sql("app_user", row))
    output.append("END;\n/\n")
    for business in range(1, BUSINESSES+1):
        profile = data.rows["msme"][business-1]
        rm, admin = profile["assigned_rm_id"], 11+(business-1)%2
        rep_row = next(r for r in data.rows["app_user"] if r["_business"] == business)
        rep = rep_row["user_id"]
        output.append(f"\n-- {profile['business_name']} / {SCENARIOS[(business-1)%5]}\n")
        output.append("DECLARE\n  TYPE id_map IS TABLE OF NUMBER INDEX BY PLS_INTEGER;\n")
        for name in ARRAYS.values():
            output.append(f"  {name} id_map;\n")
        output.append("  v_msme NUMBER; v_rm NUMBER; v_admin NUMBER; v_rep NUMBER; v_has_effective NUMBER;\nBEGIN\n")
        output.append(f"  SELECT user_id INTO v_rm FROM app_user WHERE email = 'seed26.rm{rm:02}@example.test';\n")
        output.append(f"  SELECT user_id INTO v_admin FROM app_user WHERE email = 'seed26.admin{admin-10:02}@example.test';\n")
        output.append("  SELECT COUNT(*) INTO v_has_effective FROM user_tab_columns WHERE table_name = 'THRESHOLD_CONFIG' AND column_name = 'EFFECTIVE_FROM';\n")
        output.append(insert_sql("msme", profile, business, rm, admin, rep))
        output.append(insert_sql("app_user", rep_row, business, rm, admin, rep))
        for table in TABLES[2:]:
            for row in data.rows[table]:
                if row["_business"] != business:
                    continue
                output.append(insert_sql(table, row, business, rm, admin, rep))
                if table == "threshold_config":
                    output.append("  IF v_has_effective = 1 THEN\n")
                    output.append("    EXECUTE IMMEDIATE 'UPDATE threshold_config SET effective_from = :1 WHERE threshold_config_id = :2'\n")
                    output.append(f"      USING {literal(row['effective_from'])}, v_thresholds({row['threshold_config_id']});\n  END IF;\n")
        output.append(f"  DBMS_OUTPUT.PUT_LINE('Prepared SEED26-{business:03}; actual MSME_ID=' || TO_CHAR(v_msme));\nEND;\n/\n")
    output.append("\n-- Fail before commit if the batch is incomplete or relationships do not match.\nDECLARE\n  v_count NUMBER;\nBEGIN\n")
    for table, query in counts_sql():
        expected = len(data.rows[table])
        output.append(f"  {query.replace('SELECT COUNT(*)', 'SELECT COUNT(*) INTO v_count')};\n")
        output.append(f"  IF v_count <> {expected} THEN RAISE_APPLICATION_ERROR(-20010, 'Unexpected SEED26 row count for {table}'); END IF;\n")
        output.append(f"  DBMS_OUTPUT.PUT_LINE('{table}: ' || TO_CHAR(v_count));\n")
    output.append("""  SELECT COUNT(*) INTO v_count FROM financial_snapshot s
    JOIN msme m ON m.msme_id = s.msme_id
    WHERE m.business_name LIKE 'SEED26 - Demo Business %'
      AND s.reporting_period NOT BETWEEN DATE '2022-10-01' AND DATE '2026-09-01';
  IF v_count <> 0 THEN RAISE_APPLICATION_ERROR(-20011, 'Unexpected reporting period'); END IF;
  SELECT COUNT(*) INTO v_count FROM (
    SELECT m.msme_id FROM msme m LEFT JOIN financial_snapshot s ON s.msme_id = m.msme_id
    WHERE m.business_name LIKE 'SEED26 - Demo Business %'
    GROUP BY m.msme_id HAVING COUNT(s.snapshot_id) <> 48
      OR MIN(s.reporting_period) <> DATE '2022-10-01'
      OR MAX(s.reporting_period) <> DATE '2026-09-01');
  IF v_count <> 0 THEN RAISE_APPLICATION_ERROR(-20012, 'A business does not have 48 monthly snapshots'); END IF;
  SELECT COUNT(*) INTO v_count FROM risk_assessment a
    JOIN financial_snapshot s ON s.snapshot_id = a.snapshot_id
    JOIN threshold_config t ON t.threshold_config_id = a.threshold_config_id
    JOIN msme m ON m.msme_id = a.msme_id
    WHERE m.business_name LIKE 'SEED26 - Demo Business %'
      AND (s.msme_id <> a.msme_id OR t.msme_id <> a.msme_id);
  IF v_count <> 0 THEN RAISE_APPLICATION_ERROR(-20013, 'Cross-business assessment reference'); END IF;
END;
/
COMMIT;
PROMPT SEED26 complete. Existing records were preserved.

SELECT m.msme_id, m.business_name, u.email AS assigned_rm_email
FROM msme m JOIN app_user u ON u.user_id = m.assigned_rm_id
WHERE m.business_name LIKE 'SEED26 - Demo Business %'
ORDER BY m.business_name;
""")
    return "".join(output)


def render_verification():
    sql = "-- Read-only verification scoped to the SEED26 batch.\n"
    sql += "\nUNION ALL\n".join(query.replace("SELECT COUNT(*)", f"SELECT '{table}' AS table_name, COUNT(*) AS inserted_rows")
                                for table, query in counts_sql())+";\n\n"
    sql += """SELECT m.msme_id, m.business_name, COUNT(*) AS monthly_snapshots,
       MIN(s.reporting_period) AS first_month, MAX(s.reporting_period) AS last_month
FROM msme m JOIN financial_snapshot s ON s.msme_id = m.msme_id
WHERE m.business_name LIKE 'SEED26 - Demo Business %'
GROUP BY m.msme_id, m.business_name ORDER BY m.business_name;

SELECT email, role, account_status FROM app_user
WHERE email LIKE 'seed26.%@example.test' ORDER BY role, email;

SELECT m.msme_id, w.warning_id, w.warning_status, i.intervention_id,
       i.is_active, i.follow_up_date
FROM msme m JOIN risk_assessment a ON a.msme_id = m.msme_id
JOIN warning w ON w.assessment_id = a.assessment_id
LEFT JOIN intervention i ON i.warning_id = w.warning_id
WHERE m.business_name LIKE 'SEED26 - Demo Business %'
ORDER BY m.msme_id, w.warning_id, i.intervention_id;
"""
    return sql


def main():
    data = build_data()
    counts = {table: len(rows) for table, rows in data.rows.items()}
    if min(counts.values()) < 50:
        raise ValueError(f"A table has fewer than 50 rows: {counts}")
    (ROOT / "seed_demo_48_months.sql").write_text(render_sql(data), encoding="utf-8")
    (ROOT / "verify_demo.sql").write_text(render_verification(), encoding="utf-8")
    summary = {"batch": BATCH, "first_month": START.isoformat(),
        "last_month": month_add(START, MONTHS-1).isoformat(), "months_per_business": MONTHS,
        "counts": counts, "scenarios": dict(Counter(x["scenario"] for x in data.manifest)),
        "businesses": data.manifest}
    (ROOT / "manifest.json").write_text(json.dumps(summary, indent=2)+"\n", encoding="utf-8")
    print(json.dumps({"counts": counts, "reporting_months": MONTHS,
                      "warning_statuses": dict(Counter(r["warning_status"] for r in data.rows["warning"]))}, indent=2))


if __name__ == "__main__":
    main()
