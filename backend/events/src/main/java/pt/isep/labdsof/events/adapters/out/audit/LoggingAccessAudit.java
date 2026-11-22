package pt.isep.labdsof.events.adapters.out.audit;

import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pt.isep.labdsof.events.application.AccessAudit;

/** Logs a refused attempt at WARN with pseudonymous values only (US-14, spec D8, M-10). */
@Component
class LoggingAccessAudit implements AccessAudit {

  private static final Logger log = LoggerFactory.getLogger(LoggingAccessAudit.class);

  @Override
  public void refused(UUID userId, UUID eventId, Instant at, Refusal refusal) {
    log.atWarn()
        .addKeyValue("userId", userId.toString())
        .addKeyValue("eventId", eventId.toString())
        .addKeyValue("at", at.toString())
        .addKeyValue("refusal", refusal.name())
        .log("Refused access to an event (US-14)");
  }
}
