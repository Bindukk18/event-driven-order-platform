-- Event-driven order lab: per-service state, inbox, outbox, dead letters.
-- PostgreSQL is required for FOR UPDATE SKIP LOCKED.

CREATE TABLE orders (
    order_id UUID PRIMARY KEY,
    customer_id VARCHAR(128) NOT NULL,
    sku VARCHAR(64) NOT NULL,
    quantity INTEGER NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    status VARCHAR(32) NOT NULL,
    last_applied_version INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE inventory_reservation (
    reservation_id UUID PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE,
    sku VARCHAR(64) NOT NULL,
    quantity INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE payment_attempt (
    attempt_id UUID PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE,
    source_event_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    last_error VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE shipment (
    shipment_id UUID PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE outbox_event (
    event_id UUID PRIMARY KEY,
    topic VARCHAR(64) NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id UUID NOT NULL,
    aggregate_version INTEGER NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    event_version INTEGER NOT NULL,
    correlation_id VARCHAR(128) NOT NULL,
    causation_id UUID,
    payload TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    claimed_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    last_error VARCHAR(512)
);

CREATE INDEX idx_outbox_relay
    ON outbox_event (status, next_attempt_at, created_at);

CREATE TABLE consumer_inbox (
    consumer_name VARCHAR(128) NOT NULL,
    event_id UUID NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (consumer_name, event_id)
);

CREATE TABLE dead_letter (
    dead_letter_id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    consumer_name VARCHAR(128) NOT NULL,
    original_event TEXT NOT NULL,
    attempt_count INTEGER NOT NULL,
    reason VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_dead_letter_consumer
    ON dead_letter (consumer_name, created_at);
