CREATE TABLE badge_progress (
    user_id          uuid    NOT NULL,
    track            text    NOT NULL CHECK (track IN ('ReportHistory', 'EventReporter', 'EventAttendance')),
    event_id         uuid,
    qualifying_count integer NOT NULL CHECK (qualifying_count > 0),
    tier             text    NOT NULL CHECK (tier IN ('Wood', 'Bronze', 'Silver', 'Gold', 'Platinum', 'Diamond')),
    CONSTRAINT badge_progress_scope CHECK ((track = 'EventReporter') = (event_id IS NOT NULL)),
    CONSTRAINT badge_progress_key UNIQUE NULLS NOT DISTINCT (user_id, track, event_id)
);
