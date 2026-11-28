package pt.isep.labdsof.reports.domain;

import java.time.Instant;
import java.util.UUID;

/** A field task as its team reads it, with the help request it targets (domain model FieldTask; US-31 D3). */
public record FieldTask(UUID id, UUID teamId, Status status, String acceptedBy, Instant dispatchedAt,
    Instant updatedAt, HelpRequest request) {

  /** The constants are the contract values (API, database), so they keep its PascalCase. */
  @SuppressWarnings("java:S115")
  public enum Status { Open, Accepted, Resolved, Cancelled }
}
