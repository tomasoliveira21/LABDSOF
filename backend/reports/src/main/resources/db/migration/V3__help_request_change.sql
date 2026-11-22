-- US-35 (#82, spec D3): every member decision on a request, one row per field, with who, when, the old and the new
-- value. The field names and values are those of the history entry in openapi.yaml, which US-28 (#75) serves.
CREATE TABLE help_request_change (
    id            bigserial PRIMARY KEY,
    request_id    uuid        NOT NULL REFERENCES help_request (id) ON DELETE CASCADE,
    event_id      uuid        NOT NULL,
    actor_user_id text        NOT NULL,
    changed_at    timestamptz NOT NULL,
    field         text        NOT NULL CHECK (field IN ('category', 'priority', 'status', 'routedTeamId', 'triage')),
    old_value     text,
    new_value     text
);

CREATE INDEX help_request_change_request ON help_request_change (event_id, request_id, id);
