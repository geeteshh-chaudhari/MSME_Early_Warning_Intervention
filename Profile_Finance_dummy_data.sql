-- =========================================================
-- Dummy data population for Profile Finance schema
-- Run after executing Profile_Finance_DDL.sql
-- =========================================================

SET SERVEROUTPUT OFF;

-- 1) App users
-- RM and risk admin users are inserted before MSMEs because msme.assigned_rm_id references app_user(user_id)
INSERT INTO app_user (name, email, password_hash, role, account_status)
VALUES ('Riya Mehta', 'riya.mehta@msmebank.com', 'hash_riya_001', 'RM', 'ACTIVE');

INSERT INTO app_user (name, email, password_hash, role, account_status)
VALUES ('Karan Nair', 'karan.nair@msmebank.com', 'hash_karan_002', 'RM', 'ACTIVE');

INSERT INTO app_user (name, email, password_hash, role, account_status)
VALUES ('Neha Sinha', 'neha.sinha@riskdesk.com', 'hash_neha_003', 'RISK_ADMIN', 'ACTIVE');

-- 2) MSMEs
INSERT INTO msme (business_name, sector, contact_name, sanctioned_credit_limit, consent_status, assigned_rm_id, is_active)
VALUES ('Aarav Foods Pvt Ltd', 'Food Processing', 'Ravi Shah', 2500000.00, 'GRANTED', 1, 'Y');

INSERT INTO msme (business_name, sector, contact_name, sanctioned_credit_limit, consent_status, assigned_rm_id, is_active)
VALUES ('Sapphire Textiles', 'Textiles', 'Priya Desai', 1800000.00, 'GRANTED', 2, 'Y');

INSERT INTO msme (business_name, sector, contact_name, sanctioned_credit_limit, consent_status, assigned_rm_id, is_active)
VALUES ('Metro Electric Works', 'Electronics', 'Anil Joshi', 3200000.00, 'GRANTED', 1, 'Y');

INSERT INTO msme (business_name, sector, contact_name, sanctioned_credit_limit, consent_status, assigned_rm_id, is_active)
VALUES ('Evergreen Packaging', 'Packaging', 'Meena Rao', 2100000.00, 'PENDING', 2, 'Y');

-- 3) MSME representative users
-- These users are inserted after MSMEs so their msme_id foreign key can reference the created MSME rows.
INSERT INTO app_user (name, email, password_hash, role, msme_id, account_status)
VALUES ('Ravi Shah', 'ravi.shah@aaravfoods.com', 'hash_ravi_004', 'MSME_REP', 1, 'ACTIVE');

INSERT INTO app_user (name, email, password_hash, role, msme_id, account_status)
VALUES ('Priya Desai', 'priya.desai@sapphiretextiles.com', 'hash_priya_005', 'MSME_REP', 2, 'ACTIVE');

INSERT INTO app_user (name, email, password_hash, role, msme_id, account_status)
VALUES ('Anil Joshi', 'anil.joshi@metroelectric.com', 'hash_anil_006', 'MSME_REP', 3, 'ACTIVE');

INSERT INTO app_user (name, email, password_hash, role, msme_id, account_status)
VALUES ('Meena Rao', 'meena.rao@evergreenpack.com', 'hash_meena_007', 'MSME_REP', 4, 'ACTIVE');

-- 4) Financial snapshots
INSERT INTO financial_snapshot (msme_id, reporting_period, revenue, total_receivables, overdue_receivables, credit_used, submitted_by_user_id)
VALUES (1, DATE '2025-01-01', 3200000.00, 600000.00, 180000.00, 1400000.00, 4);

INSERT INTO financial_snapshot (msme_id, reporting_period, revenue, total_receivables, overdue_receivables, credit_used, submitted_by_user_id)
VALUES (1, DATE '2025-02-01', 2900000.00, 630000.00, 230000.00, 1500000.00, 4);

INSERT INTO financial_snapshot (msme_id, reporting_period, revenue, total_receivables, overdue_receivables, credit_used, submitted_by_user_id)
VALUES (2, DATE '2025-01-01', 2500000.00, 450000.00, 120000.00, 1300000.00, 5);

