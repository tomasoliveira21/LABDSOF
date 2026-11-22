package pt.isep.labdsof.reports.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import pt.isep.labdsof.reports.domain.HelpRequest;
import pt.isep.labdsof.reports.domain.HelpRequestChange;

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
   * Reads {@code help_request.updated_at} only: a change written elsewhere (e.g. triage_suggestion, US-34/US-35)
   * must also touch it, or the control room never sees it (spec US-26 D5).
   */
  List<HelpRequest> candidatesSince(UUID eventId, Instant since);

  /**
   * Saves the request's new triage and moves its {@code updated_at} (spec US-26 D5), only while the stored triage
   * still awaits the model (Pending without a reason); false otherwise, so nothing is overwritten (US-34 D6).
   */
  boolean saveTriage(HelpRequest triaged);

  /**
   * The request, locked with its triage until the transaction ends (SELECT ... FOR UPDATE), so a member's decision
   * and the triage worker's save never interleave (spec US-35 D4).
   */
  Optional<HelpRequest> findForUpdate(UUID eventId, UUID requestId);

  /**
   * Saves a member's decision read with {@link #findForUpdate}: the request's category, priority, triage state,
   * updated_at and version, and one history row per change, by {@code actorUserId} at the request's updatedAt
   * (spec US-35 D3).
   */
  void saveDecision(HelpRequest decided, String actorUserId, List<HelpRequestChange> changes);
}
