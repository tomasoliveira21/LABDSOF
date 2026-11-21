package pt.isep.labdsof.events.adapters.in.web;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import pt.isep.labdsof.events.adapters.in.web.generated.model.DateRange;
import pt.isep.labdsof.events.adapters.in.web.generated.model.Event;
import pt.isep.labdsof.events.adapters.in.web.generated.model.EventStatus;
import pt.isep.labdsof.events.application.EventView;

/** Maps the use case's {@link EventView} to the contract's Event. No business rule here. */
final class EventResponse {

  /** Web app route that joins an event (US-12); the web app makes it absolute with its own origin for the QR code. */
  static final String JOIN_LINK = "/join/%s?code=%s";

  private EventResponse() {}

  static Event of(EventView view) {
    return new Event(view.eventId(), view.name(), view.organiserName(),
        new DateRange(view.dates().start(), view.dates().end()), view.description(),
        EventStatus.fromValue(view.status().name()),
        view.joinCode() == null ? null : JOIN_LINK.formatted(view.eventId(),
            URLEncoder.encode(view.joinCode(), StandardCharsets.UTF_8)));
  }
}
