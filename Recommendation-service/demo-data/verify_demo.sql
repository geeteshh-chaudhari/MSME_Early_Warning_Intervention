-- Read-only verification scoped to the SEED26 batch.
SELECT 'app_user' AS table_name, COUNT(*) AS inserted_rows FROM app_user WHERE email LIKE 'seed26.%@example.test'
UNION ALL
SELECT 'msme' AS table_name, COUNT(*) AS inserted_rows FROM msme WHERE business_name LIKE 'SEED26 - Demo Business %'
UNION ALL
SELECT 'threshold_config' AS table_name, COUNT(*) AS inserted_rows FROM threshold_config WHERE msme_id IN (SELECT msme_id FROM msme WHERE business_name LIKE 'SEED26 - Demo Business %')
UNION ALL
SELECT 'financial_snapshot' AS table_name, COUNT(*) AS inserted_rows FROM financial_snapshot WHERE msme_id IN (SELECT msme_id FROM msme WHERE business_name LIKE 'SEED26 - Demo Business %')
UNION ALL
SELECT 'risk_assessment' AS table_name, COUNT(*) AS inserted_rows FROM risk_assessment WHERE msme_id IN (SELECT msme_id FROM msme WHERE business_name LIKE 'SEED26 - Demo Business %')
UNION ALL
SELECT 'assessment_indicator_result' AS table_name, COUNT(*) AS inserted_rows FROM assessment_indicator_result WHERE assessment_id IN (SELECT assessment_id FROM risk_assessment WHERE msme_id IN (SELECT msme_id FROM msme WHERE business_name LIKE 'SEED26 - Demo Business %'))
UNION ALL
SELECT 'warning' AS table_name, COUNT(*) AS inserted_rows FROM warning WHERE assessment_id IN (SELECT assessment_id FROM risk_assessment WHERE msme_id IN (SELECT msme_id FROM msme WHERE business_name LIKE 'SEED26 - Demo Business %'))
UNION ALL
SELECT 'intervention' AS table_name, COUNT(*) AS inserted_rows FROM intervention WHERE warning_id IN (SELECT warning_id FROM warning WHERE assessment_id IN (SELECT assessment_id FROM risk_assessment WHERE msme_id IN (SELECT msme_id FROM msme WHERE business_name LIKE 'SEED26 - Demo Business %')))
UNION ALL
SELECT 'follow_up_review' AS table_name, COUNT(*) AS inserted_rows FROM follow_up_review WHERE intervention_id IN (SELECT intervention_id FROM intervention WHERE warning_id IN (SELECT warning_id FROM warning WHERE assessment_id IN (SELECT assessment_id FROM risk_assessment WHERE msme_id IN (SELECT msme_id FROM msme WHERE business_name LIKE 'SEED26 - Demo Business %'))))
UNION ALL
SELECT 'activity_log' AS table_name, COUNT(*) AS inserted_rows FROM activity_log WHERE msme_id IN (SELECT msme_id FROM msme WHERE business_name LIKE 'SEED26 - Demo Business %');

SELECT m.msme_id, m.business_name, COUNT(*) AS monthly_snapshots,
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
