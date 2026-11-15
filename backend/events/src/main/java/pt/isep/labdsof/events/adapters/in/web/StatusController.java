package pt.isep.labdsof.events.adapters.in.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StatusController {

  @GetMapping("/api/v1/status")
  public ServiceStatus status() {
    return new ServiceStatus("events", "ok");
  }

  public record ServiceStatus(String service, String status) {}
}
