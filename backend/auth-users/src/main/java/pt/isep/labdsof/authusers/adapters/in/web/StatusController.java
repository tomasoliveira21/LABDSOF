package pt.isep.labdsof.authusers.adapters.in.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import pt.isep.labdsof.authusers.adapters.in.web.generated.api.StatusApi;
import pt.isep.labdsof.authusers.adapters.in.web.generated.model.ServiceStatus;

@RestController
public class StatusController implements StatusApi {

  @Override
  public ResponseEntity<ServiceStatus> getServiceStatus() {
    return ResponseEntity.ok(new ServiceStatus(ServiceStatus.ServiceEnum.authUsers, ServiceStatus.StatusEnum.ok));
  }
}
