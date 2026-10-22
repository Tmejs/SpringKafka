CREATE TABLE processed_fund_order_events (
    event_id UUID PRIMARY KEY,
    schema_version INTEGER NOT NULL CHECK (schema_version = 1),
    order_id UUID NOT NULL,
    partner_code VARCHAR(32) NOT NULL,
    fund_isin VARCHAR(12) NOT NULL,
    status VARCHAR(16) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    payload_hash CHAR(64) NOT NULL
);

CREATE INDEX processed_fund_order_events_page_idx
    ON processed_fund_order_events (occurred_at DESC, event_id DESC);
