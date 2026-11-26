package pt.isep.labdsof.events.domain;

import java.util.Objects;
import java.util.UUID;

/** An event; the join code is null until it is published (US-11). */
public record Event(UUID eventId, String name, String organiserName, DateRange dates, String description,
    EventStatus status, String joinCode, long version) {

  public Event {
    Objects.requireNonNull(eventId, "eventId");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(organiserName, "organiserName");
    Objects.requireNonNull(dates, "dates");
    Objects.requireNonNull(description, "description");
    Objects.requireNonNull(status, "status");
    if (version < 1) {
      throw new IllegalArgumentException("version must be at least 1");
    }
  }

  /**
   * The same event published with the join code the participants' link carries (US-11), one version later. Only a
   * draft publishes: the state machine of the domain model has the one {@code Draft -> Published} edge, so a
   * second publish is refused here and never reaches the database (US-11 D2).
   */
  public Event published(String joinCode) {
    Objects.requireNonNull(joinCode, "joinCode");
    if (status != EventStatus.Draft) {
      throw new EventNotDraftException();
    }
    return new Event(eventId, name, organiserName, dates, description, EventStatus.Published, joinCode,
        version + 1);
  }
}
