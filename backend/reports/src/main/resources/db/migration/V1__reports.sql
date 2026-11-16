-- Reports' own database (ADR-003). Spec: Docs/specs/194-reports-postgres-outbox.md, decisions D2, D4, D7.
-- Every table holding event data has event_id; the application filters by it on every query (ADR-003 decision 3).

CREATE TABLE help_request (
    id                uuid PRIMARY KEY,
    event_id          uuid         NOT NULL,
    requester_id      text         NOT NULL,
    client_request_id uuid         NOT NULL,
    description       varchar(500) NOT NULL CHECK (length(btrim(description)) > 0),
    map_version       integer      NOT NULL CHECK (map_version >= 1),
    x                 double precision NOT NULL CHECK (x BETWEEN 0 AND 1),
    y                 double precision NOT NULL CHECK (y BETWEEN 0 AND 1),
    -- Decision 8 of 15 November 2026
    category          text CHECK (category IN ('Health', 'Safety', 'LostPerson', 'Infrastructure', 'Other')),
    -- Decision 2 of 15 November 2026
    priority          text CHECK (priority IN ('Low', 'Medium', 'High', 'Critical')),
    -- Decision 6 of 15 November 2026 (#177)
    status            text         NOT NULL CHECK (status IN ('Submitted', 'Seen', 'OnTheWay', 'Resolved', 'Closed')),
    default_team_id   uuid,
    routed_team_id    uuid,
    submitted_at      timestamptz  NOT NULL,
    updated_at        timestamptz  NOT NULL,
    version           bigint       NOT NULL CHECK (version >= 1),
    -- A retry with the same clientRequestId returns the saved request (API outline §4, US-25)
    CONSTRAINT help_request_client_request UNIQUE (event_id, requester_id, client_request_id)
);

CREATE INDEX help_request_event_updated ON help_request (event_id, updated_at);

-- One triage per request. NotTriaged is never stored: it is derived on read (decision 6).
CREATE TABLE triage_suggestion (
    request_id         uuid PRIMARY KEY REFERENCES help_request (id) ON DELETE CASCADE,
    event_id           uuid NOT NULL,
    state              text NOT NULL CHECK (state IN ('Pending', 'Suggested', 'Accepted', 'Changed')),
    suggested_category text CHECK (suggested_category IN ('Health', 'Safety', 'LostPerson', 'Infrastructure', 'Other')),
    suggested_priority text CHECK (suggested_priority IN ('Low', 'Medium', 'High', 'Critical')),
    suggested_reason   varchar(200),
    not_triaged_reason text CHECK (not_triaged_reason IN ('Timeout', 'ProviderError', 'InvalidOutput', 'Abstained', 'Unavailable')),
    updated_at         timestamptz NOT NULL
);

-- Outbox (ADR-004 decision 3): written in the business transaction, published by OutboxPublisher.
CREATE TABLE outbox (
    id              bigserial PRIMARY KEY,
    message_id      uuid        NOT NULL UNIQUE,
    exchange        text        NOT NULL,
    routing_key     text        NOT NULL,
    type            text        NOT NULL,
    body            jsonb       NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    attempts        integer     NOT NULL DEFAULT 0,
    next_attempt_at timestamptz NOT NULL DEFAULT now(),
    published_at    timestamptz,
    last_error      text
);

CREATE INDEX outbox_due ON outbox (next_attempt_at, id) WHERE published_at IS NULL;

-- Consumed messageIds, so a redelivered event is applied once (ADR-004).
CREATE TABLE processed_message (
    message_id   uuid PRIMARY KEY,
    processed_at timestamptz NOT NULL DEFAULT now()
);

-- Local copy of MembershipChanged (Auth & Users).
CREATE TABLE membership_copy (
    event_id uuid    NOT NULL,
    user_id  text    NOT NULL,
    role     text    NOT NULL CHECK (role IN ('EventAdmin', 'RegularUser')),
    status   text    NOT NULL CHECK (status IN ('Active', 'Removed')),
    team_ids uuid[]  NOT NULL,
    version  bigint  NOT NULL CHECK (version >= 1),
    PRIMARY KEY (event_id, user_id)
);

-- Local copy of TeamChanged (Events).
CREATE TABLE team_copy (
    team_id            uuid PRIMARY KEY,
    event_id           uuid    NOT NULL,
    name               text    NOT NULL,
    permissions        jsonb   NOT NULL,
    handled_categories text[]  NOT NULL,
    deleted            boolean NOT NULL,
    version            bigint  NOT NULL CHECK (version >= 1)
);

CREATE INDEX team_copy_event ON team_copy (event_id);
