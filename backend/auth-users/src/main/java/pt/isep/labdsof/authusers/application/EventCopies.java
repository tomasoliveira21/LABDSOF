package pt.isep.labdsof.authusers.application;

import java.util.Optional;
import java.util.UUID;
import pt.isep.labdsof.authusers.application.MyMemberships.Status;

/** Snapshots are idempotent by entity version, including duplicate and out-of-order deliveries. */
public interface EventCopies {

  void event(UUID eventId, String name, Status status, long version);

  void team(UUID eventId, UUID teamId, String payload, long version);

  /**
   * The event as this service last saw it, which is what US-12 D2 decides a join from; empty when no
   * {@code EventChanged} has arrived for it — indistinguishable, on purpose, from an event that does not exist.
   */
  Optional<Copy> find(UUID eventId);

  /** The name and status of an event, as the copy holds them. */
  record Copy(UUID eventId, String name, Status status) {

    public boolean isPublished() {
      return status == Status.Published;
    }
  }
}
