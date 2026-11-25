package pt.isep.labdsof.reports.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pt.isep.labdsof.reports.domain.Actor;
import pt.isep.labdsof.reports.domain.Category;
import pt.isep.labdsof.reports.domain.HelpRequest;
import pt.isep.labdsof.reports.domain.HelpRequestChange;
import pt.isep.labdsof.reports.domain.HelpRequestStatus;
import pt.isep.labdsof.reports.domain.HistoryEntry;
import pt.isep.labdsof.reports.domain.Location;
import pt.isep.labdsof.reports.domain.Membership.Role;
import pt.isep.labdsof.reports.domain.Membership.Status;
import pt.isep.labdsof.reports.domain.Membership;
import pt.isep.labdsof.reports.domain.NotTriagedReason;
import pt.isep.labdsof.reports.domain.Permission;
import pt.isep.labdsof.reports.domain.Priority;
import pt.isep.labdsof.reports.domain.Team.TeamPermission;
import pt.isep.labdsof.reports.domain.Team;
import pt.isep.labdsof.reports.domain.SubmitLimit;
import pt.isep.labdsof.reports.domain.TriageState;
import pt.isep.labdsof.reports.domain.TriageSuggestion;

class HelpRequestServiceTest {

  static final Instant NOW = Instant.parse("2027-05-08T23:14:05Z");
  static final UUID EVENT = UUID.randomUUID();
  static final UUID CONTROL_ROOM = UUID.randomUUID();
  static final Location POINT = new Location(3, 0.42, 0.77);
  static final SubmitLimit LIMIT = new SubmitLimit(5, Duration.ofMinutes(10));

  final InMemoryPorts ports = new InMemoryPorts();
  final TriageServiceTest.FakeMetrics metrics = new TriageServiceTest.FakeMetrics();
  Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  HelpRequestService service() {
    return new HelpRequestService(ports, ports, ports, ports, metrics, LIMIT, clock);
  }

  void member(String user, Role role, UUID... teams) {
    ports.upsertIfNewer(new Membership(EVENT, user, role, Status.Active, Set.of(teams), 1));
  }

  void controlRoomTeam() {
    ports.upsertIfNewer(new Team(CONTROL_ROOM, EVENT, "Control room",
        List.of(new TeamPermission(Permission.HELP_REQUESTS_TRIAGE, Set.of())), Set.of(Category.Health),
        false, 1));
  }

  @Test
  @DisplayName("194 AC-2 a submit saves the request and one HelpRequestSubmitted with only its id")
  void submitWritesTheOutbox() {
    member("ana", Role.RegularUser);
    var client = UUID.randomUUID();
    var result = service().submit(EVENT, "ana", client, "Friend fainted", POINT);

    assertThat(result.created()).isTrue();
    var request = result.request();
    assertThat(ports.requests).containsExactly(Map.entry(request.id(), request));
    assertThat(request.submittedAt()).isEqualTo(NOW);
    assertThat(request.clientRequestId()).isEqualTo(client);
    assertThat(request.requesterId()).isEqualTo("ana");
    assertThat(request.eventId()).isEqualTo(EVENT);

    assertThat(ports.outbox).singleElement().satisfies(event -> {
      assertThat(event.type()).isEqualTo("HelpRequestSubmitted");
      assertThat(event.exchange()).isEqualTo("farol.reports");
      assertThat(event.routingKey()).isEqualTo("help-request.submitted");
      assertThat(event.schemaVersion()).isEqualTo(1);
      assertThat(event.occurredAt()).isEqualTo(NOW);
      assertThat(event.eventId()).isEqualTo(EVENT);
      assertThat(event.actorUserId()).isEqualTo("ana");
      assertThat(event.payload()).isEqualTo(Map.of("requestId", request.id().toString()));
      assertThat(event.messageId()).isNotNull();
    });
  }

  @Test
  @DisplayName("194 AC-2 a retried submit returns the saved request and adds no second event")
  void retriedSubmit() {
    member("ana", Role.RegularUser);
    var client = UUID.randomUUID();
    var first = service().submit(EVENT, "ana", client, "Friend fainted", POINT);
    var second = service().submit(EVENT, "ana", client, "Friend fainted", POINT);

    assertThat(second.created()).isFalse();
    assertThat(second.request()).isEqualTo(first.request());
    assertThat(ports.outbox).hasSize(1);
  }

  @Test
  @DisplayName("194 AC-2 a clientRequestId reused with another description or location is refused, not dropped")
  void reusedClientRequestId() {
    member("ana", Role.RegularUser);
    var client = UUID.randomUUID();
    var first = service().submit(EVENT, "ana", client, "Friend fainted", POINT);
    var service = service();
    var elsewhere = new Location(3, 0.42, 0.78);

    assertThatThrownBy(() -> service.submit(EVENT, "ana", client, "Lost my phone", POINT))
        .isInstanceOf(ClientRequestConflictException.class);
    assertThatThrownBy(() -> service.submit(EVENT, "ana", client, "Friend fainted", elsewhere))
        .isInstanceOf(ClientRequestConflictException.class);
    assertThat(ports.requests).containsOnlyKeys(first.request().id());
    assertThat(ports.outbox).hasSize(1);
  }

