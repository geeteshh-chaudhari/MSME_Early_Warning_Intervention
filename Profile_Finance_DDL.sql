-- App User table

CREATE TABLE app_user (
    user_id NUMBER GENERATED ALWAYS AS IDENTITY
        PRIMARY KEY,

    name VARCHAR2(100) NOT NULL,

    email VARCHAR2(150) NOT NULL,

    password_hash VARCHAR2(255) NOT NULL,

    role VARCHAR2(20) NOT NULL,

    msme_id NUMBER,

    account_status VARCHAR2(20) DEFAULT 'ACTIVE' NOT NULL,

    created_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,

    updated_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,

    CONSTRAINT uq_app_user_email
        UNIQUE (email),

    CONSTRAINT uq_app_user_msme
        UNIQUE (msme_id),

    CONSTRAINT chk_app_user_role
        CHECK (role IN ('RM', 'RISK_ADMIN', 'MSME_REP')),

    CONSTRAINT chk_app_user_status
        CHECK (account_status IN ('ACTIVE', 'INACTIVE')),

    CONSTRAINT chk_app_user_msme_role
        CHECK (
            (role = 'MSME_REP' AND msme_id IS NOT NULL)
            OR
            (role IN ('RM', 'RISK_ADMIN') AND msme_id IS NULL)
        )
);


-- MSME Table -> Refers App user (RM)

CREATE TABLE msme (
    msme_id NUMBER GENERATED ALWAYS AS IDENTITY
        PRIMARY KEY,

    business_name VARCHAR2(150) NOT NULL,

    sector VARCHAR2(50) NOT NULL,

    contact_name VARCHAR2(100) NOT NULL,

    sanctioned_credit_limit NUMBER(18,2) NOT NULL,

    consent_status VARCHAR2(20),

    assigned_rm_id NUMBER NOT NULL,

    is_active CHAR(1) DEFAULT 'Y' NOT NULL,

    created_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,

    updated_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,

    CONSTRAINT chk_msme_credit_limit
        CHECK (sanctioned_credit_limit > 0),

    CONSTRAINT chk_msme_active
        CHECK (is_active IN ('Y', 'N')),

    CONSTRAINT fk_msme_assigned_rm
        FOREIGN KEY (assigned_rm_id)
        REFERENCES app_user(user_id)
);

-- App user MSME Rep refers MSME table

ALTER TABLE app_user
ADD CONSTRAINT fk_app_user_msme
FOREIGN KEY (msme_id)
REFERENCES msme(msme_id);

-- Financial snapshot table

CREATE TABLE financial_snapshot (
    snapshot_id NUMBER GENERATED ALWAYS AS IDENTITY
        PRIMARY KEY,

    msme_id NUMBER NOT NULL,

    reporting_period DATE NOT NULL,

    revenue NUMBER(18,2) NOT NULL,

    total_receivables NUMBER(18,2) NOT NULL,

    overdue_receivables NUMBER(18,2) NOT NULL,

    credit_used NUMBER(18,2) NOT NULL,

    submitted_by_user_id NUMBER NOT NULL,

    submitted_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,

    created_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,

    updated_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,

    CONSTRAINT uq_snapshot_msme_period
        UNIQUE (msme_id, reporting_period),

    CONSTRAINT chk_snapshot_reporting_month
        CHECK (
            reporting_period = TRUNC(reporting_period, 'MM')
        ),

    CONSTRAINT chk_snapshot_revenue
        CHECK (revenue >= 0),

    CONSTRAINT chk_snapshot_total_receivables
        CHECK (total_receivables >= 0),

    CONSTRAINT chk_snapshot_overdue_receivables
        CHECK (
            overdue_receivables >= 0
            AND overdue_receivables <= total_receivables
        ),

    CONSTRAINT chk_snapshot_credit_used
        CHECK (credit_used >= 0),

    CONSTRAINT fk_snapshot_msme
        FOREIGN KEY (msme_id)
        REFERENCES msme(msme_id),

    CONSTRAINT fk_snapshot_submitted_by
        FOREIGN KEY (submitted_by_user_id)
        REFERENCES app_user(user_id)
);

