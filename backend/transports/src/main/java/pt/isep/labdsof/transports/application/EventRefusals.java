package pt.isep.labdsof.transports.application;

import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** US-14 audit metadata for event access refusals. */
final class EventRefusals {

  private static final Logger log = LoggerFactory.getLogger(EventRefusals.class);

  private EventRefusals() {}

  static void record(UUID eventId, UUID userId, Instant at, String reason) {
    log.atWarn()
        .addKeyValue("eventId", eventId.toString())
        .addKeyValue("userId", userId.toString())
        .addKeyValue("at", at.toString())
        .addKeyValue("reason", reason)
        .log("Transport event access refused");
  }
}