  @Test
  @DisplayName("194 AC-2 a conflicting submit that is not visible fails instead of inventing a request")
  void invisibleConflict() {
    member("ana", Role.RegularUser);
    HelpRequests refusing = new HelpRequests() {
      @Override
      public boolean insertIfNew(HelpRequest request) {
        return false;
      }

      @Override
      public Optional<HelpRequest> find(UUID eventId, UUID requestId) {
        return Optional.empty();
      }

      @Override
      public Optional<HelpRequest> findByClientRequestId(UUID e, String r, UUID c) {
        return Optional.empty();
      }

      @Override
      public void lockRequester(String requesterId) {
        // nothing to lock
      }

      @Override
      public List<Instant> submittedSince(String requesterId, Instant from) {
        return List.of();
      }

      @Override
      public List<HelpRequest> open(UUID eventId) {
        return List.of();
      }

      @Override
      public List<HelpRequest> ofRequester(UUID eventId, String requesterId) {
        return List.of();
      }

      @Override
      public List<HelpRequest> candidatesSince(UUID eventId, Instant since) {
        return List.of();
      }

      @Override
      public boolean saveTriage(HelpRequest triaged) {
        return false;
      }

      @Override
      public Optional<HelpRequest> findForUpdate(UUID eventId, UUID requestId) {
        return Optional.empty();
      }

      @Override
      public void saveDecision(HelpRequest decided, Actor actor, List<HelpRequestChange> changes) {
        throw new UnsupportedOperationException();
      }

      @Override
      public List<UUID> routedTo(UUID eventId, UUID teamId) {
        return List.of();
      }

      @Override
      public List<HistoryEntry> history(UUID eventId, UUID requestId) {
        return List.of();
      }
    };
    var service = new HelpRequestService(refusing, ports, ports, ports, metrics, LIMIT, clock);
    var clientRequestId = UUID.randomUUID();
    assertThatThrownBy(() -> service.submit(EVENT, "ana", clientRequestId, "help", POINT))
        .isInstanceOf(IllegalStateException.class);
    assertThat(ports.outbox).isEmpty();
  }

  @Test
  @DisplayName("194 AC-4 a user who is not a member of the event cannot submit")
  void nonMemberSubmit() {
    ports.upsertIfNewer(new Membership(UUID.randomUUID(), "ana", Role.EventAdmin, Status.Active,
        Set.of(), 1));
    var service = service();
    var clientRequestId = UUID.randomUUID();
    assertThatThrownBy(() -> service.submit(EVENT, "ana", clientRequestId, "help", POINT))
        .isInstanceOf(NotFoundException.class);
    assertThat(ports.requests).isEmpty();
    assertThat(ports.outbox).isEmpty();
  }

  @Test
  @DisplayName("194 AC-4 the owner reads the participant view")
  void ownerReads() {
    member("ana", Role.RegularUser);
    var saved = service().submit(EVENT, "ana", UUID.randomUUID(), "help", POINT).request();

    var read = service().get(EVENT, "ana", saved.id());
    assertThat(read.controlRoom()).isFalse();
    assertThat(read.request()).isEqualTo(saved);
  }

  @Test
  @DisplayName("194 AC-4 a triage member reads the control-room view with NotTriaged derived")
  void triageReads() {
    member("ana", Role.RegularUser);
    member("rui", Role.RegularUser, CONTROL_ROOM);
    controlRoomTeam();
    var saved = service().submit(EVENT, "ana", UUID.randomUUID(), "help", POINT).request();

    clock = Clock.fixed(NOW.plusSeconds(30), ZoneOffset.UTC);
    var read = service().get(EVENT, "rui", saved.id());
    assertThat(read.controlRoom()).isTrue();
    assertThat(read.request().triage().state()).isEqualTo(TriageState.NotTriaged);
  }

  @Test
  @DisplayName("194 AC-4 another member of the event gets 404 for someone's request")
  void otherMemberReads() {
    member("ana", Role.RegularUser);
    member("rui", Role.RegularUser);
    var service = service();
    var requestId = service.submit(EVENT, "ana", UUID.randomUUID(), "help", POINT).request().id();

    assertThatThrownBy(() -> service.get(EVENT, "rui", requestId))
        .isInstanceOf(NotFoundException.class);
  }

  HelpRequest submitAt(Instant at, String description) {
    clock = Clock.fixed(at, ZoneOffset.UTC);
    return service().submit(EVENT, "ana", UUID.randomUUID(), description, POINT).request();
  }

  void close(HelpRequest request, Instant at) {
    ports.requests.put(request.id(), new HelpRequest(request.id(), request.eventId(),
        request.requesterId(), request.clientRequestId(), request.description(), request.location(),
        request.category(), request.priority(), HelpRequestStatus.Closed, null, null, request.triage(),
        request.submittedAt(), at, request.version() + 1));
  }

  @Test
  @DisplayName("US-26 AC-1 the first load lists the open requests and later polls list what changed")
  void list() {
    member("ana", Role.RegularUser);
    member("rui", Role.RegularUser, CONTROL_ROOM);
    controlRoomTeam();
    submitAt(NOW.plusSeconds(2), "second");
    var first = submitAt(NOW, "first");
    var closed = submitAt(NOW.plusSeconds(1), "closed");
    close(closed, NOW.plusSeconds(3));

    clock = Clock.fixed(NOW.plusSeconds(10), ZoneOffset.UTC);
    var load = service().list(EVENT, "rui", null);
    assertThat(load.items()).extracting(HelpRequest::description).containsExactly("first", "second");
    // Consecutive polls overlap by 5 s, so a request committed just after a poll is not lost (D3)
    assertThat(load.serverTime()).isEqualTo(NOW.plusSeconds(5));

    var changes = service().list(EVENT, "rui", NOW.plusSeconds(1));
    assertThat(changes.items()).extracting(HelpRequest::description).containsExactly("closed", "second");
    assertThat(changes.items().get(0).status()).isEqualTo(HelpRequestStatus.Closed);

    // At the 30 s deadline, the Pending requests come back as NotTriaged (D4)
    clock = Clock.fixed(NOW.plusSeconds(31), ZoneOffset.UTC);
    var late = service().list(EVENT, "rui", NOW.plusSeconds(26));
    // The closed one too: harmless, the page drops closed requests
    assertThat(late.items()).extracting(HelpRequest::id).containsExactly(first.id(), closed.id());
    assertThat(late.items().get(0).triage().state()).isEqualTo(TriageState.NotTriaged);
  }

