package pt.isep.labdsof.transports.adapters.out.audit;

import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pt.isep.labdsof.transports.application.AccessAudit;

/** Records event access refusals with the correlation ID carried by the request's MDC. */
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
