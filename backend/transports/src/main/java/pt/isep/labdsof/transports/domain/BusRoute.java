package pt.isep.labdsof.transports.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * An external bus route of an event (spec US-41 D2): a {@code TransportOption} of mode {@code ExternalBus}, matched to
 * the buses by the source's route number. {@code name} may be missing.
 */
public record BusRoute(UUID optionId, UUID eventId, String externalRoute, String name, Instant addedAt,
    UUID addedBy) {}
