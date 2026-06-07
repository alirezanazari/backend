CREATE TABLE cards (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    profile_id UUID NOT NULL,
    account_number VARCHAR(18) NOT NULL,
    masked_pan VARCHAR(19) NOT NULL,
    card_token VARCHAR(80) NOT NULL,
    status VARCHAR(40) NOT NULL,
    expires_at DATE NOT NULL,
    issuance_reference VARCHAR(120) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_cards_card_token UNIQUE (card_token),
    CONSTRAINT uk_cards_issuance_reference UNIQUE (issuance_reference),
    CONSTRAINT ck_cards_account_number_length CHECK (LENGTH(account_number) = 18)
);

CREATE TABLE card_orders (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    profile_id UUID NOT NULL,
    account_number VARCHAR(18) NOT NULL,
    delivery_country_code VARCHAR(2),
    delivery_province VARCHAR(80),
    delivery_city VARCHAR(80),
    delivery_address VARCHAR(500),
    delivery_postal_code VARCHAR(20),
    reason VARCHAR(500),
    status VARCHAR(40) NOT NULL,
    card_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_card_orders_card FOREIGN KEY (card_id) REFERENCES cards (id),
    CONSTRAINT ck_card_orders_account_number_length CHECK (LENGTH(account_number) = 18)
);

CREATE TABLE card_status_history (
    id UUID PRIMARY KEY,
    card_id UUID NOT NULL,
    user_id UUID NOT NULL,
    profile_id UUID NOT NULL,
    previous_status VARCHAR(40),
    new_status VARCHAR(40) NOT NULL,
    reason VARCHAR(500),
    actor_type VARCHAR(40) NOT NULL,
    actor_id VARCHAR(80),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_card_status_history_card FOREIGN KEY (card_id) REFERENCES cards (id)
);

CREATE INDEX idx_cards_user_profile ON cards (user_id, profile_id);
CREATE INDEX idx_cards_account_number ON cards (account_number);
CREATE INDEX idx_cards_status ON cards (status);
CREATE INDEX idx_card_orders_user_account ON card_orders (user_id, account_number);
CREATE INDEX idx_card_orders_status ON card_orders (status);
CREATE INDEX idx_card_status_history_card ON card_status_history (card_id, created_at);
