package pt.isep.labdsof.events.adapters.in.web;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import pt.isep.labdsof.events.adapters.in.web.generated.api.EventsApi;
import pt.isep.labdsof.events.adapters.in.web.generated.model.Event;
import pt.isep.labdsof.events.adapters.in.web.generated.model.NewEvent;
import pt.isep.labdsof.events.application.EventQueryService;
import pt.isep.labdsof.events.application.EventService;

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

  @Override
  public ResponseEntity<Event> createEvent(NewEvent newEvent) {
    throw new NotYetImplementedException("US-06");
  }

  /** The published event with its join link, the same body GET answers (US-11 D6). */
  @Override
  public ResponseEntity<Event> publishEvent(UUID eventId) {
    return ResponseEntity.ok(EventResponse.of(service.publish(TokenIdentity.currentUserId(), eventId)));
  }
}
