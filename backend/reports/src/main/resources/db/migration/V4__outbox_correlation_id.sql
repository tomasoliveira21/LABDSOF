-- #148: the correlation id of the request that wrote the event (plans/local-observability.md §2), sent as the AMQP
-- correlation_id. Null for rows written before this migration or outside a request.
ALTER TABLE outbox ADD COLUMN correlation_id text;
