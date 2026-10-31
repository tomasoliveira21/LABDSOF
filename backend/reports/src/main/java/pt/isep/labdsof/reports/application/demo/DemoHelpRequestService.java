package pt.isep.labdsof.reports.application.demo;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import pt.isep.labdsof.reports.domain.demo.DemoHelpRequest;
import pt.isep.labdsof.reports.domain.demo.DemoHelpRequest.Category;

@Service
@Profile("demo")
public class DemoHelpRequestService {

  private final ConcurrentHashMap<UUID, DemoHelpRequest> requests = new ConcurrentHashMap<>();

  public DemoHelpRequest create(Category category, String description) {
    var request = new DemoHelpRequest(
        UUID.randomUUID(), "demo-event", "demo-participant", category, description,
        "Submitted", new DemoHelpRequest.Triage("NotTriaged"), Instant.now(), true);
    requests.put(request.id(), request);
    return request;
  }

  public Optional<DemoHelpRequest> find(UUID id) {
    return Optional.ofNullable(requests.get(id));
  }
}
