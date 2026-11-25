package pt.isep.labdsof.reports.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pt.isep.labdsof.reports.application.LocalCopyService.Outcome;
import pt.isep.labdsof.reports.domain.Membership;
import pt.isep.labdsof.reports.domain.Membership.Role;
import pt.isep.labdsof.reports.domain.Membership.Status;
import pt.isep.labdsof.reports.domain.HelpRequest;
import pt.isep.labdsof.reports.domain.HelpRequestStatus;
import pt.isep.labdsof.reports.domain.Location;
import pt.isep.labdsof.reports.domain.Team;

class LocalCopyServiceTest {

  static final UUID EVENT = UUID.randomUUID();
  static final UUID TEAM = UUID.randomUUID();
  static final Instant NOW = Instant.parse("2027-05-08T23:14:05Z");

  final InMemoryPorts ports = new InMemoryPorts();
  final LocalCopyService service = new LocalCopyService(ports, ports, ports, ports, Clock.fixed(NOW, ZoneOffset.UTC));

  static Membership membership(Role role, long version) {
    return new Membership(EVENT, "ana", role, Status.Active, Set.of(), version);
  }

  static Team team(String name, long version) {
    return new Team(TEAM, EVENT, name, List.of(), Set.of(), false, version);
  }

  @Test
  @DisplayName("194 AC-3 a MembershipChanged is applied, a duplicate messageId is ignored")
  void membershipDuplicate() {
    var message = UUID.randomUUID();
    assertThat(service.apply(message, membership(Role.RegularUser, 1))).isEqualTo(Outcome.APPLIED);
    assertThat(service.apply(message, membership(Role.EventAdmin, 2))).isEqualTo(Outcome.DUPLICATE);
    assertThat(ports.membership(EVENT, "ana").orElseThrow().role()).isEqualTo(Role.RegularUser);
  }

  @Test
  @DisplayName("194 AC-3 an older or equal MembershipChanged version is ignored")
  void membershipStale() {
    service.apply(UUID.randomUUID(), membership(Role.EventAdmin, 2));
    assertThat(service.apply(UUID.randomUUID(), membership(Role.RegularUser, 1)))
        .isEqualTo(Outcome.STALE);
    assertThat(service.apply(UUID.randomUUID(), membership(Role.RegularUser, 2)))
        .isEqualTo(Outcome.STALE);
    assertThat(ports.membership(EVENT, "ana").orElseThrow().role()).isEqualTo(Role.EventAdmin);
  }

  @Test
  @DisplayName("194 AC-3 a TeamChanged is applied, a duplicate or older one is ignored")
  void teams() {
    var message = UUID.randomUUID();
    assertThat(service.apply(message, team("Nursing", 1))).isEqualTo(Outcome.APPLIED);
    assertThat(service.apply(message, team("Renamed", 2))).isEqualTo(Outcome.DUPLICATE);
    assertThat(service.apply(UUID.randomUUID(), team("Security", 3))).isEqualTo(Outcome.APPLIED);
    assertThat(service.apply(UUID.randomUUID(), team("Old", 2))).isEqualTo(Outcome.STALE);
    assertThat(ports.teams(EVENT, Set.of(TEAM))).singleElement()
        .extracting(Team::name).isEqualTo("Security");
  }

  /** A request of the event routed to {@code teamId}, in {@code status}, with its live task when it has one. */
  HelpRequest routed(UUID teamId, HelpRequestStatus status) {
    var r = HelpRequest.submit(UUID.randomUUID(), EVENT, "ana", UUID.randomUUID(), "help", new Location(1, 0.5, 0.5),
        NOW.minusSeconds(60));
    var request = new HelpRequest(r.id(), EVENT, r.requesterId(), r.clientRequestId(), r.description(),
        r.location(), null, null, status, null, teamId, r.triage(), r.submittedAt(), r.updatedAt(), 3);
    ports.requests.put(request.id(), request);
    var live = status == HelpRequestStatus.Submitted || status == HelpRequestStatus.Seen ? "Open"
        : status == HelpRequestStatus.OnTheWay ? "Accepted" : "Resolved";
    ports.tasks.add(new InMemoryPorts.Task(UUID.randomUUID(), EVENT, request.id(), teamId, live, r.submittedAt()));
    return request;
  }

