-- Local copies of the Events messages consumed from auth-users.events-events.
CREATE TABLE event_copy (
    event_id uuid PRIMARY KEY,
    name text NOT NULL CHECK (length(trim(name)) > 0),
    status text NOT NULL CHECK (status IN ('Draft', 'Published', 'Suspended', 'Closed')),
    version bigint NOT NULL CHECK (version >= 1)
);

-- TeamChanged shares the same queue as EventChanged. Retain its latest payload for the team-management stories.
CREATE TABLE event_team_copy (
    team_id uuid PRIMARY KEY,
    event_id uuid NOT NULL,
    payload jsonb NOT NULL,
    version bigint NOT NULL CHECK (version >= 1)
);
