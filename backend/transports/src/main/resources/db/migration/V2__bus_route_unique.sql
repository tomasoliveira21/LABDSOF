-- US-41 D4: one external bus route once per event, also for two requests at the same time.
CREATE UNIQUE INDEX transport_option_bus_route ON transport_option (event_id, external_route)
    WHERE mode = 'ExternalBus';
