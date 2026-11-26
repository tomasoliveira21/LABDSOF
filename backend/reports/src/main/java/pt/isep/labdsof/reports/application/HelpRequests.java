package pt.isep.labdsof.reports.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import pt.isep.labdsof.reports.domain.Actor;
import pt.isep.labdsof.reports.domain.HelpRequest;
import pt.isep.labdsof.reports.domain.HelpRequestChange;
import pt.isep.labdsof.reports.domain.HistoryEntry;

/** Port: the help requests of Reports' own database. Every read is filtered by event (ADR-003 decision 3). */
public interface HelpRequests {

  /** Saves a new request; false when the same requester already sent this clientRequestId to the event. */
  boolean insertIfNew(HelpRequest request);

  Optional<HelpRequest> find(UUID eventId, UUID requestId);

  Optional<HelpRequest> findByClientRequestId(UUID eventId, String requesterId, UUID clientRequestId);

  /** Holds the account's submissions back until the transaction ends (spec US-23 D5). */
  void lockRequester(String requesterId);

  /** When the account's requests, in every event, were submitted, for those submitted after {@code from} (D2). */
  List<Instant> submittedSince(String requesterId, Instant from);

  /** The event's requests that are not Closed, in order of arrival (submittedAt, then id). */
  List<HelpRequest> open(UUID eventId);

  /** The requests one requester sent to the event, Closed ones included, in order of arrival (US-24). */
  List<HelpRequest> ofRequester(UUID eventId, String requesterId);

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
   * Saves a member's change read with {@link #findForUpdate}: the request's category, priority, status, routed team,
   * triage state, updated_at and version, and one history row per change, by {@code actor} (user ID and name) at the
   * request's updatedAt (spec US-35 D3, US-28 D2, D5, US-29 D3). A system change (US-29 D6) has no changes and no
   * actor: only a member's change is a history entry (US-28 D6).
   */
  void saveDecision(HelpRequest decided, Actor actor, List<HelpRequestChange> changes);

  /** The IDs of the event's requests routed to the team, whatever their status (US-29 D6). */
  List<UUID> routedTo(UUID eventId, UUID teamId);

  /** The request's history rows, oldest first (in the order written); empty for an unknown request (US-28 D4). */
  List<HistoryEntry> history(UUID eventId, UUID requestId);
}
