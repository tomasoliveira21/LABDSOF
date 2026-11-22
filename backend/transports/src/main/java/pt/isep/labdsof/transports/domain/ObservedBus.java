package pt.isep.labdsof.transports.domain;

import java.time.Instant;

/**
 * A bus of a valid import (spec US-40 D2, D7): geographic coordinates, separate from venue-map positions (domain
 * model). Only {@link BusImport} builds one; {@code fleetVehicleId} may be missing.
 */
public record ObservedBus(String externalEntityId, String dataProvider, String fleetVehicleId, String routeId,
    double longitude, double latitude, Instant observedAt) {}
