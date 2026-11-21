-- Events' own database (ADR-003). Spec: Docs/specs/192-events-service.md, decisions D3 and D4.
-- event_id is on every table that holds event data; the application filters by it on every query
-- (ADR-003 decision 3). Event states follow the proposal of 15 November 2026 §4 (#198 still open).
-- Lengths and the report and point type codes are proposed in contracts/openapi.yaml (spec D6).

CREATE TABLE event (
    event_id        uuid        PRIMARY KEY,
    name            text        NOT NULL
        CONSTRAINT event_name CHECK (char_length(name) BETWEEN 1 AND 100 AND btrim(name) <> ''),
    organiser_name  text        NOT NULL
        CONSTRAINT event_organiser_name
            CHECK (char_length(organiser_name) BETWEEN 1 AND 100 AND btrim(organiser_name) <> ''),
    start_date      date        NOT NULL,
    end_date        date        NOT NULL,
    description     text        NOT NULL DEFAULT ''
        CONSTRAINT event_description CHECK (char_length(description) <= 2000),
    status          text        NOT NULL
        CONSTRAINT event_status CHECK (status IN ('Draft', 'Published', 'Suspended', 'Closed')),
    -- The status a suspended event returns to when reactivated (US-09)
    previous_status text
        CONSTRAINT event_previous_status_value CHECK (previous_status IN ('Draft', 'Published')),
    -- Generated when the event is published (US-11); kept while it is closed or suspended after that
    join_code       text        CONSTRAINT event_join_code_unique UNIQUE,
    version         bigint      NOT NULL CONSTRAINT event_version CHECK (version >= 1),
    created_at      timestamptz NOT NULL,
    created_by      uuid        NOT NULL,
    updated_at      timestamptz NOT NULL,
    updated_by      uuid        NOT NULL,
    CONSTRAINT event_dates CHECK (end_date >= start_date),
    CONSTRAINT event_previous_status_only_suspended CHECK ((status = 'Suspended') = (previous_status IS NOT NULL)),
    CONSTRAINT event_join_code_once_published CHECK (
        (join_code IS NOT NULL) = (status IN ('Published', 'Closed') OR previous_status IS NOT DISTINCT FROM 'Published'))
);

-- Teams are soft-deleted, so TeamChanged can carry deleted: true with a higher version (spec D4).
CREATE TABLE team (
    team_id            uuid        PRIMARY KEY,
    event_id           uuid        NOT NULL REFERENCES event (event_id),
    name               text        NOT NULL
        CONSTRAINT team_name CHECK (char_length(name) BETWEEN 1 AND 100 AND btrim(name) <> ''),
    -- The help categories of decision 8 of 15 November 2026
    handled_categories text[]      NOT NULL DEFAULT '{}'
        CONSTRAINT team_handled_categories
            CHECK (handled_categories <@ ARRAY['Health', 'Safety', 'LostPerson', 'Infrastructure', 'Other']),
    deleted_at         timestamptz,
    version            bigint      NOT NULL CONSTRAINT team_version CHECK (version >= 1),
    created_at         timestamptz NOT NULL,
    created_by         uuid        NOT NULL,
    updated_at         timestamptz NOT NULL,
    updated_by         uuid        NOT NULL,
    CONSTRAINT team_event_team UNIQUE (event_id, team_id)
);
-- A team name is unique in its event ignoring case, among the teams not deleted (US-16)
CREATE UNIQUE INDEX team_name_per_event ON team (event_id, lower(name)) WHERE deleted_at IS NULL;

-- The fixed list of features §4.2; report types (proposed, spec D6) only for Reports.Review.
-- Replaced as a whole by PUT .../permissions; who and when are on the team (updated_by, updated_at).
CREATE TABLE team_permission (
    event_id     uuid   NOT NULL,
    team_id      uuid   NOT NULL,
    permission   text   NOT NULL
        CONSTRAINT team_permission_permission CHECK (permission IN ('HelpRequests.Triage', 'HelpRequests.Respond',
            'Reports.Review', 'Reports.Respond', 'Announcements.Publish', 'Map.Edit', 'Transport.Edit',
            'Entrances.Update')),
    report_types text[] NOT NULL DEFAULT '{}'
        CONSTRAINT team_permission_report_types CHECK (report_types <@ ARRAY['QueueAtEntrance', 'CrowdedArea',
            'WcProblem', 'NoWater', 'Hazard', 'TransportProblem', 'WrongInformation']),
    CONSTRAINT team_permission_report_types_only_review
        CHECK (permission = 'Reports.Review' OR cardinality(report_types) = 0),
    PRIMARY KEY (team_id, permission),
    CONSTRAINT team_permission_team FOREIGN KEY (event_id, team_id)
        REFERENCES team (event_id, team_id) ON DELETE CASCADE
);

-- Every upload is re-encoded to PNG (ADR-007); the file lives in the Events volume and only its
-- reference is here (API outline D6). The original is not kept (it may carry EXIF data).
CREATE TABLE venue_map_version (
    event_id            uuid        NOT NULL REFERENCES event (event_id),
    version             integer     NOT NULL CONSTRAINT venue_map_version_version CHECK (version >= 1),
    image_file          text        NOT NULL CONSTRAINT venue_map_version_image_file CHECK (btrim(image_file) <> ''),
    source_content_type text        NOT NULL
        CONSTRAINT venue_map_version_source_type
            CHECK (source_content_type IN ('image/png', 'image/jpeg', 'application/pdf')),
    -- At most 20 MB (ADR-013 L-4)
    source_size_bytes   bigint      NOT NULL
        CONSTRAINT venue_map_version_source_size CHECK (source_size_bytes BETWEEN 1 AND 20971520),
    uploaded_at         timestamptz NOT NULL,
    uploaded_by         uuid        NOT NULL,
    PRIMARY KEY (event_id, version)
);

-- Normalised position on one map version (ADR-007). Soft-deleted with a version, because
-- PointOfInterestChanged carries deleted (API outline §7.4).
CREATE TABLE point_of_interest (
    poi_id      uuid             PRIMARY KEY,
    event_id    uuid             NOT NULL,
    map_version integer          NOT NULL,
    type        text             NOT NULL
        CONSTRAINT point_of_interest_type
            CHECK (type IN ('Entrance', 'Stage', 'Wc', 'FirstAid', 'Information', 'TransportStop', 'Other')),
    name        text             NOT NULL
        CONSTRAINT point_of_interest_name CHECK (char_length(name) BETWEEN 1 AND 100 AND btrim(name) <> ''),
    x           double precision NOT NULL CONSTRAINT point_of_interest_x CHECK (x BETWEEN 0 AND 1),
    y           double precision NOT NULL CONSTRAINT point_of_interest_y CHECK (y BETWEEN 0 AND 1),
    deleted_at  timestamptz,
    version     bigint           NOT NULL CONSTRAINT point_of_interest_version CHECK (version >= 1),
    updated_at  timestamptz      NOT NULL,
    updated_by  uuid             NOT NULL,
    CONSTRAINT point_of_interest_map_version FOREIGN KEY (event_id, map_version)
        REFERENCES venue_map_version (event_id, version)
);
CREATE INDEX point_of_interest_map ON point_of_interest (event_id, map_version);

-- Local copy of MembershipChanged (Auth & Users). Removed rows are kept, so a late older version
-- cannot bring a removed membership back (spec D3). No copy means no access (ADR-006).
CREATE TABLE membership_copy (
    event_id uuid   NOT NULL,
    user_id  uuid   NOT NULL,
    role     text   NOT NULL CONSTRAINT membership_copy_role CHECK (role IN ('EventAdmin', 'RegularUser')),
    status   text   NOT NULL CONSTRAINT membership_copy_status CHECK (status IN ('Active', 'Removed')),
    team_ids uuid[] NOT NULL,
    version  bigint NOT NULL CONSTRAINT membership_copy_version CHECK (version >= 1),
    PRIMARY KEY (event_id, user_id)
);
CREATE INDEX membership_copy_user ON membership_copy (user_id);

-- Consumed messageIds, so a redelivered event is applied once (ADR-004, spec D2).
CREATE TABLE processed_message (
    message_id   uuid        PRIMARY KEY,
    processed_at timestamptz NOT NULL DEFAULT now()
);

-- Outbox as Reports (#214 D4) and Auth & Users (#191), with the correlation id of the request,
-- sent as the AMQP correlationId by the publisher (F02).
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
