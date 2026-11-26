package pt.isep.labdsof.reports.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
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
   * (since, now], so it now reads NotTriaged without any write (decision 6). The database only narrows the
   * candidates (HelpRequests.candidatesSince); this rule decides.
   */
  public boolean changedAfter(Instant since, Instant now) {
    if (updatedAt.isAfter(since)) {
      return true;
    }
    var deadline = submittedAt.plus(TriageSuggestion.TRIAGE_DEADLINE);
    return awaitsModel() && deadline.isAfter(since) && !deadline.isAfter(now);
  }

  /** Whether the AI's result may still be written: the triage awaits it and the request is not closed (US-30 G8). */
  public boolean awaitsModel() {
    return status != HelpRequestStatus.Closed && triage.awaitsModel();
  }

  /**
   * This request with a new triage (US-34): only the triage and {@code updatedAt} change, so the live list shows it
   * (spec US-26 D5) and the request's own category and priority stay as a member set them (NFR-22).
   */
  public HelpRequest withTriage(TriageSuggestion triage, Instant now) {
    return new HelpRequest(id, eventId, requesterId, clientRequestId, description, location, category,
        priority, status, defaultTeamId, routedTeamId, triage, submittedAt, now.truncatedTo(ChronoUnit.MICROS),
        version);
  }

  /** A member's triage decision: the request after it, and the fields it changed (empty: nothing to write). */
  public record Decision(HelpRequest request, List<HelpRequestChange> changes) {}

  /**
   * The member takes the suggestion (US-35 AC-1, spec D1): its category and priority become the request's, the
   * triage becomes Accepted and the suggestion stays visible. Only a Suggested triage can be accepted.
   */
  public Decision acceptSuggestion(Instant now) {
    requireOpen();
    if (triage.state() != TriageState.Suggested) {
      throw new RequestConflictException("There is no suggestion to accept");
    }
    return decide(triage.suggestedCategory(), triage.suggestedPriority(), triage.decided(TriageState.Accepted),
        TriageState.Suggested, now);
  }

  /**
   * The member sets the category, the priority or both (US-35 AC-2, spec D2): from any triage state the triage
   * becomes Changed, keeping the suggestion or the NotTriaged reason as read at {@code now}; a null is not sent.
   */
  public Decision change(Category newCategory, Priority newPriority, Instant now) {
    if (newCategory == null && newPriority == null) {
      throw new InvalidHelpRequestException("category or priority is required");
    }
    requireOpen();
    var mergedCategory = newCategory == null ? category : newCategory;
    var mergedPriority = newPriority == null ? priority : newPriority;
    if (mergedCategory == category && mergedPriority == priority) {
      // The values the request already has: nothing is decided, so an Accepted triage stays Accepted
      return new Decision(this, List.of());
    }
    var seen = triage.asSeenAt(submittedAt, now);
    return decide(mergedCategory, mergedPriority, seen.decided(TriageState.Changed), seen.state(), now);
  }

  /**
   * The control room has seen the request (US-28, spec D2; decision 6): Submitted becomes Seen, whatever the triage;
   * any other status is a conflict.
   */
  public Decision acknowledge(Instant now) {
    if (status != HelpRequestStatus.Submitted) {
      throw new RequestConflictException("The request is no longer Submitted");
    }
    return moveTo(HelpRequestStatus.Seen, now);
  }

  /**
   * The control room closes the request (US-30, spec D1; decision 6): any other status becomes Closed, whatever the
   * triage. Closed is final (D2).
   */
  public Decision close(Instant now) {
    requireOpen();
    return moveTo(HelpRequestStatus.Closed, now);
  }

  private void requireOpen() {
    if (status == HelpRequestStatus.Closed) {
      throw new RequestConflictException("The request is closed");
    }
  }

  private Decision moveTo(HelpRequestStatus to, Instant now) {
    return new Decision(new HelpRequest(id, eventId, requesterId, clientRequestId, description, location, category,
        priority, to, defaultTeamId, routedTeamId, triage, submittedAt, now.truncatedTo(ChronoUnit.MICROS),
        version + 1), List.of(new HelpRequestChange("status", status.name(), to.name())));
  }

  /**
   * The control room routes the request to {@code team} (US-29 D1 to D3): a team of this event, not deleted, with
   * HelpRequests.Respond (else 422, checked first), while the request is Submitted or Seen, which is while its field
   * task, if any, is still Open (else 409). The status is kept. The team it already has changes nothing.
   */
  public Decision route(Team team, Instant now) {
    if (team == null || !team.eventId().equals(eventId) || !team.grants(Permission.HELP_REQUESTS_RESPOND)) {
      throw new IneligibleTeamException();
    }
    if (status != HelpRequestStatus.Submitted && status != HelpRequestStatus.Seen) {
      throw new RequestConflictException("The request can no longer be routed");
    }
    if (team.teamId().equals(routedTeamId)) {
      return new Decision(this, List.of());
    }
    return new Decision(new HelpRequest(id, eventId, requesterId, clientRequestId, description, location, category,
        priority, status, defaultTeamId, team.teamId(), triage, submittedAt, now.truncatedTo(ChronoUnit.MICROS),
        version + 1),
        List.of(new HelpRequestChange("routedTeamId", routedTeamId == null ? null : routedTeamId.toString(),
            team.teamId().toString())));
  }

  /**
   * The request after its team {@code teamId} was deleted (US-29 D6, US-16): back in the control room's queue, with no
   * team, and Seen if a member of that team was on the way. A finished request, or one routed elsewhere, is returned
   * as it is.
   */
  public HelpRequest returnFrom(UUID teamId, Instant now) {
    if (!teamId.equals(routedTeamId) || status == HelpRequestStatus.Resolved || status == HelpRequestStatus.Closed) {
      return this;
    }
    return new HelpRequest(id, eventId, requesterId, clientRequestId, description, location, category, priority,
        status == HelpRequestStatus.OnTheWay ? HelpRequestStatus.Seen : status, defaultTeamId, null, triage,
        submittedAt, now.truncatedTo(ChronoUnit.MICROS), version + 1);
  }

  private Decision decide(Category newCategory, Priority newPriority, TriageSuggestion newTriage, TriageState from,
      Instant now) {
    var changes = new ArrayList<HelpRequestChange>();
    if (newCategory != category) {
      changes.add(new HelpRequestChange("category", name(category), name(newCategory)));
    }
    if (newPriority != priority) {
      changes.add(new HelpRequestChange("priority", name(priority), name(newPriority)));
    }
    if (newTriage.state() != from) {
      changes.add(new HelpRequestChange("triage", from.name(), newTriage.state().name()));
    }
    return new Decision(new HelpRequest(id, eventId, requesterId, clientRequestId, description, location,
        newCategory, newPriority, status, defaultTeamId, routedTeamId, newTriage, submittedAt,
        now.truncatedTo(ChronoUnit.MICROS), version + 1), List.copyOf(changes));
  }

  private static String name(Enum<?> value) {
    return value == null ? null : value.name();
  }

  /**
   * This request with its default team (spec US-34 AC-2, D1 to D3): the only team of {@code teams} in this event that
   * handles the category (a member's, else the AI's) and responds; none or several give null and the operator routes
   * by hand. Derived when read, never stored (D4).
   */
  public HelpRequest withDefaultTeam(List<Team> teams) {
    var c = category != null ? category : triage.suggestedCategory();
    var eligible = c == null ? List.<UUID>of() : teams.stream()
        .filter(t -> t.eventId().equals(eventId) && t.canRespondTo(c)).map(Team::teamId).toList();
    return new HelpRequest(id, eventId, requesterId, clientRequestId, description, location, category, priority,
        status, eligible.size() == 1 ? eligible.get(0) : null, routedTeamId, triage, submittedAt, updatedAt, version);
  }

  /** This request as a reader sees it at {@code now} (NotTriaged derived from Pending). */
  public HelpRequest asSeenAt(Instant now) {
    return new HelpRequest(id, eventId, requesterId, clientRequestId, description, location, category,
        priority, status, defaultTeamId, routedTeamId, triage.asSeenAt(submittedAt, now), submittedAt,
        updatedAt, version);
  }
}
