package pt.isep.labdsof.events.adapters.in.web;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import pt.isep.labdsof.events.adapters.in.web.generated.api.EventsApi;
import pt.isep.labdsof.events.adapters.in.web.generated.model.Event;
import pt.isep.labdsof.events.adapters.in.web.generated.model.NewEvent;
import pt.isep.labdsof.events.application.EventQueryService;

@RestController
class EventsController implements EventsApi {

  private final EventQueryService events;

  EventsController(EventQueryService events) {
    this.events = events;
  }

  @Override
  public ResponseEntity<Event> getEvent(UUID eventId) {
    return ResponseEntity.ok(EventResponse.of(events.get(TokenIdentity.currentUserId(), eventId)));
  }

  @Override
  public ResponseEntity<Event> createEvent(NewEvent newEvent) {
    throw new NotYetImplementedException("US-06");
  }

  @Override
  public ResponseEntity<Event> publishEvent(UUID eventId) {
    throw new NotYetImplementedException("US-11");
  }
}
