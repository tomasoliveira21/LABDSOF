CREATE TABLE outbox (
    id              bigserial   PRIMARY KEY,
    message_id      uuid        NOT NULL UNIQUE,
    exchange        text        NOT NULL,
    routing_key     text        NOT NULL,
    type            text        NOT NULL,
    body            jsonb       NOT NULL,
    correlation_id  text,
    created_at      timestamptz NOT NULL DEFAULT now(),
    attempts        integer     NOT NULL DEFAULT 0,
    next_attempt_at timestamptz NOT NULL DEFAULT now(),
    published_at    timestamptz,
    last_error      text
);
CREATE INDEX outbox_due ON outbox (next_attempt_at, id) WHERE published_at IS NULL;
