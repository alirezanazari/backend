UPDATE otp_challenges
SET status = 'EXPIRED',
    updated_at = CURRENT_TIMESTAMP
WHERE id IN (
    SELECT id
    FROM (
        SELECT id,
               ROW_NUMBER() OVER (
                   PARTITION BY user_id, device_id, purpose
                   ORDER BY created_at DESC, id DESC
               ) AS duplicate_rank
        FROM otp_challenges
        WHERE status = 'PENDING'
    ) ranked_pending_challenges
    WHERE duplicate_rank > 1
);

CREATE UNIQUE INDEX uk_otp_challenges_one_pending_device_purpose
    ON otp_challenges (user_id, device_id, purpose)
    WHERE status = 'PENDING';
