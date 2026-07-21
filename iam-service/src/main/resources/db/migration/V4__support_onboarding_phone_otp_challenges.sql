ALTER TABLE otp_challenges ALTER COLUMN user_id DROP NOT NULL;
ALTER TABLE otp_challenges ALTER COLUMN device_id DROP NOT NULL;
ALTER TABLE otp_challenges ADD COLUMN phone_number VARCHAR(30);
ALTER TABLE otp_challenges ADD COLUMN onboarding_session_id UUID;

CREATE UNIQUE INDEX uk_otp_challenges_one_pending_session_purpose
    ON otp_challenges (onboarding_session_id, purpose)
    WHERE status = 'PENDING' AND onboarding_session_id IS NOT NULL;

CREATE INDEX idx_otp_challenges_phone_created_at
    ON otp_challenges (phone_number, created_at)
    WHERE phone_number IS NOT NULL;
