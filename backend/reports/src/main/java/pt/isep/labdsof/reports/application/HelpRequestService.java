package pt.isep.labdsof.reports.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pt.isep.labdsof.reports.domain.Actor;
import pt.isep.labdsof.reports.domain.Category;
import pt.isep.labdsof.reports.domain.EventAccess;
import pt.isep.labdsof.reports.domain.HelpRequest;
import pt.isep.labdsof.reports.domain.HistoryEntry;
import pt.isep.labdsof.reports.domain.Location;
import pt.isep.labdsof.reports.domain.Permission;
import pt.isep.labdsof.reports.domain.Priority;
import pt.isep.labdsof.reports.domain.SubmitLimit;
import pt.isep.labdsof.reports.domain.TriageState;

/**
 * Use cases on help requests: submit (persistence and outbox), read one (#194), the live list (US-26), the control
 * room's triage decisions (US-35), the acknowledge and the change history (US-28). The control room's reads and
 * decisions answer the default team, derived from the current copy of the event's teams (spec US-34 AC-2, D4).
 */
@Service
public class HelpRequestService {

  private static final Logger log = LoggerFactory.getLogger(HelpRequestService.class);

  private final HelpRequests helpRequests;
  private final LocalCopies copies;
  private final Outbox outbox;
  private final TriageMetrics metrics;
  private final SubmitLimit limit;
  private final Clock clock;

  public HelpRequestService(HelpRequests helpRequests, LocalCopies copies, Outbox outbox, TriageMetrics metrics,
      SubmitLimit limit, Clock clock) {
    this.helpRequests = helpRequests;
    this.copies = copies;
    this.outbox = outbox;
    this.metrics = metrics;
    this.limit = limit;
    this.clock = clock;
  }

  /** The request, and whether this call created it (false: a retry with the same clientRequestId). */
  public record Submitted(HelpRequest request, boolean created) {}

  /** The request as the caller sees it, and whether they get the control-room view. */
  public record Read(HelpRequest request, boolean controlRoom) {}

  /** The requests of one poll, and the serverTime to send as updatedSince next time. */
  public record Changes(List<HelpRequest> items, Instant serverTime) {}

  /**
   * Consecutive polls overlap by this much: updated_at is set before the commit, so a request committed just
   * after a poll read the table is still listed by the next one (spec US-26 D3).
   */
  static final Duration POLL_OVERLAP = Duration.ofSeconds(5);

  /**
   * Saves the request and its HelpRequestSubmitted in one transaction (ADR-004 outbox). Checks, in order: membership
   * (404), then under a lock per account, so two submissions cannot both pass the limit: the description and point
   * (400), a retry of a saved request (answered, never limited), then the account's limit (429, L-2) (spec US-23 D3,
   * D5).
   */
  @Transactional
  public Submitted submit(UUID eventId, String userId, UUID clientRequestId, String description,
      Location location) {
    access(eventId, userId);
    // The lock first, then the clock: a submission that waited is stamped when it can commit (US-26 D3, G8 review)
    helpRequests.lockRequester(userId);
    var request = HelpRequest.submit(UUID.randomUUID(), eventId, userId, clientRequestId, description,
        location, clock.instant());
    var saved = helpRequests.findByClientRequestId(eventId, userId, clientRequestId);
    if (saved.isPresent()) {
      return retried(saved.get(), description, location);
    }
    var now = request.submittedAt();
    var wait = limit.retryAfter(helpRequests.submittedSince(userId, limit.since(now)), now);
    if (wait.isPresent()) {
      throw new TooManyHelpRequestsException(wait.get());
    }
    if (!helpRequests.insertIfNew(request)) {
      // Not found above, yet taken: the account lock was not held, so no request is invented or dropped
      throw new IllegalStateException("conflicting submit not visible");
    }
    outbox.add(OutgoingEvent.helpRequestSubmitted(UUID.randomUUID(), request));
    return new Submitted(request, true);
  }

  /** A reused id with other content is a client bug: refuse it rather than drop the new request. */
  private static Submitted retried(HelpRequest saved, String description, Location location) {
    if (!saved.description().equals(description) || !saved.location().equals(location)) {
      throw new ClientRequestConflictException();
    }
    return new Submitted(saved, false);
  }

  /** The owner gets their request; HelpRequests.Triage gets any request of the event; others get 404. */
  @Transactional(readOnly = true)
  public Read get(UUID eventId, String userId, UUID requestId) {
    var access = access(eventId, userId);
    var request = helpRequests.find(eventId, requestId).orElseThrow(NotFoundException::new)
        .asSeenAt(clock.instant());
    if (access.has(Permission.HELP_REQUESTS_TRIAGE)) {
      return new Read(request.withDefaultTeam(copies.teams(eventId)), true);
    }
    if (request.requesterId().equals(userId)) {
      return new Read(request, false);
    }
    throw new NotFoundException();
  }

  /**
   * The live list of the control room (US-26): every open request on the first load ({@code since} null),
   * then what changed after {@code since}. Only HelpRequests.Triage (403); a non-member gets 404.
   */
  @Transactional(readOnly = true)
  public Changes list(UUID eventId, String userId, Instant since) {
    triageAccess(eventId, userId);
    // To the microsecond, as the database keeps times, so the next updatedSince compares exactly
    var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
    var items = since == null ? helpRequests.open(eventId) : changes(eventId, since, now);
    // Limit: a write slower than POLL_OVERLAP between updated_at and commit is missed; a commit-ordered
    // cursor (snapshot xmin) if one ever is
    var teams = copies.teams(eventId);
    return new Changes(items.stream().map(r -> r.asSeenAt(now).withDefaultTeam(teams)).toList(),
        now.minus(POLL_OVERLAP));
  }

