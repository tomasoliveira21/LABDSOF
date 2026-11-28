package pt.isep.labdsof.events.domain;

import java.util.Objects;
import java.util.UUID;

/** An event; the join code is null until it is published (US-11). */
public record Event(UUID eventId, String name, String organiserName, DateRange dates, String description,
    EventStatus status, String joinCode, long version) {

  /** The database's event_name and event_organiser_name checks: 1 to 100 characters once trimmed. */
  public static final int MAX_NAME_LENGTH = 100;

  /** The database's event_description check. */
  public static final int MAX_DESCRIPTION_LENGTH = 2000;

  public Event {
    Objects.requireNonNull(eventId, "eventId");
    name = bounded(name, "a name");
    organiserName = bounded(organiserName, "an organiser name");
    Objects.requireNonNull(dates, "dates");
    description = boundedDescription(description);
    Objects.requireNonNull(status, "status");
    if (version < 1) {
      throw new IllegalArgumentException("version must be at least 1");
    }
  }

  /**
   * A new event as US-06 creates it: a draft, version 1, with no join code, which only the publish generates
   * (US-11 D1). The creator is deliberately not made a member of it — the first EventAdmin is named in
   * Auth &amp; Users (US-06 D3), so a FarolAdmin reads this event back only through the list of US-07.
   *
   * <p>A null description is read as the empty string, which is what the column's default and the contract's
   * {@code Event.description} (*empty when none was given*) both say.
   */
  public static Event created(UUID eventId, String name, String organiserName, DateRange dates,
      String description) {
    return new Event(eventId, name, organiserName, dates, description == null ? "" : description,
        EventStatus.Draft, null, 1);
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

  private static String bounded(String value, String what) {
    var trimmed = Objects.requireNonNull(value, what).trim();
    if (trimmed.isEmpty() || trimmed.length() > MAX_NAME_LENGTH) {
      throw new InvalidEventException(what + " has 1 to " + MAX_NAME_LENGTH + " characters");
    }
    return trimmed;
  }

  private static String boundedDescription(String description) {
    Objects.requireNonNull(description, "description");
    if (description.length() > MAX_DESCRIPTION_LENGTH) {
      throw new InvalidEventException("a description has at most " + MAX_DESCRIPTION_LENGTH + " characters");
    }
    return description;
  }
}
