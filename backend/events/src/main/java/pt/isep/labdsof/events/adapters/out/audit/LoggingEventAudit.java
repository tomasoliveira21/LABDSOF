package pt.isep.labdsof.events.adapters.out.audit;

import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pt.isep.labdsof.events.application.EventAudit;
import pt.isep.labdsof.events.domain.EventStatus;

/**
 * Logs a publish at INFO with pseudonymous values only (US-11 D7, M-10). The request's correlation id is already
 * in the MDC, so the line carries it. The join code is never logged: nothing downstream needs it, and it is the
 * one value of the row that should not spread through the log pipeline.
 */
@Component
class LoggingEventAudit implements EventAudit {

  private static final Logger log = LoggerFactory.getLogger(LoggingEventAudit.class);

  @Override
  public void published(UUID userId, UUID eventId, Instant at, EventStatus status, long version) {
    log.atInfo()
        .addKeyValue("userId", userId.toString())
        .addKeyValue("eventId", eventId.toString())
        .addKeyValue("at", at.toString())
        .addKeyValue("status", status.name())
        .addKeyValue("version", version)
        .log("Published an event (US-11)");
  }
}
