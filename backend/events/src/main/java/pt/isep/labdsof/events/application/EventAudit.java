package pt.isep.labdsof.events.application;

import java.time.Instant;
import java.util.UUID;
import pt.isep.labdsof.events.domain.EventStatus;

/**
 * Port. US-11 D7: a status change of an event is recorded with who made it and when. The event row keeps
 * {@code updated_by} and {@code updated_at} and {@code EventChanged} carries the new state; this writes the line
 * that makes it visible in the logs — with the pseudonymous {@code userId} only, never a name or an e-mail
 * (M-10), and **never the join code**.
 */
public interface EventAudit {

  void published(UUID userId, UUID eventId, Instant at, EventStatus status, long version);

  /** US-06: the FarolAdmin created an event. The name is never logged (M-10), only the id. */
  void created(UUID userId, UUID eventId, Instant at);

  /**
   * US-06 AC-2 and US-07: a caller who is not the FarolAdmin was refused a platform operation. There is no event
   * to key it on, so this is the one refusal {@link AccessAudit} cannot record.
   */
  void notPlatformAdmin(UUID userId, Instant at, String operation);
}
