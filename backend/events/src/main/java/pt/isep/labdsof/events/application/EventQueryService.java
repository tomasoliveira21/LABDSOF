package pt.isep.labdsof.events.application;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** GET /events/{eventId}: spec D5 visibility through {@link EventReader}, every refusal recorded (spec D8). */
@Service
public class EventQueryService {

  private final EventReader reader;

  EventQueryService(EventReader reader) {
    this.reader = reader;
  }

  @Transactional(readOnly = true)
  public EventView get(UUID userId, UUID eventId) {
    return EventView.of(reader.of(userId, eventId).event());
  }
}
