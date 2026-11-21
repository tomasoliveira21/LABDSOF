package pt.isep.labdsof.reports.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Clock;
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
import pt.isep.labdsof.reports.domain.Category;
import pt.isep.labdsof.reports.domain.HelpRequest;
import pt.isep.labdsof.reports.domain.HelpRequestChange;
import pt.isep.labdsof.reports.domain.HelpRequestStatus;
import pt.isep.labdsof.reports.domain.Location;
import pt.isep.labdsof.reports.domain.Membership.Role;
import pt.isep.labdsof.reports.domain.Membership.Status;
import pt.isep.labdsof.reports.domain.Membership;
import pt.isep.labdsof.reports.domain.NotTriagedReason;
import pt.isep.labdsof.reports.domain.Permission;
import pt.isep.labdsof.reports.domain.Priority;
import pt.isep.labdsof.reports.domain.Team.TeamPermission;
import pt.isep.labdsof.reports.domain.Team;
import pt.isep.labdsof.reports.domain.TriageState;
import pt.isep.labdsof.reports.domain.TriageSuggestion;

class HelpRequestServiceTest {

  static final Instant NOW = Instant.parse("2027-05-08T23:14:05Z");
  static final UUID EVENT = UUID.randomUUID();
  static final UUID CONTROL_ROOM = UUID.randomUUID();
  static final Location POINT = new Location(3, 0.42, 0.77);

  final InMemoryPorts ports = new InMemoryPorts();
  final TriageServiceTest.FakeMetrics metrics = new TriageServiceTest.FakeMetrics();
  Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  HelpRequestService service() {
    return new HelpRequestService(ports, ports, ports, metrics, clock);
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
      public List<HelpRequest> open(UUID eventId) {
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
      public void saveDecision(HelpRequest decided, String actorUserId, List<HelpRequestChange> changes) {
        throw new UnsupportedOperationException();
      }
    };
    var service = new HelpRequestService(refusing, ports, ports, metrics, clock);
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
    var request = HelpRequest.submit(UUID.randomUUID(), EVENT, "ana", UUID.randomUUID(), "Friend fainted", POINT,
        NOW);
    ports.insertIfNew(request);
    if (!triage.equals(TriageSuggestion.pending())) {
      ports.saveTriage(request.withTriage(triage, NOW.plusSeconds(5)));
    }
    return ports.find(EVENT, request.id()).orElseThrow();
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

    var accepted = service.acceptSuggestion(EVENT, "miguel", request.id());

    var saved = ports.find(EVENT, request.id()).orElseThrow();
    assertThat(accepted).isEqualTo(saved);
    assertThat(saved.category()).isEqualTo(Category.Health);
    assertThat(saved.priority()).isEqualTo(Priority.High);
    assertThat(saved.triage().state()).isEqualTo(TriageState.Accepted);
    assertThat(saved.updatedAt()).isEqualTo(DECIDED);
    assertThat(ports.decisions).singleElement().satisfies(d -> {
      assertThat(d.requestId()).isEqualTo(request.id());
      assertThat(d.actorUserId()).isEqualTo("miguel");
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

    assertThatThrownBy(() -> service.acceptSuggestion(EVENT, "ana", id)).isInstanceOf(ForbiddenException.class);
    assertThatThrownBy(() -> service.change(EVENT, "ana", id, Category.Safety, null))
        .isInstanceOf(ForbiddenException.class);
    assertThatThrownBy(() -> service.acceptSuggestion(EVENT, "nobody", id)).isInstanceOf(NotFoundException.class);
    // A control-room member of another event, or a request of another event, is not found
    assertThatThrownBy(() -> service.acceptSuggestion(elsewhere, "miguel", id))
        .isInstanceOf(NotFoundException.class);
    var unknown = UUID.randomUUID();
    assertThatThrownBy(() -> service.change(EVENT, "miguel", unknown, Category.Safety, null))
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
    var changed = service.change(EVENT, "miguel", request.id(), Category.Safety, Priority.Critical);
    assertThat(changed.triage().state()).isEqualTo(TriageState.Changed);
    assertThat(ports.decisions).hasSize(1);

    var same = service.change(EVENT, "miguel", request.id(), Category.Safety, null);
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
    var changed = service().change(EVENT, "miguel", request.id(), Category.Safety, Priority.Medium);
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
    service.acceptSuggestion(EVENT, "miguel", accepted.id());
    service.change(EVENT, "miguel", accepted.id(), null, Priority.Critical);
    var changed = stored(SUGGESTED);
    service.change(EVENT, "miguel", changed.id(), Category.Safety, null);
    var withoutSuggestion = stored(TriageSuggestion.notTriaged(NotTriagedReason.Timeout));
    service.change(EVENT, "miguel", withoutSuggestion.id(), Category.Safety, Priority.High);

    assertThat(metrics.counted).containsExactly("decided Accepted", "decided Changed");
    assertThat(ports.decisions).hasSize(4);
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
      service.acceptSuggestion(EVENT, "miguel", request.id());

      assertThat(appender.list).singleElement().satisfies(line -> {
        assertThat(line.getLevel()).isEqualTo(Level.INFO);
        assertThat(line.getFormattedMessage()).contains(request.id().toString(), "Suggested", "Accepted")
            .doesNotContain("miguel", "Friend", "fainted");
      });
    } finally {
      logger.detachAppender(appender);
    }
  }
}
