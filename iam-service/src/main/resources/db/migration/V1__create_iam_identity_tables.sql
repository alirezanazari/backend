CREATE TABLE users (
    id UUID PRIMARY KEY,
    username VARCHAR(80) NOT NULL,
    phone_number VARCHAR(30) NOT NULL,
    national_id VARCHAR(40) NOT NULL,
    first_name VARCHAR(80) NOT NULL,
    last_name VARCHAR(80) NOT NULL,
    country_code VARCHAR(2),
    province VARCHAR(80) NOT NULL,
    city VARCHAR(80) NOT NULL,
    address VARCHAR(500) NOT NULL,
    postal_code VARCHAR(20),
    selected_job_id VARCHAR(80) NOT NULL,
    status VARCHAR(40) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_phone_number UNIQUE (phone_number),
    CONSTRAINT uk_users_national_id UNIQUE (national_id)
);

CREATE TABLE credentials (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    password_hash VARCHAR(200) NOT NULL,
    password_algorithm VARCHAR(40) NOT NULL,
    password_changed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_credentials_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_credentials_user_id UNIQUE (user_id)
);

CREATE TABLE devices (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    device_fingerprint VARCHAR(256) NOT NULL,
    device_name VARCHAR(120),
    os VARCHAR(80),
    app_version VARCHAR(40),
    status VARCHAR(40) NOT NULL,
    verified_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_devices_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_devices_user_fingerprint UNIQUE (user_id, device_fingerprint)
);

CREATE TABLE otp_challenges (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    device_id UUID NOT NULL,
    otp_hash VARCHAR(200) NOT NULL,
    purpose VARCHAR(60) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    attempt_count INTEGER NOT NULL,
    max_attempts INTEGER NOT NULL,
    status VARCHAR(40) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_otp_challenges_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_otp_challenges_device FOREIGN KEY (device_id) REFERENCES devices (id) ON DELETE CASCADE
);

CREATE INDEX idx_credentials_user_id ON credentials (user_id);
CREATE INDEX idx_devices_user_id ON devices (user_id);
CREATE INDEX idx_otp_challenges_user_id ON otp_challenges (user_id);
CREATE INDEX idx_otp_challenges_device_id ON otp_challenges (device_id);
CREATE INDEX idx_otp_challenges_status_expires_at ON otp_challenges (status, expires_at);
