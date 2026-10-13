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