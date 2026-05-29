ALTER TABLE profiles ADD COLUMN display_name_normalized VARCHAR(120);

UPDATE profiles
SET display_name_normalized = LOWER(TRIM(display_name));

UPDATE profiles p
SET display_name_normalized = LEFT(p.display_name_normalized, 110)
    || ' #'
    || (
        SELECT COUNT(*)
        FROM profiles p2
        WHERE p2.user_id = p.user_id
          AND p2.type = p.type
          AND p2.display_name_normalized = p.display_name_normalized
          AND (
              p2.created_at < p.created_at
              OR (p2.created_at = p.created_at AND CAST(p2.id AS VARCHAR) <= CAST(p.id AS VARCHAR))
          )
    )
WHERE EXISTS (
    SELECT 1
    FROM profiles p3
    WHERE p3.user_id = p.user_id
      AND p3.type = p.type
      AND p3.display_name_normalized = p.display_name_normalized
      AND (
          p3.created_at < p.created_at
          OR (p3.created_at = p.created_at AND CAST(p3.id AS VARCHAR) < CAST(p.id AS VARCHAR))
      )
);

ALTER TABLE profiles ALTER COLUMN display_name_normalized SET NOT NULL;

CREATE UNIQUE INDEX uk_profiles_user_type_display_name_normalized
    ON profiles (user_id, type, display_name_normalized);

CREATE TABLE profile_status_history (
    id UUID PRIMARY KEY,
    profile_id UUID NOT NULL,
    user_id UUID NOT NULL,
    previous_status VARCHAR(40) NOT NULL,
    new_status VARCHAR(40) NOT NULL,
    reason_text VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_profile_status_history_profile FOREIGN KEY (profile_id) REFERENCES profiles (id)
);

CREATE INDEX idx_profile_status_history_profile_id ON profile_status_history (profile_id);
