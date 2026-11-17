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
  }
}
