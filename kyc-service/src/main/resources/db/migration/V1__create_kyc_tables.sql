CREATE TABLE jobs (
    id VARCHAR(80) PRIMARY KEY,
    code VARCHAR(80) NOT NULL,
    title VARCHAR(160) NOT NULL,
    is_active BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_jobs_code UNIQUE (code)
);

CREATE TABLE kyc_cases (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    status VARCHAR(40) NOT NULL,
    rejection_reason VARCHAR(500),
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_kyc_cases_user_id UNIQUE (user_id)
);

CREATE TABLE referrals (
    id UUID PRIMARY KEY,
    referred_user_id UUID NOT NULL,
    referrer_user_id UUID NOT NULL,
    referral_code VARCHAR(80) NOT NULL,
    referrer_name VARCHAR(160),
    status VARCHAR(40) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_referrals_referred_user_id UNIQUE (referred_user_id),
    CONSTRAINT ck_referrals_different_users CHECK (referred_user_id <> referrer_user_id)
);

CREATE TABLE referral_rewards (
    id UUID PRIMARY KEY,
    referral_id UUID NOT NULL,
    referrer_account_number VARCHAR(18),
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(40) NOT NULL,
    ledger_transaction_id VARCHAR(120),
    requested_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_referral_rewards_referral FOREIGN KEY (referral_id) REFERENCES referrals (id),
    CONSTRAINT uk_referral_rewards_referral_id UNIQUE (referral_id),
    CONSTRAINT ck_referral_rewards_amount_positive CHECK (amount > 0)
);

CREATE TABLE kyc_case_history (
    id UUID PRIMARY KEY,
    kyc_case_id UUID NOT NULL,
    previous_status VARCHAR(40),
    new_status VARCHAR(40) NOT NULL,
    reason VARCHAR(500),
    actor_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_kyc_case_history_case FOREIGN KEY (kyc_case_id) REFERENCES kyc_cases (id)
);

CREATE INDEX idx_jobs_active_title ON jobs (is_active, title);
CREATE INDEX idx_kyc_cases_status ON kyc_cases (status);
CREATE INDEX idx_referrals_referrer_user_id ON referrals (referrer_user_id);
CREATE INDEX idx_referrals_status ON referrals (status);
CREATE INDEX idx_referral_rewards_status ON referral_rewards (status);
CREATE INDEX idx_kyc_case_history_case ON kyc_case_history (kyc_case_id, created_at);

INSERT INTO jobs (id, code, title, is_active, created_at, updated_at) VALUES
    ('job-engineer', 'ENGINEER', 'Engineer', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('job-student', 'STUDENT', 'Student', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('job-business-owner', 'BUSINESS_OWNER', 'Business owner', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('job-other', 'OTHER', 'Other', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
