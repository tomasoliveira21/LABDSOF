package pt.isep.labdsof.transports.adapters.in.web;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import pt.isep.labdsof.transports.adapters.in.web.generated.api.TransportSourceApi;
import pt.isep.labdsof.transports.adapters.in.web.generated.model.TransportSourceStatus;

@RestController
class TransportSourceController implements TransportSourceApi {

  @Override
  public ResponseEntity<TransportSourceStatus> getTransportSourceStatus(UUID eventId) {
    throw new NotYetImplementedException("US-40");
  }
}
