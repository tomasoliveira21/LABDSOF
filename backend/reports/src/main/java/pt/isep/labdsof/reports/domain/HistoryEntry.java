package pt.isep.labdsof.reports.domain;

import java.time.Instant;

/** One recorded change of a request, as GET .../history returns it (US-28, spec D4; openapi.yaml). */
public record HistoryEntry(String field, String oldValue, String newValue, String actorUserId, String actorName,
    Instant changedAt) {}
