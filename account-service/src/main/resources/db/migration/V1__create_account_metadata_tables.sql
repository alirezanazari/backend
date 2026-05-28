CREATE TABLE profiles (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    selected_job_id VARCHAR(80) NOT NULL,
    type VARCHAR(40) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    display_name_normalized VARCHAR(120) NOT NULL,
    image_light_url VARCHAR(500),
    image_dark_url VARCHAR(500),
    image_alt VARCHAR(120),
    status VARCHAR(40) NOT NULL,
    is_active BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX uk_profiles_user_type_display_name_normalized
    ON profiles (user_id, type, display_name_normalized);

CREATE TABLE bank_accounts (
    id UUID PRIMARY KEY,
    profile_id UUID NOT NULL,
    user_id UUID NOT NULL,
    selected_job_id VARCHAR(80) NOT NULL,
    account_number VARCHAR(18) NOT NULL,
    iban VARCHAR(34) NOT NULL,
    type VARCHAR(40) NOT NULL,
    status VARCHAR(40) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    closed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_bank_accounts_profile FOREIGN KEY (profile_id) REFERENCES profiles (id),
    CONSTRAINT uk_bank_accounts_account_number UNIQUE (account_number),
    CONSTRAINT uk_bank_accounts_iban UNIQUE (iban),
    CONSTRAINT ck_bank_accounts_account_number_length CHECK (LENGTH(account_number) = 18)
);

CREATE TABLE account_status_history (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL,
    user_id UUID NOT NULL,
    profile_id UUID NOT NULL,
    previous_status VARCHAR(40) NOT NULL,
    new_status VARCHAR(40) NOT NULL,
    reason_text VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_account_status_history_account FOREIGN KEY (account_id) REFERENCES bank_accounts (id)
);

CREATE INDEX idx_profiles_user_id ON profiles (user_id);
CREATE INDEX idx_profiles_user_active ON profiles (user_id, is_active);
CREATE INDEX idx_bank_accounts_user_id ON bank_accounts (user_id);
CREATE INDEX idx_bank_accounts_profile_id ON bank_accounts (profile_id);
CREATE INDEX idx_bank_accounts_status ON bank_accounts (status);
CREATE INDEX idx_account_status_history_account_id ON account_status_history (account_id);
