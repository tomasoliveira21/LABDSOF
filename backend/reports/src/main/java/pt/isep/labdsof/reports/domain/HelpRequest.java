package pt.isep.labdsof.reports.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/** A participant's request for help in one event (domain model; API outline §4.1). */
public record HelpRequest(
    UUID id,
    UUID eventId,
    String requesterId,
    UUID clientRequestId,
    String description,
    Location location,
    Category category,
    Priority priority,
    HelpRequestStatus status,
    UUID defaultTeamId,
    UUID routedTeamId,
    TriageSuggestion triage,
    Instant submittedAt,
    Instant updatedAt,
    long version) {

  public static final int MAX_DESCRIPTION = 500;

  public HelpRequest {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(eventId, "eventId");
    Objects.requireNonNull(clientRequestId, "clientRequestId");
    Objects.requireNonNull(location, "location");
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(triage, "triage");
    Objects.requireNonNull(submittedAt, "submittedAt");
    Objects.requireNonNull(updatedAt, "updatedAt");
    if (requesterId == null || requesterId.isBlank()) {
      throw new IllegalArgumentException("requesterId is required");
    }
    // Characters are code points, as varchar(500) and JSON Schema maxLength count them; PostgreSQL cannot hold NUL.
    if (description == null || description.isBlank()
        || description.codePointCount(0, description.length()) > MAX_DESCRIPTION
        || description.indexOf('\u0000') >= 0) {
      throw new InvalidHelpRequestException("description must have 1 to 500 characters");
    }
  }

  /**
   * A new request: Submitted, triage Pending, no category or priority until the AI or a member sets them.
   * Times are kept to the microsecond, the precision of the database, so a read returns what was saved.
   */
  public static HelpRequest submit(UUID id, UUID eventId, String requesterId, UUID clientRequestId,
      String description, Location location, Instant now) {
    var at = now.truncatedTo(ChronoUnit.MICROS);
    return new HelpRequest(id, eventId, requesterId, clientRequestId, description, location, null, null,
        HelpRequestStatus.Submitted, null, null, TriageSuggestion.pending(), at, at, 1);
  }

  /**
   * Whether a live list polled at {@code now} with {@code since} must list this request again (US-26):
   * it was changed after {@code since}, or it is still Pending and its triage deadline passed in
   * (since, now], so it now reads NotTriaged without any write (decision 6). The JDBC adapter holds the
   * same rule in SQL.
   */
  public boolean changedAfter(Instant since, Instant now) {
    if (updatedAt.isAfter(since)) {
      return true;
    }
    var deadline = submittedAt.plus(TriageSuggestion.TRIAGE_DEADLINE);
    return triage.state() == TriageState.Pending && deadline.isAfter(since) && !deadline.isAfter(now);
  }

  /** This request as a reader sees it at {@code now} (NotTriaged derived from Pending). */
  public HelpRequest asSeenAt(Instant now) {
    return new HelpRequest(id, eventId, requesterId, clientRequestId, description, location, category,
        priority, status, defaultTeamId, routedTeamId, triage.asSeenAt(submittedAt, now), submittedAt,
        updatedAt, version);
  }
}