  /** In order of arrival, as the database lists them: {@code ORDER BY submitted_at, id}. */
  // The id as text: Postgres orders a uuid by its bytes, which UUID.compareTo (signed longs) does not
  private static final Comparator<HelpRequest> ARRIVAL =
      Comparator.comparing(HelpRequest::submittedAt).thenComparing(r -> r.id().toString());

  /**
   * The requests changed after {@code since}; after a team change, every open request too, so the default team of a
   * request already listed is current (spec US-34-default-team D6). Still in order of arrival, as the contract says.
   */
  private List<HelpRequest> changes(UUID eventId, Instant since, Instant now) {
    var changed = helpRequests.candidatesSince(eventId, since).stream().filter(r -> r.changedAfter(since, now))
        .toList();
    if (!copies.teamsChangedSince(eventId, since)) {
      return changed;
    }
    var open = helpRequests.open(eventId);
    var openIds = open.stream().map(HelpRequest::id).collect(Collectors.toSet());
    return Stream.concat(open.stream(), changed.stream().filter(r -> !openIds.contains(r.id()))).sorted(ARRIVAL)
        .toList();
  }

  /**
   * The caller's own requests (US-24): every one on the first load ({@code since} null), Closed ones included,
   * then those changed after {@code since}. Any active member; a non-member gets 404 (spec US-24 D1, D2).
   */
  @Transactional(readOnly = true)
  public Changes mine(UUID eventId, String userId, Instant since) {
    access(eventId, userId);
    var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
    var items = helpRequests.ofRequester(eventId, userId).stream()
        .filter(r -> since == null || r.updatedAt().isAfter(since)).toList();
    return new Changes(items, now.minus(POLL_OVERLAP));
  }

  /** The control room of the event only: HelpRequests.Triage (403); a non-member gets 404 (US-26, US-36 D5). */
  @Transactional(readOnly = true)
  public void requireTriage(UUID eventId, String userId) {
    triageAccess(eventId, userId);
  }

  /** The check of {@link #requireTriage}, for the use cases here, which already run in their own transaction. */
  private void triageAccess(UUID eventId, String userId) {
    if (!access(eventId, userId).has(Permission.HELP_REQUESTS_TRIAGE)) {
      throw new ForbiddenException();
    }
  }

  /** The control room takes the AI's suggestion as the request's values (US-35 AC-1, spec D1). */
  @Transactional
  public HelpRequest acceptSuggestion(UUID eventId, Actor actor, UUID requestId) {
    triageAccess(eventId, actor.userId());
    var request = helpRequests.findForUpdate(eventId, requestId).orElseThrow(NotFoundException::new);
    var now = clock.instant();
    return decided(request, request.acceptSuggestion(now), actor, now);
  }

  /** The control room sets the category, the priority or both (US-35 AC-2, spec D2). */
  @Transactional
  public HelpRequest change(UUID eventId, Actor actor, UUID requestId, Category category, Priority priority) {
    triageAccess(eventId, actor.userId());
    var request = helpRequests.findForUpdate(eventId, requestId).orElseThrow(NotFoundException::new);
    var now = clock.instant();
    return decided(request, request.change(category, priority, now), actor, now);
  }

  /** The control room has seen the request: Submitted becomes Seen, recorded as the member's change (US-28 D2, D8). */
  @Transactional
  public HelpRequest acknowledge(UUID eventId, Actor actor, UUID requestId) {
    triageAccess(eventId, actor.userId());
    var request = helpRequests.findForUpdate(eventId, requestId).orElseThrow(NotFoundException::new);
    var now = clock.instant();
    var decision = request.acknowledge(now);
    helpRequests.saveDecision(decision.request(), actor, decision.changes());
    log.info("Status of request {}: {} -> {}", request.id(), request.status(), decision.request().status());
    // Same ControlRoomView as the list and the other decisions, so it carries the default team too (US-34 D4)
    return decision.request().asSeenAt(now).withDefaultTeam(copies.teams(eventId));
  }

  /** Every recorded change of a request of the event, oldest first; only HelpRequests.Triage (US-28 D4, D7). */
  @Transactional(readOnly = true)
  public List<HistoryEntry> history(UUID eventId, String userId, UUID requestId) {
    triageAccess(eventId, userId);
    helpRequests.find(eventId, requestId).orElseThrow(NotFoundException::new);
    return helpRequests.history(eventId, requestId);
  }

  /**
   * Saves and records the decision taken at {@code now}, counts the first one on a suggestion once it is committed,
   * logs metadata only (D3, D6, D8).
   */
  private HelpRequest decided(HelpRequest before, HelpRequest.Decision decision, Actor actor, Instant now) {
    var after = decision.request();
    if (!decision.changes().isEmpty()) {
      helpRequests.saveDecision(after, actor, decision.changes());
      var state = after.triage().state();
      if (before.triage().state() == TriageState.Suggested) {
        afterCommit(() -> metrics.decided(state));
      }
      log.info("Triage decision on request {}: {} -> {}", before.id(), before.asSeenAt(now).triage().state(), state);
    }
    return after.asSeenAt(now).withDefaultTeam(copies.teams(after.eventId()));
  }

  /** After the commit, so a decision rolled back is never counted (G8 review); at once outside a transaction. */
  private static void afterCommit(Runnable action) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          action.run();
        }
      });
    } else {
      action.run();
    }
  }

  private EventAccess access(UUID eventId, String userId) {
    var membership = copies.membership(eventId, userId);
    var teams = membership.map(m -> copies.teams(eventId, m.teamIds())).orElse(List.of());
    return EventAccess.of(membership.orElse(null), teams).orElseThrow(NotFoundException::new);
  }
}
