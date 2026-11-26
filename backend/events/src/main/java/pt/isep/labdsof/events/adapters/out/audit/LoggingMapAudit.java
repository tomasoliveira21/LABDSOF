package pt.isep.labdsof.events.adapters.out.audit;

import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pt.isep.labdsof.events.application.MapAudit;
import pt.isep.labdsof.events.domain.MapSourceType;

/**
 * Logs a map upload at INFO with pseudonymous values only (US-19 D11, M-10). The request's correlation id is
 * already in the MDC, so the line carries it. Neither the file name nor the file itself is logged: the event and
 * the version say which map it is, and the content is the one thing that must never reach the log pipeline.
 */
@Component
class LoggingMapAudit implements MapAudit {

  private static final Logger log = LoggerFactory.getLogger(LoggingMapAudit.class);

  @Override
  public void uploaded(UUID userId, UUID eventId, Instant at, int version, MapSourceType sourceType,
      long sourceSizeBytes) {
    log.atInfo()
        .addKeyValue("userId", userId.toString())
        .addKeyValue("eventId", eventId.toString())
        .addKeyValue("at", at.toString())
        .addKeyValue("mapVersion", version)
        .addKeyValue("sourceContentType", sourceType.contentType())
        .addKeyValue("sourceSizeBytes", sourceSizeBytes)
        .log("Uploaded a venue map version (US-19)");
  }
}
