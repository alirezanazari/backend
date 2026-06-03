CREATE TABLE ledger_accounts (
    id UUID PRIMARY KEY,
    external_account_number VARCHAR(18) NOT NULL,
    owner_user_id UUID NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(40) NOT NULL,
    allow_negative_balance BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_ledger_accounts_external_account_number UNIQUE (external_account_number),
    CONSTRAINT ck_ledger_accounts_external_account_number_length CHECK (LENGTH(external_account_number) = 18)
);

CREATE TABLE ledger_transactions (
    id UUID PRIMARY KEY,
    reference_type VARCHAR(40) NOT NULL,
    reference_id VARCHAR(120) NOT NULL,
    idempotency_key VARCHAR(120),
    transaction_type VARCHAR(40) NOT NULL,
    status VARCHAR(40) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_ledger_transactions_reference UNIQUE (reference_type, reference_id),
    CONSTRAINT uk_ledger_transactions_idempotency_key UNIQUE (idempotency_key)
);

CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY,
    ledger_transaction_id UUID NOT NULL,
    ledger_account_id UUID NOT NULL,
    direction VARCHAR(10) NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_ledger_entries_transaction FOREIGN KEY (ledger_transaction_id) REFERENCES ledger_transactions (id),
    CONSTRAINT fk_ledger_entries_account FOREIGN KEY (ledger_account_id) REFERENCES ledger_accounts (id),
    CONSTRAINT ck_ledger_entries_amount_positive CHECK (amount > 0)
);

CREATE TABLE ledger_balances (
    ledger_account_id UUID PRIMARY KEY,
    available_balance DECIMAL(19, 2) NOT NULL,
    current_balance DECIMAL(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT fk_ledger_balances_account FOREIGN KEY (ledger_account_id) REFERENCES ledger_accounts (id)
);

CREATE INDEX idx_ledger_accounts_owner_user_id ON ledger_accounts (owner_user_id);
CREATE INDEX idx_ledger_transactions_reference ON ledger_transactions (reference_type, reference_id);
CREATE INDEX idx_ledger_entries_account_created_at ON ledger_entries (ledger_account_id, created_at);
CREATE INDEX idx_ledger_entries_transaction_id ON ledger_entries (ledger_transaction_id);

INSERT INTO ledger_accounts (
    id, external_account_number, owner_user_id, currency, status, allow_negative_balance, created_at, updated_at
)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    '000000000000000000',
    '00000000-0000-0000-0000-000000000000',
    'IRR',
    'ACTIVE',
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

INSERT INTO ledger_balances (
    ledger_account_id, available_balance, current_balance, currency, updated_at, version
)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    0.00,
    0.00,
    'IRR',
    CURRENT_TIMESTAMP,
    0
);
