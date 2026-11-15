package pt.isep.labdsof.gateway;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StatusController {

  @GetMapping("/api/v1/status")
  public ServiceStatus status() {
    return new ServiceStatus("gateway", "ok");
  }

  public record ServiceStatus(String service, String status) {}
}
