# Oracle synthetic demo data: SEED26

Run `seed_demo_48_months.sql` to add a new, labelled demo batch to the ten existing
tables shown in your screenshots. No connection credentials are embedded, and no
live database operation was performed while preparing these files.

## Included data

| Table | New rows |
| --- | ---: |
| APP_USER | 62 |
| MSME | 50 |
| THRESHOLD_CONFIG | 200 |
| FINANCIAL_SNAPSHOT | 2,400 |
| RISK_ASSESSMENT | 2,400 |
| ASSESSMENT_INDICATOR_RESULT | 7,200 |
| WARNING | 93 |
| INTERVENTION | 250 |
| FOLLOW_UP_REVIEW | 982 |
| ACTIVITY_LOG | 6,343 |

Total: **19,980 new rows**. Each of 50 MSMEs has **48 contiguous monthly snapshots
and assessments, October 2022–September 2026**. Each assessment has three indicator
results. Threshold versions are approved annually. Warnings, interventions, reviews
and audit activity span more than three years; they occur when the synthetic
evidence requires them, rather than fabricating a warning every healthy month.

There are ten businesses in each scenario: steady growth, seasonal revenue,
declining revenue, collections stress and recovery. Names, people and financial
values are fictitious. Most businesses remain within their credit limit; selected
stress months deliberately exceed it. Revenue has both growth and decline periods.

The dataset includes 73 closed warnings and 20 with active interventions, revised
plans, and all four review outcomes. Escalated reviews have recorded Risk Admin
confirmation and then resume active monitoring. No future financial observations
are included; active interventions may have future due dates.

## Run the SQL

1. Open a **new demo-database session** as the owner of all ten tables (your
   screenshots show `MSME`). The worksheet must have no other uncommitted edits.
2. Open `seed_demo_48_months.sql`.
3. Use **Run Script**, so PL/SQL `/` separators and SQL*Plus error controls are
   processed. SQLcl or SQL*Plus is the reference runner. Do not send the entire
   file as one SQL statement or run selected INSERT statements individually.
4. Wait for `SEED26 complete` and the printed counts/actual MSME IDs.
5. Run `verify_demo.sql` to review the inserted batch.

From an already connected SQLcl/SQL*Plus prompt, for example:

```sql
@"C:\Users\Geetesh Chaudhari\Desktop\MSME_Early_Warning_Intervention\Recommendation-service\demo-data\seed_demo_48_months.sql"
```

The script disables autocommit, rolls back and stops on SQL/PLSQL errors in the
reference runner, and commits once after final assertions pass. If your editor does
not support the SQL*Plus script controls, use SQLcl/SQL*Plus for this import.

It **does not delete or replace existing records**, insert explicit identity IDs,
reset sequences, or create/alter tables. IDs are captured with `RETURNING INTO`, so
existing records do not need IDs starting from any particular value. Tables must
already exist with the supplied DDL's constraints. `INTERVENTION.FOLLOW_UP_DATE`
must exist, as in your screenshot; the script stops before inserting if it is absent.
`THRESHOLD_CONFIG.EFFECTIVE_FROM` is populated only if that optional column exists;
its absence does not require a schema change. Only new threshold rows are updated.

The batch is intentionally **not rerunnable**: existing `seed26.*@example.test`
accounts or labelled businesses cause the preflight to stop. Repeated execution
does not quietly add another copy. Do not bypass that check to reuse existing IDs.

## Test login and recommendation agent

All new demo users have password **`SeedDemo@2026!`**, stored as a valid BCrypt hash
verified against Spring Security. These credentials are public synthetic fixtures.

- RM example: `seed26.rm01@example.test`
- Risk Admin example: `seed26.admin01@example.test`
- Representative example: `seed26.rep001@example.test`

Login via `POST http://localhost:8082/api/auth/login`:

```json
{"email":"seed26.rm01@example.test","password":"SeedDemo@2026!"}
```

The final SQL query lists actual MSME IDs and their assigned RM emails. Use those
IDs for APIs and the recommendation agent; these are new businesses, so they will
not necessarily have IDs 1–50. `manifest.json` maps labelled businesses to scenarios
and login accounts; it cannot know the database-generated IDs before execution.

From the `Recommendation-service` directory:

```powershell
.\.venv\Scripts\python.exe -m recommendation --msme-id ACTUAL_ID --as-of 2026-09 --metrics-only
.\.venv\Scripts\python.exe -m recommendation --msme-id ACTUAL_ID --as-of 2026-09
```

Replace `ACTUAL_ID` with a number printed by the SQL script. Choose a seasonal
business from the manifest for YoY/seasonality testing. Four years supplies the
latest month plus the same calendar month in three prior years. Do not expect all
four years of every metric to be available at the beginning of the history.

## Synthetic calculation and workflow conventions

- Revenue decline: `(previous_month_revenue - revenue) / previous_month_revenue * 100`.
  First-month decline is NULL with a non-breached flag; history is unavailable.
- Overdue ratio: `overdue_receivables / total_receivables * 100`.
- Credit utilisation: `credit_used / sanctioned_credit_limit * 100`.
- Monetary inputs are rounded to cents. Indicators are rounded to four decimal
  places. A breach means the stored value is **strictly greater** than its threshold.
- Demo severity is determined by breach count: zero HEALTHY, one WATCH, two
  EARLY_STRESS, three HIGH_PRIORITY. This is a fixture convention, not an approved
  bank assessment policy or a change to your application rules.
- The assigned RM owns interventions and reviews. Follow-ups reference a later
  reporting month than both the warning baseline and intervention creation month.
  Closing reviews are STABILISING; stressed cases stay active or are revised.
  Historical due dates are valid historical fixtures, not inputs for today's API calls.
- Snapshots, assessments, warnings and reviews have historical timestamps within
  their reporting month, allowing the recommendation agent's as-of filtering to work.
  These are monthly test observations, not claims of audited real-time availability.
- Growth scenarios are illustrative. Do not train or evaluate a production credit
  model as though these artificial examples were real customer outcomes.

## Reproduce and check locally

`generate_seed.py` uses only the Python standard library and never connects to
Oracle. It regenerates the SQL, read-only verification queries and manifest.

```powershell
python generate_seed.py
python -B -m unittest discover -s . -p test_seed.py -v
```

The tests read the project's `Profile_Finance_DDL.sql`, adapt its syntax for an
in-memory SQLite check, and verify foreign keys, uniqueness, CHECK constraints,
monthly coverage, indicator values, temporal relationships and rendered INSERT
references with deliberately shifted identity IDs. This does not certify Oracle
PL/SQL compilation or a live Oracle import; those remain unverified.

Oracle reference for the script's error/rollback control:
[WHENEVER SQLERROR](https://docs.oracle.com/en/database/oracle/oracle-database/21/sqpug/WHENEVER-SQLERROR.html).
