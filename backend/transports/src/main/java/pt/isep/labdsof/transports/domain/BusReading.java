package pt.isep.labdsof.transports.domain;

import java.time.Instant;

/**
 * One bus as the source sent it (spec US-40 D7): its {@code position} in the answer (from 0) and whatever could be
 * read; any other field may be missing. {@link BusImport} decides what is broken, late or kept.
 */
public record BusReading(int position, String externalEntityId, String dataProvider, String fleetVehicleId,
    String routeId, Double longitude, Double latitude, Instant observedAt) {}
