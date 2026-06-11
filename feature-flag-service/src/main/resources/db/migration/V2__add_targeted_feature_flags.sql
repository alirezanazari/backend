ALTER TABLE feature_flags
    ADD client_visible BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE feature_flag_audit_logs
    ADD old_client_visible BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE feature_flag_audit_logs
    ADD new_client_visible BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE feature_flag_user_overrides (
    id UUID PRIMARY KEY,
    feature_flag_id UUID NOT NULL,
    user_id UUID NOT NULL,
    enabled BOOLEAN NOT NULL,
    updated_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_feature_flag_user_override_flag
        FOREIGN KEY (feature_flag_id) REFERENCES feature_flags (id),
    CONSTRAINT uk_feature_flag_user_override
        UNIQUE (feature_flag_id, user_id)
);

CREATE TABLE feature_flag_targeting_rules (
    id UUID PRIMARY KEY,
    feature_flag_id UUID NOT NULL,
    rule_name VARCHAR(160) NOT NULL,
    priority INTEGER NOT NULL,
    enabled BOOLEAN NOT NULL,
    match_type VARCHAR(10) NOT NULL,
    is_active BOOLEAN NOT NULL,
    updated_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_feature_flag_targeting_rule_flag
        FOREIGN KEY (feature_flag_id) REFERENCES feature_flags (id),
    CONSTRAINT uk_feature_flag_targeting_rule_priority
        UNIQUE (feature_flag_id, priority),
    CONSTRAINT ck_feature_flag_targeting_rule_priority
        CHECK (priority >= 0),
    CONSTRAINT ck_feature_flag_targeting_rule_match_type
        CHECK (match_type IN ('ALL', 'ANY'))
);

CREATE TABLE feature_flag_rule_conditions (
    id UUID PRIMARY KEY,
    targeting_rule_id UUID NOT NULL,
    attribute VARCHAR(60) NOT NULL,
    operator VARCHAR(40) NOT NULL,
    values_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_feature_flag_rule_condition_rule
        FOREIGN KEY (targeting_rule_id) REFERENCES feature_flag_targeting_rules (id)
);

CREATE TABLE feature_flag_targeting_audit_logs (
    id UUID PRIMARY KEY,
    feature_flag_id UUID NOT NULL,
    flag_key VARCHAR(160) NOT NULL,
    old_targeting_json TEXT NOT NULL,
    new_targeting_json TEXT NOT NULL,
    changed_by UUID NOT NULL,
    reason VARCHAR(500) NOT NULL,
    request_id VARCHAR(120) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_feature_flag_targeting_audit_flag
        FOREIGN KEY (feature_flag_id) REFERENCES feature_flags (id)
);

CREATE INDEX idx_feature_flag_user_override_user
    ON feature_flag_user_overrides (user_id, feature_flag_id);
CREATE INDEX idx_feature_flag_targeting_rule_flag
    ON feature_flag_targeting_rules (feature_flag_id, is_active, priority);
CREATE INDEX idx_feature_flag_rule_condition_rule
    ON feature_flag_rule_conditions (targeting_rule_id);
CREATE INDEX idx_feature_flag_targeting_audit_flag
    ON feature_flag_targeting_audit_logs (feature_flag_id, created_at);

UPDATE feature_flags
SET client_visible = TRUE
WHERE flag_key IN (
    'transfer.card-to-card.enabled',
    'transfer.account-to-account.enabled',
    'transfer.card-to-account.enabled',
    'transfer.account-to-card.enabled',
    'card.order.enabled'
);
