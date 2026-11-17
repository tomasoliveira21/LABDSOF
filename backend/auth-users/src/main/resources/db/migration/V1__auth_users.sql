-- Auth & Users' own database (ADR-003). Spec: Docs/specs/191-auth-users-service.md, decision D3.
-- user_id is the token's sub (a UUID, D4). States follow the proposal of 15 November 2026 §4 (#198 still open).

CREATE TABLE user_account (
    user_id      uuid        PRIMARY KEY,
    email        text        NOT NULL,
    display_name text        NOT NULL
        CONSTRAINT user_account_display_name_length CHECK (char_length(display_name) BETWEEN 1 AND 100),
    status       text        NOT NULL
        CONSTRAINT user_account_status CHECK (status IN ('Active', 'Disabled', 'DeletionPending')),
    version      bigint      NOT NULL CONSTRAINT user_account_version CHECK (version >= 1),
    created_at   timestamptz NOT NULL,
    updated_at   timestamptz NOT NULL
);

-- Only the sub and when it was deleted (API outline §2): refuses tokens issued before the deletion.
CREATE TABLE user_tombstone (
    user_id    uuid        PRIMARY KEY,
    deleted_at timestamptz NOT NULL
);

-- granted_by NULL: granted by the system (bootstrap FarolAdmin, D6).
CREATE TABLE global_role_grant (
    user_id    uuid        NOT NULL REFERENCES user_account (user_id) ON DELETE CASCADE,
    role       text        NOT NULL CONSTRAINT global_role_grant_role CHECK (role IN ('FarolAdmin')),
    version    bigint      NOT NULL CONSTRAINT global_role_grant_version CHECK (version >= 1),
    granted_at timestamptz NOT NULL,
    granted_by uuid,
    PRIMARY KEY (user_id, role)
);

-- No MembershipStatus (proposal of 15 November 2026 §4): a removed member has no row.
CREATE TABLE event_membership (
    event_id   uuid        NOT NULL,
    user_id    uuid        NOT NULL REFERENCES user_account (user_id) ON DELETE CASCADE,
    role       text        NOT NULL CONSTRAINT event_membership_role CHECK (role IN ('EventAdmin', 'RegularUser')),
    version    bigint      NOT NULL CONSTRAINT event_membership_version CHECK (version >= 1),
    created_at timestamptz NOT NULL,
    created_by uuid,
    updated_at timestamptz NOT NULL,
    updated_by uuid,
    PRIMARY KEY (event_id, user_id)
);
CREATE INDEX event_membership_user ON event_membership (user_id);

CREATE TABLE team_assignment (
    event_id    uuid        NOT NULL,
    user_id     uuid        NOT NULL,
    team_id     uuid        NOT NULL,
    assigned_at timestamptz NOT NULL,
    assigned_by uuid,
    PRIMARY KEY (event_id, user_id, team_id),
    CONSTRAINT team_assignment_membership FOREIGN KEY (event_id, user_id)
        REFERENCES event_membership (event_id, user_id) ON DELETE CASCADE
);

-- Outbox as Reports (#214 D4), plus the request's correlation id (D8), sent as the AMQP correlationId by F02.
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
