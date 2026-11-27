-- US-41 D4: one external bus route once per event, also for two requests at the same time.
CREATE UNIQUE INDEX transport_option_bus_route ON transport_option (event_id, external_route)
    WHERE mode = 'ExternalBus';

-- US-41 D6: the routes are listed in the order they were added; last_updated_at moves when US-37 changes a route.
-- When it was added is the time of the insert, so an insert that leaves it out still gets it right.
ALTER TABLE transport_option ADD COLUMN created_at timestamptz NOT NULL DEFAULT now();
