package pt.isep.labdsof.events.application;

import java.time.Instant;
import java.util.UUID;

/**
 * Port. US-14, spec D8: a refused attempt on an event, with the user, the event and the time; never a name
 * or e-mail.
 */
public interface AccessAudit {

  enum Refusal { NO_SUCH_EVENT, NOT_A_MEMBER, NOT_VISIBLE, NO_SUCH_TEAM }

  void refused(UUID userId, UUID eventId, Instant at, Refusal refusal);
}
