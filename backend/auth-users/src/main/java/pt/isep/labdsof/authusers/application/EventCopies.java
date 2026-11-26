package pt.isep.labdsof.authusers.application;

import java.util.UUID;
import pt.isep.labdsof.authusers.application.MyMemberships.Status;

/** Snapshots are idempotent by entity version, including duplicate and out-of-order deliveries. */
public interface EventCopies {

  void event(UUID eventId, String name, Status status, long version);

  void team(UUID eventId, UUID teamId, String payload, long version);
}
