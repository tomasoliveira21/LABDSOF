package pt.isep.labdsof.reports.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import pt.isep.labdsof.reports.domain.Actor;
import pt.isep.labdsof.reports.domain.HelpRequest;
import pt.isep.labdsof.reports.domain.HelpRequestChange;
import pt.isep.labdsof.reports.domain.HelpRequestStatus;
import pt.isep.labdsof.reports.domain.HistoryEntry;
import pt.isep.labdsof.reports.domain.Membership;
import pt.isep.labdsof.reports.domain.Team;
import pt.isep.labdsof.reports.domain.TriageSuggestion;

/** Test doubles of the ports, with the same rules as the JDBC adapters (checked by the *IT tests). */
class InMemoryPorts implements HelpRequests, FieldTasks, LocalCopies, Outbox, ProcessedMessages {

  final Map<UUID, HelpRequest> requests = new HashMap<>();
  final Map<String, Membership> memberships = new HashMap<>();
  final Map<UUID, Team> teams = new HashMap<>();
  final List<OutgoingEvent> outbox = new ArrayList<>();
  final Set<UUID> processed = new HashSet<>();
  final List<Decision> decisions = new ArrayList<>();
  final List<Task> tasks = new ArrayList<>();

  /** One field task, in the order dispatched (US-29 D4). */
  record Task(UUID id, UUID eventId, UUID requestId, UUID teamId, String status, Instant at) {}

  /** One member decision saved through {@link #saveDecision}. */
  record Decision(UUID requestId, Actor actor, Instant changedAt, List<HelpRequestChange> changes) {}

  @Override
  public boolean insertIfNew(HelpRequest request) {
    if (findByClientRequestId(request.eventId(), request.requesterId(), request.clientRequestId())
        .isPresent()) {
      return false;
    }
    requests.put(request.id(), request);
    return true;
  }

  @Override
  public Optional<HelpRequest> find(UUID eventId, UUID requestId) {
    return Optional.ofNullable(requests.get(requestId)).filter(r -> r.eventId().equals(eventId));
  }

  /** The account locks and counts, in order (spec US-23 D5). */
  final List<String> calls = new ArrayList<>();

  /** Runs when an account is locked, as another transaction ending while this one waits. */
  Runnable onLock = () -> { };

  @Override
  public void lockRequester(String requesterId) {
    calls.add("lock " + requesterId);
    onLock.run();
  }

  @Override
  public List<Instant> submittedSince(String requesterId, Instant from) {
    calls.add("count " + requesterId + " after " + from);
    return requests.values().stream().filter(r -> r.requesterId().equals(requesterId))
        .map(HelpRequest::submittedAt).filter(t -> t.isAfter(from)).toList();
  }

  @Override
  public Optional<HelpRequest> findByClientRequestId(UUID eventId, String requesterId, UUID clientId) {
    return requests.values().stream().filter(r -> r.eventId().equals(eventId)
        && r.requesterId().equals(requesterId) && r.clientRequestId().equals(clientId)).findFirst();
  }

  @Override
  public List<HelpRequest> open(UUID eventId) {
    return inArrivalOrder(eventId, r -> r.status() != HelpRequestStatus.Closed);
  }

  @Override
  public List<HelpRequest> ofRequester(UUID eventId, String requesterId) {
    return inArrivalOrder(eventId, r -> r.requesterId().equals(requesterId));
  }

  @Override
  public List<HelpRequest> candidatesSince(UUID eventId, Instant since) {
    var submittedAfter = since.minus(TriageSuggestion.TRIAGE_DEADLINE);
    return inArrivalOrder(eventId,
        r -> r.updatedAt().isAfter(since) || r.submittedAt().isAfter(submittedAfter));
  }

  @Override
  public boolean saveTriage(HelpRequest triaged) {
    var current = requests.get(triaged.id());
    if (current == null || !current.eventId().equals(triaged.eventId()) || !current.triage().awaitsModel()) {
      return false;
    }
    requests.put(triaged.id(), current.withTriage(triaged.triage(), triaged.updatedAt()));
    return true;
  }

