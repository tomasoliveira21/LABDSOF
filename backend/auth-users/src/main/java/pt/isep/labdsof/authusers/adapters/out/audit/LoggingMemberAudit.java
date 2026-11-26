package pt.isep.labdsof.authusers.adapters.out.audit;

import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.stereotype.Component;
import pt.isep.labdsof.authusers.application.MemberAudit;

/**
 * Logs a team assignment and every refusal at INFO/WARN with pseudonymous values only (US-18 D8, US-14, M-10).
 * The request's correlation id is already in the MDC, so each line carries it.
 */
@Component
class LoggingMemberAudit implements MemberAudit {

  private static final Logger log = LoggerFactory.getLogger(LoggingMemberAudit.class);

  @Override
  public void teamAssigned(UUID actorUserId, UUID eventId, UUID userId, UUID teamId, Instant at) {
    assignment(actorUserId, eventId, userId, teamId, at).log("Added a member to a team (US-18)");
  }

  @Override
  public void teamUnassigned(UUID actorUserId, UUID eventId, UUID userId, UUID teamId, Instant at) {
    assignment(actorUserId, eventId, userId, teamId, at).log("Removed a member from a team (US-18)");
  }

  @Override
  public void refused(UUID actorUserId, UUID eventId, Refusal refusal, Instant at) {
    log.atWarn()
        .addKeyValue("actorUserId", actorUserId.toString())
        .addKeyValue("eventId", eventId.toString())
        .addKeyValue("at", at.toString())
        .addKeyValue("refusal", refusal.name())
        .log("Refused a call about an event's members (US-14)");
  }

  private static LoggingEventBuilder assignment(UUID actorUserId, UUID eventId, UUID userId,
      UUID teamId, Instant at) {
    return log.atInfo()
        .addKeyValue("actorUserId", actorUserId.toString())
        .addKeyValue("eventId", eventId.toString())
        .addKeyValue("userId", userId.toString())
        .addKeyValue("teamId", teamId.toString())
        .addKeyValue("at", at.toString());
  }
}
