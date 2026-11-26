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
}
