ALTER TABLE feature_flags
    ADD COLUMN min_amount DECIMAL(19, 2),
    ADD COLUMN max_amount DECIMAL(19, 2),
    ADD CONSTRAINT ck_feature_flags_amount_range
        CHECK (
            (min_amount IS NULL AND max_amount IS NULL)
            OR (
                min_amount IS NOT NULL
                AND max_amount IS NOT NULL
                AND min_amount > 0
                AND max_amount >= min_amount
            )
        );
INSERT INTO feature_flags (
    id,
    flag_key,
    enabled,
    description,
    client_visible,
    updated_by,
    created_at,
    updated_at,
    min_amount,
    max_amount
) VALUES (
    '10000000-0000-4000-8000-000000000007',
    'account.top-up.enabled',
    TRUE,
    'Allows customers to add funds to their main account.',
    FALSE,
    NULL,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    1000.00,
    50000000.00
);
