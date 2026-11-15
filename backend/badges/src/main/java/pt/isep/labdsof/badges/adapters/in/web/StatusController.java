package pt.isep.labdsof.badges.adapters.in.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StatusController {

  @GetMapping("/api/v1/status")
  public ServiceStatus status() {
    return new ServiceStatus("badges", "ok");
  }

  public record ServiceStatus(String service, String status) {}
}
