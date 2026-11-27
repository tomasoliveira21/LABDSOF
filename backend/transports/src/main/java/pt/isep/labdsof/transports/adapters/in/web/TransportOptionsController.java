package pt.isep.labdsof.transports.adapters.in.web;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import pt.isep.labdsof.transports.adapters.in.web.generated.api.TransportOptionsApi;
import pt.isep.labdsof.transports.adapters.in.web.generated.model.NewTransportOption;
import pt.isep.labdsof.transports.adapters.in.web.generated.model.TransportMode;
import pt.isep.labdsof.transports.adapters.in.web.generated.model.TransportOption;
import pt.isep.labdsof.transports.application.TransportOptionService;
import pt.isep.labdsof.transports.domain.NewBusRoute;

/**
 * Adds an event's external bus routes (spec US-41 D2); shuttles arrive with US-37. The body is checked before the
 * caller's access (D3), since a 400 about the body tells nothing about the event.
 */
@RestController
class TransportOptionsController implements TransportOptionsApi {

  private final TransportOptionService service;

  TransportOptionsController(TransportOptionService service) {
    this.service = service;
  }

  @Override
  public ResponseEntity<TransportOption> createTransportOption(UUID eventId, NewTransportOption body) {
    var userId = TokenIdentity.userId();
    if (body.mode() == TransportMode.SHUTTLE) {
      service.refuseShuttle(eventId, userId);
    }
    var route = service.addBusRoute(eventId, userId,
        NewBusRoute.of(body.externalRoute(), body.name(), body.stopPoiIds(), !body.departures().isEmpty()));
    var location = URI.create("/api/v1/events/" + eventId + "/transport-options/" + route.optionId());
    return ResponseEntity.created(location).body(new TransportOption(route.optionId(), TransportMode.EXTERNAL_BUS,
        route.name(), route.externalRoute(), List.of(), List.of()));
  }
}
