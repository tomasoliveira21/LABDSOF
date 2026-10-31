package pt.isep.labdsof.reports.adapters.in.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pt.isep.labdsof.reports.application.demo.DemoHelpRequestService;
import pt.isep.labdsof.reports.domain.demo.DemoHelpRequest;
import pt.isep.labdsof.reports.domain.demo.DemoHelpRequest.Category;

@RestController
@Profile("demo")
@RequestMapping("/api/v1/demo/help-requests")
public class DemoHelpRequestController {

  private final DemoHelpRequestService service;

  public DemoHelpRequestController(DemoHelpRequestService service) {
    this.service = service;
  }

  @PostMapping(consumes = "application/json", produces = "application/json")
  public ResponseEntity<DemoHelpRequest> create(@Valid @RequestBody CreateRequest input) {
    var saved = service.create(input.category(), input.description());
    return ResponseEntity.created(URI.create("/api/v1/demo/help-requests/" + saved.id()))
        .header("X-Demo-Mode", "true")
        .body(saved);
  }

  @GetMapping(value = "/{requestId}", produces = "application/json")
  public ResponseEntity<DemoHelpRequest> get(@PathVariable UUID requestId) {
    return service.find(requestId)
        .map(request -> ResponseEntity.ok().header("X-Demo-Mode", "true").body(request))
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  public record CreateRequest(
      @NotNull Category category,
      @NotBlank @Size(max = 500) String description) {}
}