CREATE TABLE threshold_config (
    threshold_config_id NUMBER GENERATED ALWAYS AS IDENTITY
        PRIMARY KEY,

    msme_id NUMBER NOT NULL,

    version_no NUMBER NOT NULL,

    revenue_decline_pct NUMBER(5,2) DEFAULT 15 NOT NULL,

    overdue_receivable_pct NUMBER(5,2) DEFAULT 30 NOT NULL,

    credit_utilisation_pct NUMBER(5,2) DEFAULT 70 NOT NULL,

    status VARCHAR2(20) DEFAULT 'PENDING' NOT NULL,

    proposed_by_user_id NUMBER NOT NULL,

    proposed_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,

    reviewed_by_user_id NUMBER,

    reviewed_at TIMESTAMP,

    review_reason CLOB,

    effective_from TIMESTAMP,

    CONSTRAINT uq_threshold_msme_version
        UNIQUE (msme_id, version_no),

    CONSTRAINT chk_threshold_status
        CHECK (
            status IN ('PENDING', 'APPROVED', 'REJECTED')
        ),

    CONSTRAINT chk_revenue_threshold
        CHECK (
            revenue_decline_pct >= 0
            AND revenue_decline_pct <= 100
        ),

    CONSTRAINT chk_receivable_threshold
        CHECK (
            overdue_receivable_pct >= 0
            AND overdue_receivable_pct <= 100
        ),

    CONSTRAINT chk_credit_threshold
        CHECK (
            credit_utilisation_pct >= 0
        ),

    CONSTRAINT fk_threshold_config_msme
        FOREIGN KEY (msme_id)
        REFERENCES msme(msme_id),

    CONSTRAINT fk_threshold_proposed_by
        FOREIGN KEY (proposed_by_user_id)
        REFERENCES app_user(user_id),

    CONSTRAINT fk_threshold_reviewed_by
        FOREIGN KEY (reviewed_by_user_id)
        REFERENCES app_user(user_id)
);

CREATE TABLE risk_assessment (
    assessment_id NUMBER GENERATED ALWAYS AS IDENTITY
        PRIMARY KEY,

    msme_id NUMBER NOT NULL,

    snapshot_id NUMBER NOT NULL,

    threshold_config_id NUMBER NOT NULL,

    assessment_date TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,

    severity VARCHAR2(30) NOT NULL,

    stress_pattern VARCHAR2(50),

    previous_assessment_id NUMBER,

    created_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,

    CONSTRAINT uq_assessment_snapshot
        UNIQUE (snapshot_id),

    CONSTRAINT chk_assessment_severity
        CHECK (
            severity IN (
                'HEALTHY',
                'WATCH',
                'EARLY_STRESS',
                'HIGH_PRIORITY'
            )
        ),

    CONSTRAINT fk_assessment_threshold
        FOREIGN KEY (threshold_config_id)
        REFERENCES threshold_config(threshold_config_id),

    CONSTRAINT fk_previous_assessment
        FOREIGN KEY (previous_assessment_id)
        REFERENCES risk_assessment(assessment_id),

    CONSTRAINT fk_assessment_msme
        FOREIGN KEY (msme_id)
        REFERENCES msme(msme_id),

    CONSTRAINT fk_assessment_snapshot
        FOREIGN KEY (snapshot_id)
        REFERENCES financial_snapshot(snapshot_id)
);


CREATE TABLE assessment_indicator_result (
    indicator_result_id NUMBER GENERATED ALWAYS AS IDENTITY
        PRIMARY KEY,

    assessment_id NUMBER NOT NULL,

    indicator_code VARCHAR2(30) NOT NULL,

    calculated_value NUMBER(12,4),

    threshold_value_used NUMBER(12,4) NOT NULL,

    breached_flag CHAR(1) NOT NULL,

    explanation_text CLOB,

    CONSTRAINT uq_assessment_indicator
        UNIQUE (assessment_id, indicator_code),

    CONSTRAINT chk_indicator_code
        CHECK (
            indicator_code IN (
                'REVENUE_DETERIORATION',
                'OVERDUE_RECEIVABLES',
                'CREDIT_UTILISATION'
            )
        ),

    CONSTRAINT chk_indicator_breached
        CHECK (
            breached_flag IN ('Y', 'N')
        ),

    CONSTRAINT fk_indicator_assessment
        FOREIGN KEY (assessment_id)
        REFERENCES risk_assessment(assessment_id)
);

CREATE TABLE warning (
    warning_id NUMBER GENERATED ALWAYS AS IDENTITY
        PRIMARY KEY,

    assessment_id NUMBER NOT NULL,

    warning_status VARCHAR2(30) DEFAULT 'OPEN' NOT NULL,

    primary_driver VARCHAR2(50),

    why_now_text CLOB,

    recommended_action CLOB,

    created_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,

    closed_at TIMESTAMP,

    CONSTRAINT uq_warning_assessment
        UNIQUE (assessment_id),

    CONSTRAINT chk_warning_status
        CHECK (
            warning_status IN (
                'OPEN',
                'UNDER_REVIEW',
                'ESCALATED',
                'INTERVENTION_ACTIVE',
                'CLOSED_REVIEWED',
                'CLOSED'
            )
        ),

    CONSTRAINT fk_warning_assessment
        FOREIGN KEY (assessment_id)
        REFERENCES risk_assessment(assessment_id)
);

