package pt.isep.labdsof.events.adapters.in.web;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import pt.isep.labdsof.events.adapters.in.web.generated.api.EventsApi;
import pt.isep.labdsof.events.adapters.in.web.generated.model.Event;
import pt.isep.labdsof.events.adapters.in.web.generated.model.EventSummary;
import pt.isep.labdsof.events.adapters.in.web.generated.model.NewEvent;
import pt.isep.labdsof.events.application.EventQueryService;
import pt.isep.labdsof.events.application.EventService;
import pt.isep.labdsof.events.domain.DateRange;

@RestController
class EventsController implements EventsApi {

  private final EventQueryService events;
  private final EventService service;

  EventsController(EventQueryService events, EventService service) {
    this.events = events;
    this.service = service;
  }

  @Override
  public ResponseEntity<Event> getEvent(UUID eventId) {
    return ResponseEntity.ok(EventResponse.of(events.get(TokenIdentity.currentUserId(), eventId)));
  }

  /**
   * The draft as its creator sees it once (US-06): a {@code 201} with no join link, because only the publish
   * generates a join code. The FarolAdmin reads it back through {@link #listEvents}, never through the event.
   */
  @Override
  public ResponseEntity<Event> createEvent(NewEvent newEvent) {
    var created = service.create(TokenIdentity.currentUserId(), newEvent.name(), newEvent.organiserName(),
        new DateRange(newEvent.dates().start(), newEvent.dates().end()), newEvent.description());
    return ResponseEntity.status(HttpStatus.CREATED).body(EventResponse.of(created));
  }

  /** Every event with the five fields §4.1 allows the FarolAdmin (US-07). */
  @Override
  public ResponseEntity<List<EventSummary>> listEvents() {
    return ResponseEntity.ok(service.list(TokenIdentity.currentUserId()).stream()
        .map(EventResponse::summaryOf)
        .toList());
  }

  /** The published event with its join link, the same body GET answers (US-11 D6). */
  @Override
  public ResponseEntity<Event> publishEvent(UUID eventId) {
    return ResponseEntity.ok(EventResponse.of(service.publish(TokenIdentity.currentUserId(), eventId)));
  }
}