INSERT INTO financial_snapshot (msme_id, reporting_period, revenue, total_receivables, overdue_receivables, credit_used, submitted_by_user_id)
VALUES (2, DATE '2025-02-01', 2200000.00, 510000.00, 170000.00, 1450000.00, 5);

INSERT INTO financial_snapshot (msme_id, reporting_period, revenue, total_receivables, overdue_receivables, credit_used, submitted_by_user_id)
VALUES (3, DATE '2025-01-01', 4100000.00, 760000.00, 240000.00, 2200000.00, 6);

INSERT INTO financial_snapshot (msme_id, reporting_period, revenue, total_receivables, overdue_receivables, credit_used, submitted_by_user_id)
VALUES (3, DATE '2025-02-01', 3900000.00, 790000.00, 300000.00, 2350000.00, 6);

INSERT INTO financial_snapshot (msme_id, reporting_period, revenue, total_receivables, overdue_receivables, credit_used, submitted_by_user_id)
VALUES (4, DATE '2025-01-01', 3300000.00, 560000.00, 190000.00, 1800000.00, 7);

INSERT INTO financial_snapshot (msme_id, reporting_period, revenue, total_receivables, overdue_receivables, credit_used, submitted_by_user_id)
VALUES (4, DATE '2025-02-01', 3450000.00, 580000.00, 200000.00, 1750000.00, 7);

-- 5) Threshold configurations
INSERT INTO threshold_config (msme_id, version_no, revenue_decline_pct, overdue_receivable_pct, credit_utilisation_pct, status, proposed_by_user_id, reviewed_by_user_id, reviewed_at, review_reason, effective_from)
VALUES (1, 1, 15.00, 30.00, 70.00, 'APPROVED', 3, 3, TIMESTAMP '2025-01-15 10:00:00', 'Baseline thresholds approved for FY-2025 cycle.', TIMESTAMP '2025-01-15 00:00:00');

INSERT INTO threshold_config (msme_id, version_no, revenue_decline_pct, overdue_receivable_pct, credit_utilisation_pct, status, proposed_by_user_id, proposed_at)
VALUES (1, 2, 20.00, 35.00, 75.00, 'PENDING', 1, TIMESTAMP '2025-02-10 09:15:00');

INSERT INTO threshold_config (msme_id, version_no, revenue_decline_pct, overdue_receivable_pct, credit_utilisation_pct, status, proposed_by_user_id, reviewed_by_user_id, reviewed_at, review_reason, effective_from)
VALUES (2, 1, 18.00, 32.00, 72.00, 'APPROVED', 3, 3, TIMESTAMP '2025-01-18 15:00:00', 'Approved after review of seasonality pattern.', TIMESTAMP '2025-01-18 00:00:00');

INSERT INTO threshold_config (msme_id, version_no, revenue_decline_pct, overdue_receivable_pct, credit_utilisation_pct, status, proposed_by_user_id, reviewed_by_user_id, reviewed_at, review_reason, effective_from)
VALUES (3, 1, 25.00, 40.00, 80.00, 'APPROVED', 3, 3, TIMESTAMP '2025-01-20 11:30:00', 'High-risk thresholds approved with tighter monitoring.', TIMESTAMP '2025-01-20 00:00:00');

INSERT INTO threshold_config (msme_id, version_no, revenue_decline_pct, overdue_receivable_pct, credit_utilisation_pct, status, proposed_by_user_id, proposed_at)
VALUES (4, 1, 12.00, 28.00, 68.00, 'PENDING', 2, TIMESTAMP '2025-02-12 14:00:00');

-- 6) Risk assessments
INSERT INTO risk_assessment (msme_id, snapshot_id, threshold_config_id, assessment_date, severity, stress_pattern, previous_assessment_id)
VALUES (1, 2, 2, TIMESTAMP '2025-02-15 09:00:00', 'EARLY_STRESS', 'SEASONAL_DROP', NULL);