CREATE TABLE intervention (
    intervention_id NUMBER GENERATED ALWAYS AS IDENTITY
        PRIMARY KEY,

    warning_id NUMBER NOT NULL,

    created_by_user_id NUMBER NOT NULL,

    action_text CLOB NOT NULL,

    notes CLOB,

    is_active CHAR(1) DEFAULT 'Y' NOT NULL,

    created_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,

    closed_at TIMESTAMP,

    closed_by_user_id NUMBER,

    CONSTRAINT chk_intervention_active
        CHECK (
            is_active IN ('Y', 'N')
        ),

    CONSTRAINT fk_intervention_warning
        FOREIGN KEY (warning_id)
        REFERENCES warning(warning_id),

    CONSTRAINT fk_intervention_created_by
        FOREIGN KEY (created_by_user_id)
        REFERENCES app_user(user_id),

    CONSTRAINT fk_intervention_closed_by
        FOREIGN KEY (closed_by_user_id)
        REFERENCES app_user(user_id)
);

CREATE TABLE follow_up_review (
    followup_id NUMBER GENERATED ALWAYS AS IDENTITY
        PRIMARY KEY,

    intervention_id NUMBER NOT NULL,

    assessment_id NUMBER NOT NULL,

    reviewed_by_user_id NUMBER NOT NULL,

    outcome VARCHAR2(30) NOT NULL,

    review_notes CLOB,

    reviewed_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,

    keep_intervention_active CHAR(1) NOT NULL,

    CONSTRAINT uq_followup_intervention_assessment
        UNIQUE (intervention_id, assessment_id),

    CONSTRAINT chk_followup_outcome
        CHECK (
            outcome IN (
                'IMPROVING',
                'STABILISING',
                'STILL_STRESSED',
                'ESCALATED'
            )
        ),

    CONSTRAINT chk_keep_intervention_active
        CHECK (
            keep_intervention_active IN ('Y', 'N')
        ),

    CONSTRAINT fk_followup_intervention
        FOREIGN KEY (intervention_id)
        REFERENCES intervention(intervention_id),

    CONSTRAINT fk_followup_assessment
        FOREIGN KEY (assessment_id)
        REFERENCES risk_assessment(assessment_id),

    CONSTRAINT fk_followup_reviewed_by
        FOREIGN KEY (reviewed_by_user_id)
        REFERENCES app_user(user_id)
);

CREATE TABLE activity_log (
    activity_id NUMBER GENERATED ALWAYS AS IDENTITY
        PRIMARY KEY,

    msme_id NUMBER NOT NULL,

    actor_user_id NUMBER,

    actor_type VARCHAR2(20) NOT NULL,

    action_code VARCHAR2(50) NOT NULL,

    related_entity_type VARCHAR2(30),

    related_entity_id NUMBER,

    description CLOB,

    created_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,

    metadata_json CLOB,

    CONSTRAINT chk_activity_actor_type
        CHECK (
            actor_type IN (
                'USER',
                'SYSTEM'
            )
        ),

    CONSTRAINT chk_activity_metadata_json
        CHECK (
            metadata_json IS JSON
        ),

    CONSTRAINT fk_activity_msme
        FOREIGN KEY (msme_id)
        REFERENCES msme(msme_id),

    CONSTRAINT fk_activity_actor
        FOREIGN KEY (actor_user_id)
        REFERENCES app_user(user_id)
);

CREATE INDEX idx_threshold_msme
ON threshold_config (msme_id);

CREATE INDEX idx_assessment_msme_date
ON risk_assessment (msme_id, assessment_date);

CREATE INDEX idx_assessment_severity
ON risk_assessment (severity);

CREATE INDEX idx_indicator_assessment
ON assessment_indicator_result (assessment_id);

CREATE INDEX idx_warning_status
ON warning (warning_status);

CREATE INDEX idx_intervention_warning
ON intervention (warning_id);

CREATE INDEX idx_intervention_active
ON intervention (is_active);

CREATE INDEX idx_followup_intervention
ON follow_up_review (intervention_id);

CREATE INDEX idx_activity_msme_date
ON activity_log (msme_id, created_at);

--USER1 / Profile-Financial
--─────────────────────────────────────
--APP_USER
--MSME
--FINANCIAL_SNAPSHOT


--USER2 / Assessment
--─────────────────────────────────────
--THRESHOLD_CONFIG
--RISK_ASSESSMENT
--ASSESSMENT_INDICATOR_RESULT


--USER3 / Warning-Intervention
--─────────────────────────────────────
--WARNING
--INTERVENTION
--FOLLOW_UP_REVIEW
--ACTIVITY_LOG