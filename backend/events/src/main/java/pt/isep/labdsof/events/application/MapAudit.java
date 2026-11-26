package pt.isep.labdsof.events.application;

import java.time.Instant;
import java.util.UUID;
import pt.isep.labdsof.events.domain.MapSourceType;

/**
 * Port. US-19 D11: an upload is recorded with who made it and when. The row keeps {@code uploaded_by} and
 * {@code uploaded_at} and {@code VenueMapVersionPublished} tells the other services the version exists; this
 * writes the line that makes it visible in the logs — with the pseudonymous {@code userId} only, never a name or
 * an e-mail (M-10), and **never the file name or the file itself**.
 */
public interface MapAudit {

  void uploaded(UUID userId, UUID eventId, Instant at, int version, MapSourceType sourceType, long sourceSizeBytes);
}