  @Test
  @DisplayName("US-26 AC-1 serverTime is kept to the microsecond, as the database stores times")
  void serverTimeInMicros() {
    member("sofia", Role.EventAdmin);
    clock = Clock.fixed(NOW.plusNanos(1_700), ZoneOffset.UTC);
    assertThat(service().list(EVENT, "sofia", null).serverTime())
        .isEqualTo(NOW.plusNanos(1_000).minusSeconds(5));
  }

  @Test
  @DisplayName("US-26 AC-1 an EventAdmin and a triage team member get the list")
  void whoGetsTheList() {
    member("ana", Role.RegularUser);
    member("rui", Role.RegularUser, CONTROL_ROOM);
    member("sofia", Role.EventAdmin);
    controlRoomTeam();
    var request = submitAt(NOW, "help");

    assertThat(service().list(EVENT, "rui", null).items()).containsExactly(request);
    assertThat(service().list(EVENT, "sofia", null).items()).containsExactly(request);
  }

  @Test
  @DisplayName("US-26 AC-2 a member without HelpRequests.Triage is refused")
  void refused() {
    member("ana", Role.RegularUser);
    submitAt(NOW, "help");
    var service = service();
    assertThatThrownBy(() -> service.list(EVENT, "ana", null)).isInstanceOf(ForbiddenException.class);
    assertThatThrownBy(() -> service.list(EVENT, "nobody", null))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  @DisplayName("US-24 AC-1 mine lists my requests and later polls list what changed")
  void mine() {
    member("ana", Role.RegularUser);
    member("rui", Role.RegularUser);
    var elsewhere = UUID.randomUUID();
    ports.upsertIfNewer(new Membership(elsewhere, "ana", Role.RegularUser, Status.Active, Set.of(), 1));
    var second = submitAt(NOW.plusSeconds(2), "second");
    var first = submitAt(NOW, "first");
    var closed = submitAt(NOW.plusSeconds(1), "closed");
    close(closed, NOW.plusSeconds(3));
    service().submit(EVENT, "rui", UUID.randomUUID(), "rui's", POINT);
    service().submit(elsewhere, "ana", UUID.randomUUID(), "other event", POINT);

    clock = Clock.fixed(NOW.plusSeconds(10), ZoneOffset.UTC);
    var load = service().mine(EVENT, "ana", null);
    // Closed ones stay: the participant sees Closed (D2)
    assertThat(load.items()).extracting(HelpRequest::id).containsExactly(first.id(), closed.id(), second.id());
    assertThat(load.serverTime()).isEqualTo(NOW.plusSeconds(5));

    assertThat(service().mine(EVENT, "ana", NOW.plusSeconds(1)).items()).extracting(HelpRequest::description)
        .containsExactly("closed", "second");
    // Strictly after updatedSince
    assertThat(service().mine(EVENT, "ana", NOW.plusSeconds(2)).items()).extracting(HelpRequest::description)
        .containsExactly("closed");

    // The triage deadline changes nothing the participant sees, so nothing comes back (D2)
    clock = Clock.fixed(NOW.plusSeconds(31), ZoneOffset.UTC);
    assertThat(service().mine(EVENT, "ana", NOW.plusSeconds(26)).items()).isEmpty();
  }

  @Test
  @DisplayName("US-24 AC-1 a triage member asking mine gets only their own requests")
  void mineForTriage() {
    member("ana", Role.RegularUser);
    member("rui", Role.RegularUser, CONTROL_ROOM);
    member("sofia", Role.EventAdmin);
    controlRoomTeam();
    submitAt(NOW, "ana's");
    var rui = service().submit(EVENT, "rui", UUID.randomUUID(), "rui's", POINT).request();

    assertThat(service().mine(EVENT, "rui", null).items()).containsExactly(rui);
    assertThat(service().mine(EVENT, "sofia", null).items()).isEmpty();
  }

  @Test
  @DisplayName("US-24 AC-1 a non-member asking mine gets 404")
  void mineNotMember() {
    member("ana", Role.RegularUser);
    submitAt(NOW, "help");
    ports.upsertIfNewer(new Membership(EVENT, "gone", Role.RegularUser, Status.Removed, Set.of(), 1));
    var service = service();
    assertThatThrownBy(() -> service.mine(EVENT, "nobody", null)).isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> service.mine(EVENT, "gone", null)).isInstanceOf(NotFoundException.class);
  }

  @Test
  @DisplayName("US-24 AC-2 serverTime overlaps the polls by 5 s, to the microsecond")
  void mineServerTime() {
    member("ana", Role.RegularUser);
    clock = Clock.fixed(NOW.plusNanos(1_700), ZoneOffset.UTC);
    assertThat(service().mine(EVENT, "ana", null).serverTime()).isEqualTo(NOW.plusNanos(1_000).minusSeconds(5));
  }

