-- US-06 / US-07 (#61, #89): the local copy of the platform roles Auth & Users publishes.
--
-- Events already received GlobalRoleChanged — the binding farol.auth-users -> events.auth-users-events with
-- routing key global-role.changed exists since #212 — and acknowledged it as a type it did not handle yet. It is
-- handled now, because the only thing that makes a caller the FarolAdmin of POST /events and GET /events is this
-- copy, never the token (ADR-006, US-06 D1).
--
-- Applied exactly as membership_copy is (#192 D2): full state with a version, so a replayed or reordered message
-- is decided by the version alone. A revoke keeps the row with granted = false rather than deleting it, so a
-- later message carrying an older granted = true is still rejected by the version guard (US-06 D2).
CREATE TABLE global_role_copy (
    user_id uuid    NOT NULL,
    role    text    NOT NULL CONSTRAINT global_role_copy_role CHECK (role IN ('FarolAdmin')),
    granted boolean NOT NULL,
    version bigint  NOT NULL CONSTRAINT global_role_copy_version CHECK (version >= 1),
    PRIMARY KEY (user_id, role)
);

-- The gate reads "is this user a FarolAdmin?" on every create and every list, and nothing else reads the table.
CREATE INDEX global_role_copy_granted ON global_role_copy (role, user_id) WHERE granted;
