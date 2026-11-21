package pt.isep.labdsof.events.application;

import java.util.Optional;
import java.util.UUID;
import pt.isep.labdsof.events.domain.Event;

/** Port: the events of this service's own database. */
public interface Events {

  Optional<Event> findById(UUID eventId);
}
