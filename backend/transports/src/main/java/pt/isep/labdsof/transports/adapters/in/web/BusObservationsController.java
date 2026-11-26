package pt.isep.labdsof.transports.adapters.in.web;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import pt.isep.labdsof.transports.adapters.in.web.generated.api.BusObservationsApi;
import pt.isep.labdsof.transports.adapters.in.web.generated.model.BusObservation;
import pt.isep.labdsof.transports.adapters.in.web.generated.model.BusObservations;
import pt.isep.labdsof.transports.adapters.in.web.generated.model.BusRouteSummary;
import pt.isep.labdsof.transports.adapters.in.web.generated.model.GeoPosition;
import pt.isep.labdsof.transports.application.BusObservationService;
import pt.isep.labdsof.transports.domain.BusView;

/** The buses on the event's routes near the venue (spec US-41 AC-1, D5 to D8). */
@RestController
class BusObservationsController implements BusObservationsApi {

  private final BusObservationService service;

  BusObservationsController(BusObservationService service) {
    this.service = service;
  }

  @Override
  public ResponseEntity<BusObservations> getBusObservations(UUID eventId) {
    var view = service.view(eventId, TokenIdentity.userId());
    var routes = view.routes().stream()
        .map(route -> new BusRouteSummary(route.externalRoute(), route.name(), route.busCount())).toList();
    var buses = view.buses().stream().map(BusObservationsController::bus).toList();
    var dataAsOf = view.dataAsOf().map(at -> OffsetDateTime.ofInstant(at, ZoneOffset.UTC)).orElse(null);
    return ResponseEntity.ok(new BusObservations(new GeoPosition(view.venue().longitude(), view.venue().latitude()),
        routes, dataAsOf, view.stale(), buses));
  }

  private static BusObservation bus(BusView.ShownBus shown) {
    var bus = shown.bus();
    return new BusObservation(bus.externalEntityId(), bus.fleetVehicleId(), bus.routeId(),
        new GeoPosition(bus.longitude(), bus.latitude()), OffsetDateTime.ofInstant(bus.observedAt(), ZoneOffset.UTC),
        shown.distanceMetres());
  }
}