  @Test
  @DisplayName("US-29 AC-2 a deleted team's requests go back to the queue and its tasks are cancelled")
  void deletedTeam() {
    service.apply(UUID.randomUUID(), team("Nursing", 1));
    var other = UUID.randomUUID();
    var submitted = routed(TEAM, HelpRequestStatus.Submitted);
    var seen = routed(TEAM, HelpRequestStatus.Seen);
    var onTheWay = routed(TEAM, HelpRequestStatus.OnTheWay);
    var resolved = routed(TEAM, HelpRequestStatus.Resolved);
    var elsewhere = routed(other, HelpRequestStatus.Seen);
    // A finished request whose task is still live (out of step, G8 review): the request stays, the task is cancelled
    var closed = routed(TEAM, HelpRequestStatus.Closed);
    ports.tasks.replaceAll(t -> t.requestId().equals(closed.id())
        ? new InMemoryPorts.Task(t.id(), EVENT, t.requestId(), TEAM, "Accepted", t.at()) : t);
    var deleted = new Team(TEAM, EVENT, "Nursing", List.of(), Set.of(), true, 2);

    var message = UUID.randomUUID();
    assertThat(service.apply(message, deleted)).isEqualTo(Outcome.APPLIED);

    for (var request : List.of(submitted, seen, onTheWay)) {
      var returned = ports.requests.get(request.id());
      assertThat(returned.routedTeamId()).isNull();
      assertThat(returned.status())
          .isEqualTo(request.status() == HelpRequestStatus.OnTheWay ? HelpRequestStatus.Seen : request.status());
      assertThat(returned.version()).isEqualTo(4);
      assertThat(returned.updatedAt()).isEqualTo(NOW);
    }
    assertThat(ports.requests.get(resolved.id())).isEqualTo(resolved);
    assertThat(ports.requests.get(elsewhere.id())).isEqualTo(elsewhere);
    assertThat(ports.tasks).extracting(InMemoryPorts.Task::requestId, InMemoryPorts.Task::status)
        .containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple(submitted.id(), "Cancelled"),
            org.assertj.core.groups.Tuple.tuple(seen.id(), "Cancelled"),
            org.assertj.core.groups.Tuple.tuple(onTheWay.id(), "Cancelled"),
            org.assertj.core.groups.Tuple.tuple(resolved.id(), "Resolved"),
            org.assertj.core.groups.Tuple.tuple(elsewhere.id(), "Open"),
            org.assertj.core.groups.Tuple.tuple(closed.id(), "Cancelled"));
    assertThat(ports.requests.get(closed.id())).isEqualTo(closed);
    // A system change: no history entry (US-28 D6)
    assertThat(ports.decisions).allSatisfy(d -> assertThat(d.changes()).isEmpty());

    // A repeated or stale deletion changes nothing more
    var after = Map.copyOf(ports.requests);
    var tasks = List.copyOf(ports.tasks);
    assertThat(service.apply(message, deleted)).isEqualTo(Outcome.DUPLICATE);
    assertThat(service.apply(UUID.randomUUID(), new Team(TEAM, EVENT, "Nursing", List.of(), Set.of(), true, 1)))
        .isEqualTo(Outcome.STALE);
    var routedAgain = routed(TEAM, HelpRequestStatus.Seen);
    assertThat(service.apply(UUID.randomUUID(), new Team(TEAM, EVENT, "Nursing", List.of(), Set.of(), true, 2)))
        .isEqualTo(Outcome.STALE);
    assertThat(ports.requests.get(routedAgain.id())).isEqualTo(routedAgain);
    assertThat(ports.requests).containsAllEntriesOf(after);
    assertThat(ports.tasks).containsAll(tasks);
    // A team that is not deleted keeps its requests
    assertThat(service.apply(UUID.randomUUID(), team("Nursing", 3))).isEqualTo(Outcome.APPLIED);
    assertThat(ports.requests.get(routedAgain.id())).isEqualTo(routedAgain);
  }
}
