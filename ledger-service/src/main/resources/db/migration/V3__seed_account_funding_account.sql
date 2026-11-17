INSERT INTO ledger_accounts (
    id, external_account_number, owner_user_id, currency, status, allow_negative_balance, created_at, updated_at
)
VALUES (
    '00000000-0000-0000-0000-000000000002',
    '000000000000000001',
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
    '00000000-0000-0000-0000-000000000002',
    0.00,
    0.00,
    'IRR',
    CURRENT_TIMESTAMP,
    0
);
