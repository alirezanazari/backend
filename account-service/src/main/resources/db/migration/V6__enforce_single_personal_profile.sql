CREATE UNIQUE INDEX uk_profiles_single_personal_per_user
    ON profiles (user_id)
    WHERE type = 'PERSONAL';
