package pt.isep.labdsof.transports.adapters.in.web;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import pt.isep.labdsof.transports.adapters.in.web.generated.api.BusObservationsApi;
import pt.isep.labdsof.transports.adapters.in.web.generated.model.BusObservations;

@RestController
class BusObservationsController implements BusObservationsApi {

  @Override
  public ResponseEntity<BusObservations> getBusObservations(UUID eventId) {
    throw new NotYetImplementedException("US-41");
  }
}
