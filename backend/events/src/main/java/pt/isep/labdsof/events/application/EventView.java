package pt.isep.labdsof.events.application;

import java.util.UUID;
import pt.isep.labdsof.events.domain.DateRange;
import pt.isep.labdsof.events.domain.Event;
import pt.isep.labdsof.events.domain.EventStatus;

/**
 * What a reader of an event gets. {@code joinCode} is null unless the event is Published; F03 turns it
 * into joinLink.
 */
public record EventView(UUID eventId, String name, String organiserName, DateRange dates, String description,
    EventStatus status, String joinCode) {

  static EventView of(Event event) {
    var joinCode = event.status() == EventStatus.Published ? event.joinCode() : null;
    return new EventView(event.eventId(), event.name(), event.organiserName(), event.dates(),
        event.description(), event.status(), joinCode);
  }
}