INSERT INTO risk_assessment (msme_id, snapshot_id, threshold_config_id, assessment_date, severity, stress_pattern, previous_assessment_id)
VALUES (2, 4, 3, TIMESTAMP '2025-02-18 11:30:00', 'WATCH', 'RECEIVABLE_SPIKE', 1);

INSERT INTO risk_assessment (msme_id, snapshot_id, threshold_config_id, assessment_date, severity, stress_pattern, previous_assessment_id)
VALUES (3, 6, 4, TIMESTAMP '2025-02-20 08:45:00', 'HIGH_PRIORITY', 'CREDIT_OVERUSE', NULL);

INSERT INTO risk_assessment (msme_id, snapshot_id, threshold_config_id, assessment_date, severity, stress_pattern, previous_assessment_id)
VALUES (4, 8, 5, TIMESTAMP '2025-02-22 10:00:00', 'HEALTHY', 'STABLE_GROWTH', NULL);

-- 7) Indicator results
INSERT INTO assessment_indicator_result (assessment_id, indicator_code, calculated_value, threshold_value_used, breached_flag, explanation_text)
VALUES (1, 'REVENUE_DETERIORATION', 18.50, 15.00, 'Y', 'Revenue declined by 18.5% versus the forecast baseline in the latest period.');

INSERT INTO assessment_indicator_result (assessment_id, indicator_code, calculated_value, threshold_value_used, breached_flag, explanation_text)
VALUES (1, 'OVERDUE_RECEIVABLES', 36.50, 30.00, 'Y', 'Overdue receivables ratio increased above the approved trigger threshold.');

INSERT INTO assessment_indicator_result (assessment_id, indicator_code, calculated_value, threshold_value_used, breached_flag, explanation_text)
VALUES (1, 'CREDIT_UTILISATION', 72.00, 70.00, 'Y', 'Credit utilisation is at 72%, pushing the firm into elevated stress conditions.');

INSERT INTO assessment_indicator_result (assessment_id, indicator_code, calculated_value, threshold_value_used, breached_flag, explanation_text)
VALUES (2, 'REVENUE_DETERIORATION', 12.00, 18.00, 'N', 'Revenue drop is mild and remains within tolerance.');

INSERT INTO assessment_indicator_result (assessment_id, indicator_code, calculated_value, threshold_value_used, breached_flag, explanation_text)
VALUES (2, 'OVERDUE_RECEIVABLES', 33.30, 32.00, 'Y', 'Receivables are elevated and trending above acceptable levels.');

INSERT INTO assessment_indicator_result (assessment_id, indicator_code, calculated_value, threshold_value_used, breached_flag, explanation_text)
VALUES (2, 'CREDIT_UTILISATION', 74.00, 72.00, 'Y', 'Utilisation exceeds the configured threshold and requires monitoring.');

INSERT INTO assessment_indicator_result (assessment_id, indicator_code, calculated_value, threshold_value_used, breached_flag, explanation_text)
VALUES (3, 'REVENUE_DETERIORATION', 24.40, 25.00, 'N', 'Revenue contraction is close to the trigger, but still below the limit.');

INSERT INTO assessment_indicator_result (assessment_id, indicator_code, calculated_value, threshold_value_used, breached_flag, explanation_text)
VALUES (3, 'OVERDUE_RECEIVABLES', 38.00, 40.00, 'N', 'Overdue receivables are within the allowable threshold.');

INSERT INTO assessment_indicator_result (assessment_id, indicator_code, calculated_value, threshold_value_used, breached_flag, explanation_text)
VALUES (3, 'CREDIT_UTILISATION', 82.00, 80.00, 'Y', 'Utilisation is above the approved threshold and warrants escalation.');

INSERT INTO assessment_indicator_result (assessment_id, indicator_code, calculated_value, threshold_value_used, breached_flag, explanation_text)
VALUES (4, 'REVENUE_DETERIORATION', 3.80, 12.00, 'N', 'Business shows stable revenue growth with no deterioration trend.');

INSERT INTO assessment_indicator_result (assessment_id, indicator_code, calculated_value, threshold_value_used, breached_flag, explanation_text)
VALUES (4, 'OVERDUE_RECEIVABLES', 15.00, 28.00, 'N', 'Receivables remain healthy and in control.');

