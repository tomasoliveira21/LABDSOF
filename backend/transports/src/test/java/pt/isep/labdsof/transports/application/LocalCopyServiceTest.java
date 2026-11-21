package pt.isep.labdsof.transports.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pt.isep.labdsof.transports.application.LocalCopyService.Outcome;
import pt.isep.labdsof.transports.domain.EventCopy;
import pt.isep.labdsof.transports.domain.EventStatus;
import pt.isep.labdsof.transports.domain.Membership;
import pt.isep.labdsof.transports.domain.Membership.Role;
import pt.isep.labdsof.transports.domain.Membership.Status;
import pt.isep.labdsof.transports.domain.Permission;
import pt.isep.labdsof.transports.domain.Team;

class LocalCopyServiceTest {

  private static final UUID EVENT = UUID.randomUUID();
  private static final UUID USER = UUID.randomUUID();
  private static final UUID TEAM = UUID.randomUUID();

  private InMemoryPorts ports;
  private LocalCopyService service;

  @BeforeEach
  void setUp() {
    ports = new InMemoryPorts();
    service = new LocalCopyService(ports, ports);
  }

  private static Membership membership(Role role, long version) {
    return new Membership(EVENT, USER, role, Status.Active, Set.of(TEAM), version);
  }

  private static EventCopy event(EventStatus status, long version) {
    return new EventCopy(EVENT, status, version);
  }

  private static Team team(UUID eventId, Set<Permission> permissions, long version) {
    return new Team(TEAM, eventId, permissions, false, version);
  }

  @Test
  @DisplayName("193 AC-5 a new membership is applied")
  void aNewMembershipIsApplied() {
    assertThat(service.apply(UUID.randomUUID(), membership(Role.RegularUser, 1))).isEqualTo(Outcome.APPLIED);
    assertThat(ports.membership(EVENT, USER)).contains(membership(Role.RegularUser, 1));

    assertThat(service.apply(UUID.randomUUID(), membership(Role.EventAdmin, 2))).isEqualTo(Outcome.APPLIED);
    assertThat(ports.membership(EVENT, USER)).contains(membership(Role.EventAdmin, 2));
  }

  @ParameterizedTest(name = "version {0} after version 2")
  @ValueSource(longs = {1, 2})
  @DisplayName("193 AC-5 an older or equal membership version is stale")
  void anOlderMembershipIsStale(long version) {
    service.apply(UUID.randomUUID(), membership(Role.EventAdmin, 2));
    assertThat(service.apply(UUID.randomUUID(), membership(Role.RegularUser, version))).isEqualTo(Outcome.STALE);
    assertThat(ports.membership(EVENT, USER)).contains(membership(Role.EventAdmin, 2));
  }

  @Test
  @DisplayName("193 AC-5 a duplicate membership messageId is applied once")
  void aDuplicateMembershipIsAppliedOnce() {
    var messageId = UUID.randomUUID();
    service.apply(messageId, membership(Role.RegularUser, 1));
    assertThat(service.apply(messageId, membership(Role.EventAdmin, 2))).isEqualTo(Outcome.DUPLICATE);
    assertThat(ports.membership(EVENT, USER)).contains(membership(Role.RegularUser, 1));
  }

  @Test
  @DisplayName("193 AC-5 a new event status is applied")
  void aNewEventIsApplied() {
    assertThat(service.apply(UUID.randomUUID(), event(EventStatus.Draft, 1))).isEqualTo(Outcome.APPLIED);
    assertThat(service.apply(UUID.randomUUID(), event(EventStatus.Suspended, 2))).isEqualTo(Outcome.APPLIED);
    assertThat(ports.event(EVENT)).contains(event(EventStatus.Suspended, 2));
  }

  @ParameterizedTest(name = "version {0} after version 2")
  @ValueSource(longs = {1, 2})
  @DisplayName("193 AC-5 an older or equal event version is stale")
  void anOlderEventIsStale(long version) {
    service.apply(UUID.randomUUID(), event(EventStatus.Published, 2));
    assertThat(service.apply(UUID.randomUUID(), event(EventStatus.Draft, version))).isEqualTo(Outcome.STALE);
    assertThat(ports.event(EVENT)).contains(event(EventStatus.Published, 2));
  }

  @Test
  @DisplayName("193 AC-5 a duplicate event messageId is applied once")
  void aDuplicateEventIsAppliedOnce() {
    var messageId = UUID.randomUUID();
    service.apply(messageId, event(EventStatus.Draft, 1));
    assertThat(service.apply(messageId, event(EventStatus.Published, 2))).isEqualTo(Outcome.DUPLICATE);
    assertThat(ports.event(EVENT)).contains(event(EventStatus.Draft, 1));
  }

  @Test
  @DisplayName("193 AC-5 a new team is applied")
  void aNewTeamIsApplied() {
    assertThat(service.apply(UUID.randomUUID(), team(EVENT, Set.of(), 1))).isEqualTo(Outcome.APPLIED);
    var withTransport = team(EVENT, Set.of(Permission.TRANSPORT_EDIT), 2);
    assertThat(service.apply(UUID.randomUUID(), withTransport)).isEqualTo(Outcome.APPLIED);
    assertThat(ports.team(TEAM)).contains(withTransport);
  }

  @ParameterizedTest(name = "version {0} after version 2")
  @ValueSource(longs = {1, 2})
  @DisplayName("193 AC-5 an older or equal team version is stale")
  void anOlderTeamIsStale(long version) {
    var stored = team(EVENT, Set.of(Permission.TRANSPORT_EDIT), 2);
    service.apply(UUID.randomUUID(), stored);
    assertThat(service.apply(UUID.randomUUID(), team(EVENT, Set.of(), version))).isEqualTo(Outcome.STALE);
    assertThat(ports.team(TEAM)).contains(stored);
  }

  @Test
  @DisplayName("193 AC-5 a duplicate team messageId is applied once")
  void aDuplicateTeamIsAppliedOnce() {
    var messageId = UUID.randomUUID();
    var first = team(EVENT, Set.of(), 1);
    service.apply(messageId, first);
    assertThat(service.apply(messageId, team(EVENT, Set.of(Permission.TRANSPORT_EDIT), 2)))
        .isEqualTo(Outcome.DUPLICATE);
    assertThat(ports.team(TEAM)).contains(first);
  }

  @Test
  @DisplayName("193 AC-5 a team copy never moves to another event")
  void aTeamNeverMovesToAnotherEvent() {
    var stored = team(EVENT, Set.of(), 1);
    service.apply(UUID.randomUUID(), stored);
    var elsewhere = team(UUID.randomUUID(), Set.of(Permission.TRANSPORT_EDIT), 2);
    assertThat(service.apply(UUID.randomUUID(), elsewhere)).isEqualTo(Outcome.OTHER_EVENT);
    assertThat(ports.team(TEAM)).contains(stored);
  }
}
