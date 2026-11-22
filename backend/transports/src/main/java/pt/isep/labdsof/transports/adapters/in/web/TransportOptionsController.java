package pt.isep.labdsof.transports.adapters.in.web;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import pt.isep.labdsof.transports.adapters.in.web.generated.api.TransportOptionsApi;
import pt.isep.labdsof.transports.adapters.in.web.generated.model.NewTransportOption;
import pt.isep.labdsof.transports.adapters.in.web.generated.model.TransportOption;

@RestController
class TransportOptionsController implements TransportOptionsApi {

  @Override
  public ResponseEntity<TransportOption> createTransportOption(UUID eventId, NewTransportOption newTransportOption) {
    throw new NotYetImplementedException("US-41");
  }
}
