CREATE TABLE feature_flags (
    id UUID PRIMARY KEY,
    flag_key VARCHAR(160) NOT NULL,
    enabled BOOLEAN NOT NULL,
    description VARCHAR(500),
    updated_by UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_feature_flags_flag_key UNIQUE (flag_key)
);

CREATE TABLE feature_flag_audit_logs (
    id UUID PRIMARY KEY,
    feature_flag_id UUID NOT NULL,
    flag_key VARCHAR(160) NOT NULL,
    old_enabled BOOLEAN NOT NULL,
    new_enabled BOOLEAN NOT NULL,
    old_description VARCHAR(500),
    new_description VARCHAR(500),
    changed_by UUID NOT NULL,
    reason VARCHAR(500) NOT NULL,
    request_id VARCHAR(120) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_feature_flag_audit_flag
        FOREIGN KEY (feature_flag_id) REFERENCES feature_flags (id)
);

CREATE INDEX idx_feature_flags_enabled_key ON feature_flags (enabled, flag_key);
CREATE INDEX idx_feature_flag_audit_flag_created
    ON feature_flag_audit_logs (feature_flag_id, created_at);
CREATE INDEX idx_feature_flag_audit_actor_created
    ON feature_flag_audit_logs (changed_by, created_at);

INSERT INTO feature_flags (
    id,
    flag_key,
    enabled,
    description,
    updated_by,
    created_at,
    updated_at
) VALUES
    (
        '10000000-0000-4000-8000-000000000001',
        'transfer.card-to-card.enabled',
        TRUE,
        'Allows card-to-card transfers.',
        NULL,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    (
        '10000000-0000-4000-8000-000000000002',
        'transfer.account-to-account.enabled',
        TRUE,
        'Allows account-to-account transfers.',
        NULL,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    (
        '10000000-0000-4000-8000-000000000003',
        'transfer.card-to-account.enabled',
        TRUE,
        'Allows card-to-account transfers.',
        NULL,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    (
        '10000000-0000-4000-8000-000000000004',
        'transfer.account-to-card.enabled',
        TRUE,
        'Allows account-to-card transfers.',
        NULL,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    (
        '10000000-0000-4000-8000-000000000005',
        'card.order.enabled',
        TRUE,
        'Allows customers to order physical cards.',
        NULL,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    (
        '10000000-0000-4000-8000-000000000006',
        'referral.reward.enabled',
        TRUE,
        'Allows referral rewards after KYC approval.',
        NULL,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    );
