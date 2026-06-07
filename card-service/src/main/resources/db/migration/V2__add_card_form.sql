ALTER TABLE cards
    ADD COLUMN form VARCHAR(40) NOT NULL DEFAULT 'PHYSICAL';

ALTER TABLE card_orders
    ADD CONSTRAINT uk_card_orders_card_id UNIQUE (card_id);

CREATE INDEX idx_cards_form ON cards (form);
