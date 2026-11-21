package pt.isep.labdsof.events.adapters.in.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import pt.isep.labdsof.events.adapters.in.web.generated.api.StatusApi;
import pt.isep.labdsof.events.adapters.in.web.generated.model.ServiceStatus;

@RestController
public class StatusController implements StatusApi {

  @Override
  public ResponseEntity<ServiceStatus> getServiceStatus() {
    return ResponseEntity.ok(new ServiceStatus(ServiceStatus.ServiceEnum.EVENTS, ServiceStatus.StatusEnum.OK));
  }
}
