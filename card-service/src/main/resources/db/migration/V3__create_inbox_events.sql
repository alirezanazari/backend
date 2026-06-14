CREATE TABLE inbox_events (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    event_type VARCHAR(120) NOT NULL,
    consumer_name VARCHAR(120) NOT NULL,
    status VARCHAR(40) NOT NULL,
    result_payload TEXT,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_card_inbox_event_consumer UNIQUE (event_id, consumer_name)
);

CREATE INDEX idx_card_inbox_status_received ON inbox_events (status, received_at);
