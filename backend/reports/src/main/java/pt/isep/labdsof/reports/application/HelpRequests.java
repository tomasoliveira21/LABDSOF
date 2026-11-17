package pt.isep.labdsof.reports.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import pt.isep.labdsof.reports.domain.HelpRequest;

/** Port: the help requests of Reports' own database. Every read is filtered by event (ADR-003 decision 3). */
public interface HelpRequests {

  /** Saves a new request; false when the same requester already sent this clientRequestId to the event. */
  boolean insertIfNew(HelpRequest request);

  Optional<HelpRequest> find(UUID eventId, UUID requestId);

  Optional<HelpRequest> findByClientRequestId(UUID eventId, String requesterId, UUID clientRequestId);

  /** The event's requests that are not Closed, in order of arrival (submittedAt, then id). */
  List<HelpRequest> open(UUID eventId);

  /**
   * The event's requests that may have changed after {@code since}, in order of arrival: updated after it, or
   * submitted after {@code since} minus the triage deadline. A superset: the caller keeps those for which
   * {@link HelpRequest#changedAfter} holds, so the rule lives in the domain only.
   */
  List<HelpRequest> candidatesSince(UUID eventId, Instant since);
}
