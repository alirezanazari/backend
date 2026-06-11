CREATE TABLE referral_codes (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    code VARCHAR(80) NOT NULL,
    display_name VARCHAR(160),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_referral_codes_user_id UNIQUE (user_id),
    CONSTRAINT uk_referral_codes_code UNIQUE (code)
);
