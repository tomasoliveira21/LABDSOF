-- US-29 (#76, spec D4): the field tasks, as API outline §4.1 describes them. A help request is the only target
-- until US-52 adds reports. One live (Open or Accepted) task per request: routing again cancels the Open one first.
CREATE TABLE field_task (
    id              uuid PRIMARY KEY,
    event_id        uuid        NOT NULL,
    help_request_id uuid        NOT NULL REFERENCES help_request (id) ON DELETE CASCADE,
    team_id         uuid        NOT NULL,
    purpose         text        NOT NULL CHECK (purpose IN ('Assist', 'Verify', 'Fix')),
    status          text        NOT NULL CHECK (status IN ('Open', 'Accepted', 'Answered', 'Resolved', 'Cancelled')),
    accepted_by     text,
    dispatched_at   timestamptz NOT NULL,
    updated_at      timestamptz NOT NULL,
    version         bigint      NOT NULL CHECK (version >= 1)
);

CREATE UNIQUE INDEX field_task_one_live_per_request ON field_task (help_request_id)
    WHERE status IN ('Open', 'Accepted');

-- The team's list of its tasks (US-31) and a team's deletion (spec D6) read by team
CREATE INDEX field_task_event_team ON field_task (event_id, team_id, updated_at);
