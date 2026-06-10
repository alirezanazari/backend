CREATE TABLE transfer_methods (
    method VARCHAR(40) PRIMARY KEY,
    source_type VARCHAR(20) NOT NULL,
    destination_type VARCHAR(20) NOT NULL,
    title VARCHAR(120) NOT NULL,
    enabled BOOLEAN NOT NULL,
    sort_order INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE transfers (
    id UUID PRIMARY KEY,
    request_id VARCHAR(120) NOT NULL,
    user_id UUID NOT NULL,
    profile_id UUID NOT NULL,
    source_type VARCHAR(20) NOT NULL,
    source_reference VARCHAR(80) NOT NULL,
    source_account_number VARCHAR(18) NOT NULL,
    destination_type VARCHAR(20) NOT NULL,
    destination_reference VARCHAR(80) NOT NULL,
    destination_account_number VARCHAR(18) NOT NULL,
    destination_display_name VARCHAR(160) NOT NULL,
    destination_account_or_card_number VARCHAR(40) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    method VARCHAR(40) NOT NULL,
    status VARCHAR(40) NOT NULL,
    ledger_transaction_id VARCHAR(120),
    description VARCHAR(500),
    failure_reason VARCHAR(120),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_transfers_method FOREIGN KEY (method) REFERENCES transfer_methods (method),
    CONSTRAINT ck_transfers_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_transfers_source_account_length CHECK (LENGTH(source_account_number) = 18),
    CONSTRAINT ck_transfers_destination_account_length CHECK (LENGTH(destination_account_number) = 18)
);

CREATE TABLE idempotency_keys (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    transfer_id UUID,
    response_snapshot VARCHAR(1000),
    status VARCHAR(40) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_idempotency_user_key UNIQUE (user_id, idempotency_key),
    CONSTRAINT fk_idempotency_transfer FOREIGN KEY (transfer_id) REFERENCES transfers (id)
);

CREATE TABLE transfer_status_history (
    id UUID PRIMARY KEY,
    transfer_id UUID NOT NULL,
    previous_status VARCHAR(40),
    new_status VARCHAR(40) NOT NULL,
    reason VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_transfer_status_history_transfer FOREIGN KEY (transfer_id) REFERENCES transfers (id)
);

CREATE INDEX idx_transfers_user_profile_status_created
    ON transfers (user_id, profile_id, status, created_at);
CREATE INDEX idx_transfers_request_id ON transfers (request_id);
CREATE INDEX idx_transfers_ledger_transaction ON transfers (ledger_transaction_id);
CREATE INDEX idx_idempotency_expires_at ON idempotency_keys (expires_at);
CREATE INDEX idx_transfer_status_history_transfer
    ON transfer_status_history (transfer_id, created_at);

INSERT INTO transfer_methods (
    method,
    source_type,
    destination_type,
    title,
    enabled,
    sort_order,
    created_at,
    updated_at
) VALUES
    ('CARD_TO_CARD', 'CARD', 'CARD', 'Card to card', TRUE, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('ACCOUNT_TO_ACCOUNT', 'ACCOUNT', 'ACCOUNT', 'Account to account', TRUE, 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('CARD_TO_ACCOUNT', 'CARD', 'ACCOUNT', 'Card to account', TRUE, 30, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('ACCOUNT_TO_CARD', 'ACCOUNT', 'CARD', 'Account to card', TRUE, 40, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
