package pt.isep.labdsof.transports.domain;

import java.util.Objects;
import java.util.UUID;

/** Local copy of EventChanged (Events): only the status Transports decides with (API outline 7.4). */
public record EventCopy(UUID eventId, EventStatus status, long version) {

  public EventCopy {
    Objects.requireNonNull(eventId, "eventId");
    Objects.requireNonNull(status, "status");
    if (version < 1) {
      throw new IllegalArgumentException("version must be at least 1");
    }
  }

  /** Transports refuses a suspended event (API outline 7.4). */
  public boolean isSuspended() {
    return status == EventStatus.Suspended;
  }
}