INSERT INTO assessment_indicator_result (assessment_id, indicator_code, calculated_value, threshold_value_used, breached_flag, explanation_text)
VALUES (4, 'CREDIT_UTILISATION', 58.00, 68.00, 'N', 'Utilisation is comfortably below the risk threshold.');

-- 8) Warnings
INSERT INTO warning (assessment_id, warning_status, primary_driver, why_now_text, recommended_action)
VALUES (1, 'OPEN', 'REVENUE_DETERIORATION', 'Revenue and utilisation have deteriorated sharply in the last reporting cycle, increasing refinancing risk.', 'Offer a structured repayment plan and review monthly cash flow controls immediately.');

INSERT INTO warning (assessment_id, warning_status, primary_driver, why_now_text, recommended_action)
VALUES (2, 'UNDER_REVIEW', 'OVERDUE_RECEIVABLES', 'Receivables are trending above policy limits and require anRM-led review before escalation.', 'Validate customer ageing report and confirm whether temporary credit extension is still viable.');

INSERT INTO warning (assessment_id, warning_status, primary_driver, why_now_text, recommended_action)
VALUES (3, 'ESCALATED', 'CREDIT_UTILISATION', 'Utilisation exceeds the approved threshold and the customer is operating near maximum exposure.', 'Escalate to senior risk committee and prepare intervention monitoring across the next cycle.');

-- 9) Interventions
INSERT INTO intervention (warning_id, created_by_user_id, action_text, notes, is_active)
VALUES (1, 1, 'Restructure the repayment schedule and set weekly collections checkpoints.', 'The customer needs cash-flow support while maintaining operating continuity.', 'Y');

INSERT INTO intervention (warning_id, created_by_user_id, action_text, notes, is_active)
VALUES (3, 3, 'Raise the intervention to committee escalation with enhanced account review.', 'Account shows elevated exposure and has entered the high-priority watch list.', 'Y');

-- 10) Follow-up reviews
INSERT INTO follow_up_review (intervention_id, assessment_id, reviewed_by_user_id, outcome, review_notes, keep_intervention_active)
VALUES (1, 1, 3, 'IMPROVING', 'Customer has improved collections quality after schedule rescheduling and tighter monitoring.', 'Y');

INSERT INTO follow_up_review (intervention_id, assessment_id, reviewed_by_user_id, outcome, review_notes, keep_intervention_active)
VALUES (2, 3, 1, 'ESCALATED', 'Risk remains elevated, and account exposure continues to exceed internal tolerance levels.', 'Y');

-- 11) Activity logs
INSERT INTO activity_log (msme_id, actor_user_id, actor_type, action_code, related_entity_type, related_entity_id, description, metadata_json)
VALUES (1, 4, 'USER', 'FINANCIAL_SUBMISSION', 'FINANCIAL_SNAPSHOT', 2, 'Submitted February financial snapshot for the customer.', '{"month":"2025-02","status":"submitted"}');

INSERT INTO activity_log (msme_id, actor_user_id, actor_type, action_code, related_entity_type, related_entity_id, description, metadata_json)
VALUES (2, 5, 'USER', 'RISK_REVIEW', 'RISK_ASSESSMENT', 2, 'Reviewed risk assessment and flagged receivables spike.', '{"severity":"WATCH","status":"reviewed"}');

INSERT INTO activity_log (msme_id, actor_user_id, actor_type, action_code, related_entity_type, related_entity_id, description, metadata_json)
VALUES (3, 3, 'USER', 'WARNING_ESCALATED', 'WARNING', 3, 'Escalated warning to senior risk committee.', '{"warning_status":"ESCALATED","priority":"HIGH"}');

INSERT INTO activity_log (msme_id, actor_user_id, actor_type, action_code, related_entity_type, related_entity_id, description, metadata_json)
VALUES (1, 1, 'USER', 'INTERVENTION_CREATED', 'INTERVENTION', 1, 'Created intervention to restructure repayment timeline.', '{"action":"restructure","status":"active"}');

COMMIT;
