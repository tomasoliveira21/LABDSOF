package pt.isep.labdsof.reports.domain.demo;

import java.time.Instant;
import java.util.UUID;

/** Synthetic R0 data; not the production request/identity model. */
public record DemoHelpRequest(
    UUID id,
    String eventId,
    String requesterId,
    Category category,
    String description,
    String status,
    Triage triage,
    Instant submittedAt,
    boolean demo) {

  public enum Category { Health, Safety, LostPerson, Theft, Other }

  public record Triage(String state) {}
}
