CREATE TABLE box_presets (
    id UUID PRIMARY KEY,
    language VARCHAR(20) NOT NULL,
    title VARCHAR(120) NOT NULL,
    image_light_url VARCHAR(500),
    image_dark_url VARCHAR(500),
    image_alt VARCHAR(120),
    sort_order INTEGER NOT NULL,
    is_active BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE boxes (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    profile_id UUID NOT NULL,
    account_number VARCHAR(18) NOT NULL,
    title VARCHAR(120) NOT NULL,
    preset_id UUID,
    image_light_url VARCHAR(500),
    image_dark_url VARCHAR(500),
    image_alt VARCHAR(120),
    status VARCHAR(40) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_boxes_account_number UNIQUE (account_number),
    CONSTRAINT fk_boxes_preset FOREIGN KEY (preset_id) REFERENCES box_presets (id),
    CONSTRAINT ck_boxes_account_number_length CHECK (LENGTH(account_number) = 18)
);

CREATE INDEX idx_box_presets_language_active_sort
    ON box_presets (language, is_active, sort_order);
CREATE INDEX idx_boxes_user_profile_status_created
    ON boxes (user_id, profile_id, status, created_at);

INSERT INTO box_presets (
    id,
    language,
    title,
    image_light_url,
    image_dark_url,
    image_alt,
    sort_order,
    is_active,
    created_at,
    updated_at
) VALUES
    ('10000000-0000-0000-0000-000000000001', 'en-US', 'Emergency fund',
     '/images/boxes/emergency-light.png', '/images/boxes/emergency-dark.png', 'Emergency fund', 10, TRUE,
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('10000000-0000-0000-0000-000000000002', 'en-US', 'Travel',
     '/images/boxes/travel-light.png', '/images/boxes/travel-dark.png', 'Travel', 20, TRUE,
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('10000000-0000-0000-0000-000000000003', 'en-US', 'Shopping',
     '/images/boxes/shopping-light.png', '/images/boxes/shopping-dark.png', 'Shopping', 30, TRUE,
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