  @Override
  public Optional<HelpRequest> findForUpdate(UUID eventId, UUID requestId) {
    return find(eventId, requestId);
  }

  @Override
  public void saveDecision(HelpRequest decided, Actor actor, List<HelpRequestChange> changes) {
    var current = find(decided.eventId(), decided.id()).orElseThrow();
    if (current.version() + 1 != decided.version()) {
      throw new IllegalStateException("lost update");
    }
    requests.put(decided.id(), decided);
    decisions.add(new Decision(decided.id(), actor, decided.updatedAt(), changes));
  }

  @Override
  public List<HistoryEntry> history(UUID eventId, UUID requestId) {
    return find(eventId, requestId).stream().flatMap(r -> decisions.stream().filter(d -> d.requestId().equals(r.id())))
        .flatMap(d -> d.changes().stream().map(c -> new HistoryEntry(c.field(), c.oldValue(), c.newValue(),
            d.actor().userId(), d.actor().name(), d.changedAt())))
        .toList();
  }

  @Override
  public List<UUID> routedTo(UUID eventId, UUID teamId) {
    return inArrivalOrder(eventId, r -> teamId.equals(r.routedTeamId())).stream().map(HelpRequest::id).toList();
  }

  @Override
  public void dispatch(UUID taskId, UUID eventId, UUID requestId, UUID teamId, Instant at) {
    if (tasks.stream().anyMatch(t -> t.requestId().equals(requestId) && live(t))) {
      throw new IllegalStateException("one live task per request");
    }
    tasks.add(new Task(taskId, eventId, requestId, teamId, "Open", at));
  }

  @Override
  public void cancelLive(UUID eventId, UUID requestId, Instant at) {
    tasks.replaceAll(t -> t.eventId().equals(eventId) && t.requestId().equals(requestId) && live(t)
        ? new Task(t.id(), t.eventId(), t.requestId(), t.teamId(), "Cancelled", at) : t);
  }

  private static boolean live(Task task) {
    return task.status().equals("Open") || task.status().equals("Accepted");
  }

  private List<HelpRequest> inArrivalOrder(UUID eventId, Predicate<HelpRequest> filter) {
    return requests.values().stream().filter(r -> r.eventId().equals(eventId)).filter(filter)
        .sorted(Comparator.comparing(HelpRequest::submittedAt).thenComparing(HelpRequest::id)).toList();
  }

  @Override
  public Optional<Membership> membership(UUID eventId, String userId) {
    return Optional.ofNullable(memberships.get(eventId + "/" + userId));
  }

  @Override
  public List<Team> teams(UUID eventId, Collection<UUID> teamIds) {
    return teams.values().stream()
        .filter(t -> t.eventId().equals(eventId) && teamIds.contains(t.teamId())).toList();
  }

  @Override
  public Optional<Team> teamForShare(UUID eventId, UUID teamId) {
    return Optional.ofNullable(teams.get(teamId)).filter(t -> t.eventId().equals(eventId));
  }

  /** When the copy of each event's teams last changed; the tests set it. */
  final Map<UUID, Instant> teamsChangedAt = new HashMap<>();

  @Override
  public boolean teamsChangedSince(UUID eventId, Instant since) {
    var at = teamsChangedAt.get(eventId);
    return at != null && at.isAfter(since);
  }

  @Override
  public List<Team> teams(UUID eventId) {
    return teams.values().stream().filter(t -> t.eventId().equals(eventId)).toList();
  }

  @Override
  public boolean upsertIfNewer(Membership membership) {
    var key = membership.eventId() + "/" + membership.userId();
    var current = memberships.get(key);
    if (current != null && current.version() >= membership.version()) {
      return false;
    }
    memberships.put(key, membership);
    return true;
  }

  @Override
  public boolean upsertIfNewer(Team team) {
    var current = teams.get(team.teamId());
    if (current != null && current.version() >= team.version()) {
      return false;
    }
    teams.put(team.teamId(), team);
    return true;
  }

  @Override
  public void add(OutgoingEvent event) {
    outbox.add(event);
  }

  @Override
  public boolean markProcessed(UUID messageId) {
    return processed.add(messageId);
  }
}
