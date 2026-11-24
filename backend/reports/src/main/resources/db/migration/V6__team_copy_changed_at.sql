-- When Reports last applied a TeamChanged to each team copy (spec US-34-default-team D6): a poll of the live list
-- after a team change lists the event's open requests again, so their default team is current. V6, not V5, so this
-- does not clash with the V5 of PR #245; existing rows get the time of the migration.
ALTER TABLE team_copy ADD COLUMN changed_at timestamptz NOT NULL DEFAULT now();
