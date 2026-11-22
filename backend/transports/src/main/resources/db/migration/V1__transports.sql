-- Transports schema (spec Docs/specs/193-transports-service.md, D4). One database, one owner (ADR-003);
-- event_id on every table that holds event data, so every query can filter by event.

-- An event's transport option: a shuttle entered by the team, or an external bus route matched by its
-- reference (domain model). No origin column: the mode decides it.
CREATE TABLE transport_option (
    option_id       uuid        PRIMARY KEY,
    event_id        uuid        NOT NULL,
    mode            text        NOT NULL CONSTRAINT transport_option_mode CHECK (mode IN ('Shuttle', 'ExternalBus')),
    name            text        CONSTRAINT transport_option_name
                                CHECK (char_length(name) BETWEEN 1 AND 100 AND name ~ '\S'),
    external_route  text        CONSTRAINT transport_option_external_route
                                CHECK (char_length(external_route) BETWEEN 1 AND 20 AND external_route ~ '\S'),
    stop_poi_ids    uuid[]      NOT NULL DEFAULT '{}',
    version         bigint      NOT NULL CONSTRAINT transport_option_version CHECK (version >= 1),
    last_updated_at timestamptz NOT NULL,
    last_updated_by uuid        NOT NULL,
    CONSTRAINT transport_option_mode_fields CHECK ((mode <> 'ExternalBus' OR external_route IS NOT NULL)
                                                   AND (mode <> 'Shuttle' OR name IS NOT NULL)),
    CONSTRAINT transport_option_event UNIQUE (option_id, event_id)
);
CREATE INDEX transport_option_by_event ON transport_option (event_id);

-- A departure of an option, always of the same event (the composite key refuses another event's option).
CREATE TABLE transport_departure (
    departure_id uuid        PRIMARY KEY,
    option_id    uuid        NOT NULL,
    event_id     uuid        NOT NULL,
    destination  text        NOT NULL CONSTRAINT transport_departure_destination
                             CHECK (char_length(destination) BETWEEN 1 AND 100 AND destination ~ '\S'),
    scheduled_at timestamptz NOT NULL,
    expected_at  timestamptz,
    CONSTRAINT transport_departure_option FOREIGN KEY (option_id, event_id)
        REFERENCES transport_option (option_id, event_id) ON DELETE CASCADE
);
CREATE INDEX transport_departure_by_option ON transport_departure (option_id, scheduled_at);

-- External bus data spans events (domain model), so it has no event_id. Filled by US-40.
CREATE TABLE bus_vehicle (
    vehicle_id         uuid PRIMARY KEY,
    data_provider      text NOT NULL CONSTRAINT bus_vehicle_data_provider CHECK (data_provider ~ '\S'),
    external_entity_id text NOT NULL CONSTRAINT bus_vehicle_external_entity_id CHECK (external_entity_id ~ '\S'),
    fleet_vehicle_id   text,
    CONSTRAINT bus_vehicle_external_entity UNIQUE (data_provider, external_entity_id)
);

-- The latest valid observation of each vehicle; geographic, not venue-map, coordinates.
CREATE TABLE bus_observation (
    vehicle_id   uuid             PRIMARY KEY REFERENCES bus_vehicle ON DELETE CASCADE,
    longitude    double precision NOT NULL CONSTRAINT bus_observation_longitude CHECK (longitude BETWEEN -180 AND 180),
    latitude     double precision NOT NULL CONSTRAINT bus_observation_latitude CHECK (latitude BETWEEN -90 AND 90),
    route_id     text             NOT NULL CONSTRAINT bus_observation_route_id CHECK (route_id ~ '\S'),
    observed_at  timestamptz      NOT NULL,
    retrieved_at timestamptz      NOT NULL
);

-- One row per attempt to read the source (spec D5): what US-43 and the import metrics read later.
CREATE TABLE source_import (
    import_id     bigserial   PRIMARY KEY,
    started_at    timestamptz NOT NULL,
    finished_at   timestamptz NOT NULL,
    outcome       text        NOT NULL CONSTRAINT source_import_outcome
                              CHECK (outcome IN ('Succeeded', 'Unavailable', 'Invalid')),
    vehicle_count integer     CONSTRAINT source_import_vehicle_count CHECK (vehicle_count >= 0),
    error         text        CONSTRAINT source_import_error CHECK (char_length(error) <= 500),
    CONSTRAINT source_import_finished_at CHECK (finished_at >= started_at),
    CONSTRAINT source_import_outcome_fields CHECK (CASE WHEN outcome = 'Succeeded'
                                                        THEN vehicle_count IS NOT NULL AND error IS NULL
                                                        ELSE vehicle_count IS NULL AND error IS NOT NULL END)
);
CREATE INDEX source_import_latest ON source_import (outcome, finished_at DESC);

-- Local copies (spec D3, ADR-004): memberships from Auth & Users, event status and teams from Events.
-- Removed memberships are kept, so a late older version cannot bring one back (#192 D3).
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

CREATE TABLE event_copy (
    event_id uuid   PRIMARY KEY,
    status   text   NOT NULL CONSTRAINT event_copy_status CHECK (status IN ('Draft', 'Published', 'Suspended', 'Closed')),
    version  bigint NOT NULL CONSTRAINT event_copy_version CHECK (version >= 1)
);

-- Only what Transports decides with: the permissions (features.md 4.2) and whether the team was deleted.
CREATE TABLE team_copy (
    team_id     uuid    PRIMARY KEY,
    event_id    uuid    NOT NULL,
    permissions text[]  NOT NULL CONSTRAINT team_copy_permissions CHECK (permissions <@ ARRAY[
                            'HelpRequests.Triage', 'HelpRequests.Respond', 'Reports.Review', 'Reports.Respond',
                            'Announcements.Publish', 'Map.Edit', 'Transport.Edit', 'Entrances.Update']::text[]),
    deleted     boolean NOT NULL,
    version     bigint  NOT NULL CONSTRAINT team_copy_version CHECK (version >= 1)
);
CREATE INDEX team_copy_by_event ON team_copy (event_id);

-- Consumed messageIds, so a redelivered event is applied once (ADR-004, spec D3).
CREATE TABLE processed_message (
    message_id   uuid        PRIMARY KEY,
    processed_at timestamptz NOT NULL DEFAULT now()
);