  @Test
  @DisplayName("US-36 AC-2 only the control room reads the triage status")
  void triageStatusAccess() {
    controlRoomTeam();
    member("ana", Role.RegularUser);
    member("rui", Role.RegularUser, CONTROL_ROOM);
    var service = service();
    var otherEvent = UUID.randomUUID();
    assertThatThrownBy(() -> service.requireTriage(EVENT, "ana")).isInstanceOf(ForbiddenException.class);
    assertThatThrownBy(() -> service.requireTriage(EVENT, "nobody")).isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> service.requireTriage(otherEvent, "rui")).isInstanceOf(NotFoundException.class);
    service.requireTriage(EVENT, "rui");
  }

  @Test
  @DisplayName("US-26 AC-2 a team of another event or a deleted team does not grant the list")
  void teamsThatGrantNothing() {
    var elsewhere = UUID.randomUUID();
    var deleted = UUID.randomUUID();
    var triage = List.of(new TeamPermission(Permission.HELP_REQUESTS_TRIAGE, Set.of()));
    ports.upsertIfNewer(new Team(elsewhere, UUID.randomUUID(), "Other control room", triage, Set.of(),
        false, 1));
    ports.upsertIfNewer(new Team(deleted, EVENT, "Old control room", triage, Set.of(), true, 2));
    member("rui", Role.RegularUser, elsewhere, deleted);
    var service = service();
    assertThatThrownBy(() -> service.list(EVENT, "rui", null)).isInstanceOf(ForbiddenException.class);
  }

  @Test
  @DisplayName("194 AC-4 an unknown request or a non-member gets 404")
  void unknownOrNonMember() {
    member("ana", Role.EventAdmin);
    var service = service();
    var unknown = UUID.randomUUID();
    assertThatThrownBy(() -> service.get(EVENT, "ana", unknown))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> service.get(EVENT, "nobody", unknown))
        .isInstanceOf(NotFoundException.class);
  }

  // US-35: the control room accepts or changes the suggestion (spec US-35 D1 to D8)

  static final Instant DECIDED = NOW.plusSeconds(60);
  static final TriageSuggestion SUGGESTED = TriageSuggestion.suggested(Category.Health, Priority.High, "fainted");

  /** A request of the event, submitted at NOW, with this triage stored at NOW + 5 s. */
  HelpRequest stored(TriageSuggestion triage) {
    return stored(UUID.randomUUID(), triage);
  }

  HelpRequest stored(UUID id, TriageSuggestion triage) {
    var request = HelpRequest.submit(id, EVENT, "ana", UUID.randomUUID(), "Friend fainted", POINT,
        NOW);
    ports.insertIfNew(request);
    if (!triage.equals(TriageSuggestion.pending())) {
      ports.saveTriage(request.withTriage(triage, NOW.plusSeconds(5)));
    }
    return ports.find(EVENT, request.id()).orElseThrow();
  }

  static Actor actor(String userId) {
    return new Actor(userId, "Name of " + userId);
  }

  HelpRequestService operatorService() {
    controlRoomTeam();
    member("miguel", Role.RegularUser, CONTROL_ROOM);
    clock = Clock.fixed(DECIDED, ZoneOffset.UTC);
    return service();
  }

  @Test
  @DisplayName("US-35 AC-1 an accept is saved as the member's decision")
  void acceptSaved() {
    var service = operatorService();
    var request = stored(SUGGESTED);

    var accepted = service.acceptSuggestion(EVENT, actor("miguel"), request.id());

    var saved = ports.find(EVENT, request.id()).orElseThrow();
    assertThat(accepted).isEqualTo(saved);
    assertThat(saved.category()).isEqualTo(Category.Health);
    assertThat(saved.priority()).isEqualTo(Priority.High);
    assertThat(saved.triage().state()).isEqualTo(TriageState.Accepted);
    assertThat(saved.updatedAt()).isEqualTo(DECIDED);
    assertThat(ports.decisions).singleElement().satisfies(d -> {
      assertThat(d.requestId()).isEqualTo(request.id());
      assertThat(d.actor()).isEqualTo(actor("miguel"));
      assertThat(d.changes()).extracting(HelpRequestChange::field).containsExactly("category", "priority", "triage");
    });
    assertThat(metrics.counted).containsExactly("decided Accepted");
  }

  @Test
  @DisplayName("US-35 AC-1 only the control room decides")
  void onlyTheControlRoomDecides() {
    var service = operatorService();
    member("ana", Role.RegularUser);
    var request = stored(SUGGESTED);
    var id = request.id();
    var elsewhere = UUID.randomUUID();
    ports.upsertIfNewer(new Membership(elsewhere, "miguel", Role.EventAdmin, Status.Active, Set.of(), 1));
    var ana = actor("ana");
    var nobody = actor("nobody");
    var miguel = actor("miguel");

    assertThatThrownBy(() -> service.acceptSuggestion(EVENT, ana, id)).isInstanceOf(ForbiddenException.class);
    assertThatThrownBy(() -> service.change(EVENT, ana, id, Category.Safety, null))
        .isInstanceOf(ForbiddenException.class);
    assertThatThrownBy(() -> service.acceptSuggestion(EVENT, nobody, id)).isInstanceOf(NotFoundException.class);
    // A control-room member of another event, or a request of another event, is not found
    assertThatThrownBy(() -> service.acceptSuggestion(elsewhere, miguel, id))
        .isInstanceOf(NotFoundException.class);
    var unknown = UUID.randomUUID();
    assertThatThrownBy(() -> service.change(EVENT, miguel, unknown, Category.Safety, null))
        .isInstanceOf(NotFoundException.class);
    assertThat(ports.find(EVENT, id).orElseThrow()).isEqualTo(request);
    assertThat(ports.decisions).isEmpty();
    assertThat(metrics.counted).isEmpty();
  }

  @Test
  @DisplayName("US-35 AC-2 a change that changes nothing saves nothing")
  void changeThatChangesNothing() {
    var service = operatorService();
    var request = stored(SUGGESTED);
    var changed = service.change(EVENT, actor("miguel"), request.id(), Category.Safety, Priority.Critical);
    assertThat(changed.triage().state()).isEqualTo(TriageState.Changed);
    assertThat(ports.decisions).hasSize(1);

    var same = service.change(EVENT, actor("miguel"), request.id(), Category.Safety, null);
    assertThat(same).isEqualTo(changed);
    assertThat(ports.decisions).hasSize(1);
    assertThat(metrics.counted).containsExactly("decided Changed");
  }

  @Test
  @DisplayName("US-35 AC-2 a suggestion that arrives after a member's values changes nothing")
  void suggestionAfterTheMember() {
    operatorService();
    var request = stored(TriageSuggestion.pending());
    clock = Clock.fixed(NOW.plusSeconds(2), ZoneOffset.UTC);
    var changed = service().change(EVENT, actor("miguel"), request.id(), Category.Safety, Priority.Medium);
    assertThat(changed.category()).isEqualTo(Category.Safety);
    assertThat(changed.priority()).isEqualTo(Priority.Medium);

    var model = new TriageServiceTest.FakeModel();
    var workerClock = new TriageServiceTest.MovingClock();
    workerClock.now = NOW.plusSeconds(3);
    model.clock = workerClock;
    var worker = new TriageService(ports, model, new TriageServiceTest.FakeCircuit(), metrics, workerClock);

    assertThat(worker.triage(EVENT, request.id()).outcome()).isEqualTo(TriageService.Outcome.ALREADY_TRIAGED);
    assertThat(model.descriptions).isEmpty();
    assertThat(ports.find(EVENT, request.id()).orElseThrow()).isEqualTo(changed);
    // A suggestion whose save comes after the member's values is refused too
    assertThat(ports.saveTriage(request.withTriage(SUGGESTED, NOW.plusSeconds(4)))).isFalse();
    assertThat(ports.find(EVENT, request.id()).orElseThrow()).isEqualTo(changed);
  }

  @Test
  @DisplayName("US-35 AC-1 the first decision on a suggestion is counted once")
  void decisionsCounted() {
    var service = operatorService();
    var accepted = stored(SUGGESTED);
    service.acceptSuggestion(EVENT, actor("miguel"), accepted.id());
    service.change(EVENT, actor("miguel"), accepted.id(), null, Priority.Critical);
    var changed = stored(SUGGESTED);
    service.change(EVENT, actor("miguel"), changed.id(), Category.Safety, null);
    var withoutSuggestion = stored(TriageSuggestion.notTriaged(NotTriagedReason.Timeout));
    service.change(EVENT, actor("miguel"), withoutSuggestion.id(), Category.Safety, Priority.High);

    assertThat(metrics.counted).containsExactly("decided Accepted", "decided Changed");
    assertThat(ports.decisions).hasSize(4);
  }

  @Test
  @DisplayName("US-35 AC-1 inside a transaction the decision is counted only after the commit")
  void countedAfterCommit() {
    var service = operatorService();
    var request = stored(SUGGESTED);
    TransactionSynchronizationManager.initSynchronization();
    try {
      service.acceptSuggestion(EVENT, actor("miguel"), request.id());
      assertThat(metrics.counted).isEmpty();
      TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
      assertThat(metrics.counted).containsExactly("decided Accepted");
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  @DisplayName("US-35 AC-1 a decision is logged without the description or the user")
  void decisionLogged() {
    var logger = (Logger) LoggerFactory.getLogger(HelpRequestService.class);
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);
    try {
      var service = operatorService();
      var request = stored(SUGGESTED);
      service.acceptSuggestion(EVENT, actor("miguel"), request.id());

      assertThat(appender.list).singleElement().satisfies(line -> {
        assertThat(line.getLevel()).isEqualTo(Level.INFO);
        assertThat(line.getFormattedMessage()).contains(request.id().toString(), "Suggested", "Accepted")
            .doesNotContain("miguel", "Friend", "fainted");
      });
    } finally {
      logger.detachAppender(appender);
    }
  }

  // US-23 AC-4: at most 5 help requests per account in 10 minutes (spec US-23 D1 to D6)

  /** Five requests of ana in EVENT, saved at NOW. */
  void fiveSaved() {
    member("ana", Role.RegularUser);
    for (int i = 0; i < 5; i++) {
      assertThat(service().submit(EVENT, "ana", UUID.randomUUID(), "help " + i, POINT).created()).isTrue();
    }
  }

  @Test
  @DisplayName("US-23 AC-4 the account is locked before its requests are counted")
  void lockedBeforeCounted() {
    member("ana", Role.RegularUser);
    service().submit(EVENT, "ana", UUID.randomUUID(), "help", POINT);
    assertThat(ports.calls).containsExactly("lock ana", "count ana after " + NOW.minus(Duration.ofMinutes(10)));
  }

  @Test
  @DisplayName("US-23 AC-4 a retry of a saved request passes at the limit and counts nothing")
  void retryAtTheLimit() {
    member("ana", Role.RegularUser);
    var first = service().submit(EVENT, "ana", UUID.randomUUID(), "help", POINT).request();
    for (int i = 0; i < 4; i++) {
      service().submit(EVENT, "ana", UUID.randomUUID(), "help " + i, POINT);
    }

    var retried = service().submit(EVENT, "ana", first.clientRequestId(), "help", POINT);

    assertThat(retried.created()).isFalse();
    assertThat(retried.request()).isEqualTo(first);
    assertThat(ports.requests).hasSize(5);
    assertThat(ports.outbox).hasSize(5);
    // A retry with other content is still a conflict, not a limit
    var service = service();
    var client = first.clientRequestId();
    assertThatThrownBy(() -> service.submit(EVENT, "ana", client, "other", POINT))
        .isInstanceOf(ClientRequestConflictException.class);
  }

  @Test
  @DisplayName("US-23 AC-4 refused requests are not counted")
  void refusedNotCounted() {
    fiveSaved();
    var service = service();
    var client = UUID.randomUUID();

    assertThatThrownBy(() -> service.submit(EVENT, "ana", client, "help", POINT))
        .isInstanceOfSatisfying(TooManyHelpRequestsException.class,
            e -> assertThat(e.retryAfter()).isEqualTo(Duration.ofMinutes(10)));
    // Invalid first (400), and a non-member first (404), even at the limit
    assertThatThrownBy(() -> service.submit(EVENT, "ana", client, " ", POINT))
        .isInstanceOf(pt.isep.labdsof.reports.domain.InvalidHelpRequestException.class);
    var elsewhere = UUID.randomUUID();
    assertThatThrownBy(() -> service.submit(elsewhere, "ana", client, "help", POINT))
        .isInstanceOf(NotFoundException.class);
    assertThat(ports.requests).hasSize(5);
    assertThat(ports.outbox).hasSize(5);

    // Ten minutes after the five, none of the refused ones counts: the next passes
    clock = Clock.fixed(NOW.plus(Duration.ofMinutes(10)), ZoneOffset.UTC);
    assertThat(service().submit(EVENT, "ana", client, "help", POINT).created()).isTrue();
  }

  @Test
  @DisplayName("US-23 AC-4 the time of a request is read after the account lock")
  void timeAfterTheLock() {
    member("ana", Role.RegularUser);
    var time = new java.util.concurrent.atomic.AtomicReference<>(NOW);
    var moving = new Clock() {
      @Override
      public ZoneOffset getZone() {
        return ZoneOffset.UTC;
      }

      @Override
      public Clock withZone(java.time.ZoneId zone) {
        return this;
      }

      @Override
      public Instant instant() {
        return time.get();
      }
    };
    // The lock is held by another submission of the account for 7 s
    ports.onLock = () -> time.set(NOW.plusSeconds(7));

    var saved = new HelpRequestService(ports, ports, ports, ports, metrics, LIMIT, moving)
        .submit(EVENT, "ana", UUID.randomUUID(), "help", POINT).request();

    assertThat(saved.submittedAt()).isEqualTo(NOW.plusSeconds(7));
    assertThat(saved.updatedAt()).isEqualTo(NOW.plusSeconds(7));
    assertThat(ports.calls).containsExactly("lock ana", "count ana after " + NOW.plusSeconds(7).minus(Duration.ofMinutes(10)));
  }

  @Test
  @DisplayName("US-34 AC-2 every control-room read and decision answers the default team")
  void defaultTeamOnEveryControlRoomRead() {
    var service = operatorService();
    var nursing = UUID.randomUUID();
    ports.upsertIfNewer(new Team(nursing, EVENT, "Nursing",
        List.of(new TeamPermission(Permission.HELP_REQUESTS_RESPOND, Set.of())), Set.of(Category.Health), false, 1));
    var security = UUID.randomUUID();
    ports.upsertIfNewer(new Team(security, EVENT, "Security",
        List.of(new TeamPermission(Permission.HELP_REQUESTS_RESPOND, Set.of())), Set.of(Category.Safety), false, 1));
    member("ana", Role.RegularUser);
    var request = stored(SUGGESTED);

    assertThat(service.get(EVENT, "miguel", request.id()).request().defaultTeamId()).isEqualTo(nursing);
    assertThat(service.list(EVENT, "miguel", null).items()).extracting(HelpRequest::defaultTeamId)
        .containsExactly(nursing);
    assertThat(service.list(EVENT, "miguel", NOW).items()).extracting(HelpRequest::defaultTeamId)
        .containsExactly(nursing);
    assertThat(service.change(EVENT, actor("miguel"), request.id(), Category.Safety, null).defaultTeamId())
        .isEqualTo(security);
    assertThat(service.acceptSuggestion(EVENT, actor("miguel"), stored(SUGGESTED).id()).defaultTeamId())
        .isEqualTo(nursing);
    // Nothing changed: the answer still carries the default team
    assertThat(service.change(EVENT, actor("miguel"), request.id(), Category.Safety, null).defaultTeamId())
        .isEqualTo(security);
    // The acknowledge (US-28) answers the same control-room view
    assertThat(service.acknowledge(EVENT, actor("miguel"), stored(SUGGESTED).id()).defaultTeamId())
        .isEqualTo(nursing);
    // The participant's own read is not the control room's, and the team is never stored
    assertThat(service.get(EVENT, "ana", request.id()).request().defaultTeamId()).isNull();
    assertThat(ports.find(EVENT, request.id()).orElseThrow().defaultTeamId()).isNull();
  }

  @Test
  @DisplayName("US-34 AC-2 a poll after a team change lists the open requests again, with those that changed")
  void pollAfterTeamChange() {
    var service = operatorService();
    // Fixed ids that a signed UUID.compareTo orders the other way round from the database (see below)
    var quiet = stored(UUID.fromString("80000000-0000-4000-8000-000000000000"), SUGGESTED);
    var since = NOW.plusSeconds(40);
    // A request changed in the window (accepted at DECIDED), and one that did not change
    var accepted = service.acceptSuggestion(EVENT, actor("miguel"),
        stored(UUID.fromString("00000000-0000-4000-8000-000000000001"), SUGGESTED).id());
    assertThat(service.list(EVENT, "miguel", since).items()).extracting(HelpRequest::id)
        .containsExactly(accepted.id());

    ports.teamsChangedAt.put(EVENT, since);
    assertThat(service.list(EVENT, "miguel", since).items()).extracting(HelpRequest::id)
        .containsExactly(accepted.id());
    ports.teamsChangedAt.put(EVENT, since.plusNanos(1000));
    assertThat(service.list(EVENT, "miguel", since).items()).extracting(HelpRequest::id)
        .containsExactlyInAnyOrder(quiet.id(), accepted.id());

    // A request closed in the window is still sent, so the page drops it (US-26), and only once. It arrived
    // before the open ones, so it comes first: the list stays in order of arrival, as the contract says
    var c = stored(SUGGESTED);
    var closed = new HelpRequest(c.id(), EVENT, c.requesterId(), c.clientRequestId(), c.description(), c.location(),
        c.category(), c.priority(), HelpRequestStatus.Closed, null, null, c.triage(), NOW.minusSeconds(1), DECIDED,
        c.version() + 1);
    ports.requests.put(c.id(), closed);
    // The two that arrived at the same time follow by id, compared as the database compares a uuid (by its bytes)
    assertThat(service.list(EVENT, "miguel", since).items()).extracting(HelpRequest::id)
        .containsExactly(closed.id(), accepted.id(), quiet.id());
  }

  // US-28: acknowledge and history (spec US-28 D2 to D8)

  @Test
  @DisplayName("US-28 AC-1 an acknowledge is saved as the member's change with their name")
  void acknowledgeSaved() {
    var service = operatorService();
    var request = stored(SUGGESTED);

    var seen = service.acknowledge(EVENT, actor("miguel"), request.id());

    var saved = ports.find(EVENT, request.id()).orElseThrow();
    assertThat(seen).isEqualTo(saved.asSeenAt(DECIDED));
    assertThat(saved.status()).isEqualTo(HelpRequestStatus.Seen);
    assertThat(saved.triage().state()).isEqualTo(TriageState.Suggested);
    assertThat(saved.updatedAt()).isEqualTo(DECIDED);
    assertThat(ports.decisions).singleElement().satisfies(d -> {
      assertThat(d.actor()).isEqualTo(new Actor("miguel", "Name of miguel"));
      assertThat(d.changes()).containsExactly(new HelpRequestChange("status", "Submitted", "Seen"));
    });
    // Not a triage decision: the suggestion counter is untouched
    assertThat(metrics.counted).isEmpty();
    assertThatThrownBy(() -> service.acknowledge(EVENT, actor("miguel"), request.id()))
        .isInstanceOf(pt.isep.labdsof.reports.domain.RequestConflictException.class);
    assertThat(ports.decisions).hasSize(1);
  }

  @Test
  @DisplayName("US-28 AC-1 only the control room acknowledges or reads the history")
  void onlyTheControlRoomAcknowledges() {
    var service = operatorService();
    member("ana", Role.RegularUser);
    var id = stored(SUGGESTED).id();
    var elsewhere = UUID.randomUUID();
    ports.upsertIfNewer(new Membership(elsewhere, "miguel", Role.EventAdmin, Status.Active, Set.of(), 1));
    var ana = actor("ana");
    var nobody = actor("nobody");
    var miguel = actor("miguel");

    assertThatThrownBy(() -> service.acknowledge(EVENT, ana, id)).isInstanceOf(ForbiddenException.class);
    assertThatThrownBy(() -> service.history(EVENT, "ana", id)).isInstanceOf(ForbiddenException.class);
    assertThatThrownBy(() -> service.acknowledge(EVENT, nobody, id)).isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> service.history(EVENT, "nobody", id)).isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> service.acknowledge(elsewhere, miguel, id))
        .isInstanceOf(NotFoundException.class);
    var unknown = UUID.randomUUID();
    assertThatThrownBy(() -> service.acknowledge(EVENT, miguel, unknown))
        .isInstanceOf(NotFoundException.class);
    assertThat(ports.find(EVENT, id).orElseThrow().status()).isEqualTo(HelpRequestStatus.Submitted);
    assertThat(ports.decisions).isEmpty();
  }

  @Test
  @DisplayName("US-28 AC-2 the history is read only for a request of the event")
  void history() {
    var service = operatorService();
    var request = stored(SUGGESTED);
    var other = stored(SUGGESTED);
    assertThat(service.history(EVENT, "miguel", request.id())).isEmpty();

    service.change(EVENT, actor("miguel"), request.id(), null, Priority.Critical);
    service.acknowledge(EVENT, new Actor("miguel", null), request.id());
    service.acknowledge(EVENT, actor("miguel"), other.id());

    assertThat(service.history(EVENT, "miguel", request.id())).containsExactly(
        new HistoryEntry("priority", null, "Critical", "miguel", "Name of miguel", DECIDED),
        new HistoryEntry("triage", "Suggested", "Changed", "miguel", "Name of miguel", DECIDED),
        new HistoryEntry("status", "Submitted", "Seen", "miguel", null, DECIDED));
    var elsewhere = UUID.randomUUID();
    ports.upsertIfNewer(new Membership(elsewhere, "miguel", Role.EventAdmin, Status.Active, Set.of(), 1));
    var id = request.id();
    assertThatThrownBy(() -> service.history(elsewhere, "miguel", id)).isInstanceOf(NotFoundException.class);
    var unknown = UUID.randomUUID();
    assertThatThrownBy(() -> service.history(EVENT, "miguel", unknown)).isInstanceOf(NotFoundException.class);
  }

  @Test
  @DisplayName("US-28 AC-1 an acknowledge is logged without the description or the user")
  void acknowledgeLogged() {
    var logger = (Logger) LoggerFactory.getLogger(HelpRequestService.class);
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);
    try {
      var service = operatorService();
      var request = stored(SUGGESTED);
      service.acknowledge(EVENT, actor("miguel"), request.id());

      assertThat(appender.list).singleElement().satisfies(line -> {
        assertThat(line.getLevel()).isEqualTo(Level.INFO);
        assertThat(line.getFormattedMessage()).contains(request.id().toString(), "Submitted", "Seen")
            .doesNotContain("miguel", "Friend", "fainted");
      });
    } finally {
      logger.detachAppender(appender);
    }
  }

  static final UUID NURSING = UUID.randomUUID();
  static final UUID SECURITY = UUID.randomUUID();

  void respondingTeam(UUID teamId, Category category) {
    ports.upsertIfNewer(new Team(teamId, EVENT, "Team " + teamId,
        List.of(new TeamPermission(Permission.HELP_REQUESTS_RESPOND, Set.of())), Set.of(category), false, 1));
  }

  @Test
  @DisplayName("US-29 AC-2 a route dispatches an open task to the team and is saved as the member's change")
  void routeSaved() {
    var service = operatorService();
    respondingTeam(NURSING, Category.Health);
    var request = stored(SUGGESTED);

    var routed = service.route(EVENT, actor("miguel"), request.id(), NURSING);

    var saved = ports.find(EVENT, request.id()).orElseThrow();
    assertThat(saved.routedTeamId()).isEqualTo(NURSING);
    assertThat(saved.status()).isEqualTo(HelpRequestStatus.Submitted);
    assertThat(saved.updatedAt()).isEqualTo(DECIDED);
    // The control-room view, default team included (D9)
    assertThat(routed).isEqualTo(saved.asSeenAt(DECIDED).withDefaultTeam(ports.teams(EVENT)));
    assertThat(routed.defaultTeamId()).isEqualTo(NURSING);
    assertThat(ports.decisions).singleElement().satisfies(d -> {
      assertThat(d.actor()).isEqualTo(new Actor("miguel", "Name of miguel"));
      assertThat(d.changes()).containsExactly(new HelpRequestChange("routedTeamId", null, NURSING.toString()));
    });
    assertThat(ports.tasks).singleElement().satisfies(task -> {
      assertThat(task.eventId()).isEqualTo(EVENT);
      assertThat(task.requestId()).isEqualTo(request.id());
      assertThat(task.teamId()).isEqualTo(NURSING);
      assertThat(task.status()).isEqualTo("Open");
      assertThat(task.at()).isEqualTo(DECIDED);
    });
    assertThat(metrics.counted).isEmpty();
  }

  @Test
  @DisplayName("US-29 AC-2 routing again cancels the open task and dispatches a new one")
  void routeAgain() {
    var service = operatorService();
    respondingTeam(NURSING, Category.Health);
    respondingTeam(SECURITY, Category.Safety);
    var request = stored(SUGGESTED);

    service.route(EVENT, actor("miguel"), request.id(), NURSING);
    service.route(EVENT, actor("miguel"), request.id(), SECURITY);

    assertThat(ports.find(EVENT, request.id()).orElseThrow().routedTeamId()).isEqualTo(SECURITY);
    assertThat(ports.tasks).extracting(InMemoryPorts.Task::teamId, InMemoryPorts.Task::status)
        .containsExactly(org.assertj.core.groups.Tuple.tuple(NURSING, "Cancelled"),
            org.assertj.core.groups.Tuple.tuple(SECURITY, "Open"));
    assertThat(ports.decisions).extracting(InMemoryPorts.Decision::changes).containsExactly(
        List.of(new HelpRequestChange("routedTeamId", null, NURSING.toString())),
        List.of(new HelpRequestChange("routedTeamId", NURSING.toString(), SECURITY.toString())));
  }

  @Test
  @DisplayName("US-29 AC-2 routing to the same team again writes nothing")
  void routeToTheSameTeam() {
    var service = operatorService();
    respondingTeam(NURSING, Category.Health);
    var request = stored(SUGGESTED);
    service.route(EVENT, actor("miguel"), request.id(), NURSING);
    var saved = ports.find(EVENT, request.id()).orElseThrow();

    var again = service.route(EVENT, actor("miguel"), request.id(), NURSING);

    assertThat(again.version()).isEqualTo(saved.version());
    assertThat(ports.find(EVENT, request.id()).orElseThrow()).isEqualTo(saved);
    assertThat(ports.decisions).hasSize(1);
    assertThat(ports.tasks).singleElement().extracting(InMemoryPorts.Task::status).isEqualTo("Open");
  }

  @Test
  @DisplayName("US-29 AC-3 a team that cannot respond gets 422 and nothing is written")
  void routeToATeamThatCannotRespond() {
    var service = operatorService();
    var request = stored(SUGGESTED);
    var elsewhere = UUID.randomUUID();
    ports.upsertIfNewer(new Team(elsewhere, UUID.randomUUID(), "Elsewhere",
        List.of(new TeamPermission(Permission.HELP_REQUESTS_RESPOND, Set.of())), Set.of(Category.Health), false, 1));
    var miguel = actor("miguel");
    var id = request.id();

    for (var team : List.of(CONTROL_ROOM, elsewhere, UUID.randomUUID())) {
      assertThatThrownBy(() -> service.route(EVENT, miguel, id, team))
          .isInstanceOf(pt.isep.labdsof.reports.domain.IneligibleTeamException.class);
    }
    assertThat(ports.find(EVENT, id).orElseThrow()).isEqualTo(request);
    assertThat(ports.decisions).isEmpty();
    assertThat(ports.tasks).isEmpty();
  }

  @Test
  @DisplayName("US-29 AC-2 only the control room routes")
  void onlyTheControlRoomRoutes() {
    var service = operatorService();
    respondingTeam(NURSING, Category.Health);
    member("nurse", Role.RegularUser, NURSING);
    member("ana", Role.RegularUser);
    var id = stored(SUGGESTED).id();
    var elsewhere = UUID.randomUUID();
    ports.upsertIfNewer(new Membership(elsewhere, "miguel", Role.EventAdmin, Status.Active, Set.of(), 1));
    var miguel = actor("miguel");
    var nurse = actor("nurse");
    var ana = actor("ana");
    var nobody = actor("nobody");

    assertThatThrownBy(() -> service.route(EVENT, nurse, id, NURSING)).isInstanceOf(ForbiddenException.class);
    assertThatThrownBy(() -> service.route(EVENT, ana, id, NURSING)).isInstanceOf(ForbiddenException.class);
    assertThatThrownBy(() -> service.route(EVENT, nobody, id, NURSING)).isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> service.route(elsewhere, miguel, id, NURSING)).isInstanceOf(NotFoundException.class);
    var unknown = UUID.randomUUID();
    // An unknown request is 404 even with an unusable team: the request is read first (D8)
    assertThatThrownBy(() -> service.route(EVENT, miguel, unknown, NURSING)).isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> service.route(EVENT, miguel, unknown, CONTROL_ROOM))
        .isInstanceOf(NotFoundException.class);
    assertThat(ports.find(EVENT, id).orElseThrow().routedTeamId()).isNull();
    assertThat(ports.decisions).isEmpty();
    assertThat(ports.tasks).isEmpty();
  }

  @Test
  @DisplayName("US-29 AC-2 a route is logged without the description or the user")
  void routeLogged() {
    var logger = (Logger) LoggerFactory.getLogger(HelpRequestService.class);
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);
    try {
      var service = operatorService();
      respondingTeam(NURSING, Category.Health);
      var request = stored(SUGGESTED);
      service.route(EVENT, actor("miguel"), request.id(), NURSING);
      // The same team again writes nothing, so it logs nothing
      service.route(EVENT, actor("miguel"), request.id(), NURSING);

      assertThat(appender.list).singleElement().satisfies(line -> {
        assertThat(line.getLevel()).isEqualTo(Level.INFO);
        assertThat(line.getFormattedMessage()).contains(request.id().toString(), "null", NURSING.toString())
            .doesNotContain("miguel", "Friend", "fainted");
      });
    } finally {
      logger.detachAppender(appender);
    }
  }
}
