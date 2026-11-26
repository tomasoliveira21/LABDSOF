package pt.isep.labdsof.authusers.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import pt.isep.labdsof.authusers.application.MemberAudit.Refusal;
import pt.isep.labdsof.authusers.domain.EventMembership;
import pt.isep.labdsof.authusers.domain.EventRole;

/** US-18: adding a member of an event to one of its teams and removing them, with the ports faked (spec §4 F2). */
class TeamMemberServiceTest {

  private static final Instant NOW = Instant.parse("2027-04-30T10:15:30.123456Z");

  private final InMemoryPorts ports = new InMemoryPorts();
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  private final TeamMemberService service = new TeamMemberService(new EventAdmins(ports, ports, clock), ports,
      ports, ports, ports, clock);
  private final UUID event = UUID.randomUUID();
  private final UUID sofia = UUID.randomUUID();
  private final UUID rita = UUID.randomUUID();
  private final UUID nursing = UUID.randomUUID();

  private void admin(UUID userId) {
    ports.member(new EventMembership(event, userId, EventRole.EventAdmin, List.of(), 1));
  }

  private EventMembership regular(UUID userId, UUID... teams) {
    var membership = new EventMembership(event, userId, EventRole.RegularUser, List.of(teams), 1);
    ports.member(membership);
    return membership;
  }

  private EventMembership stored(UUID userId) {
    return ports.find(event, userId).orElseThrow();
  }

  @Test
  @DisplayName("US-18 AC-1 an EventAdmin adds a member to a team: the assignment, the version and the event")
  void anEventAdminAddsAMemberToATeam() {
    admin(sofia);
    regular(rita);
    ports.liveTeam(event, nursing);

    service.add(sofia, event, rita, nursing);

    assertThat(stored(rita).teamIds()).containsExactly(nursing);
    assertThat(stored(rita).version()).isEqualTo(2);
    assertThat(ports.assignments).singleElement().satisfies(assignment -> {
      assertThat(assignment.added()).isTrue();
      assertThat(assignment.actorUserId()).isEqualTo(sofia);
      assertThat(assignment.teamId()).isEqualTo(nursing);
      assertThat(assignment.at()).isEqualTo(NOW);
    });
    assertThat(ports.locked).containsExactly(rita); // the membership is locked before its version is raised (D7)
    assertThat(ports.audited).singleElement().satisfies(line -> assertThat(line.added()).isTrue());
    assertThat(ports.outbox).singleElement().satisfies(published -> {
      assertThat(published.type()).isEqualTo("MembershipChanged");
      assertThat(published.routingKey()).isEqualTo("membership.changed");
      assertThat(published.eventId()).isEqualTo(event);
      assertThat(published.actorUserId()).isEqualTo(sofia.toString());
      assertThat(published.occurredAt()).isEqualTo(NOW);
      assertThat(published.payload()).containsExactlyInAnyOrderEntriesOf(Map.of(
          "userId", rita.toString(), "role", "RegularUser", "status", "Active",
          "teamIds", List.of(nursing.toString()), "version", 2L));
    });
  }

  @Test
  @DisplayName("US-18 AC-3 a member can be in two teams and both are published")
  void aMemberCanBeInTwoTeams() {
    admin(sofia);
    regular(rita);
    var security = UUID.randomUUID();
    ports.liveTeam(event, nursing);
    ports.liveTeam(event, security);

    service.add(sofia, event, rita, nursing);
    service.add(sofia, event, rita, security);

    assertThat(stored(rita).teamIds()).containsExactlyInAnyOrder(nursing, security);
    assertThat(stored(rita).version()).isEqualTo(3);
    assertThat(ports.outbox).hasSize(2);
    assertThat(ports.outbox.getLast().payload().get("teamIds"))
        .isEqualTo(stored(rita).teamIds().stream().map(UUID::toString).toList());
  }

  @Test
  @DisplayName("US-18 AC-1 adding a member twice changes nothing and publishes nothing")
  void addingAMemberTwiceChangesNothing() {
    admin(sofia);
    regular(rita, nursing);
    ports.liveTeam(event, nursing);

    service.add(sofia, event, rita, nursing);

    assertThat(stored(rita).version()).isEqualTo(1);
    assertThat(ports.assignments).isEmpty();
    assertThat(ports.outbox).isEmpty();
    assertThat(ports.audited).isEmpty();
  }

  @Test
  @DisplayName("US-18 AC-4 an EventAdmin removes a member from a team and the teams left are published")
  void anEventAdminRemovesAMemberFromATeam() {
    admin(sofia);
    var security = UUID.randomUUID();
    regular(rita, nursing, security);

    service.remove(sofia, event, rita, nursing);

    assertThat(stored(rita).teamIds()).containsExactly(security);
    assertThat(stored(rita).version()).isEqualTo(2);
    assertThat(ports.assignments).singleElement().satisfies(assignment -> {
      assertThat(assignment.added()).isFalse();
      assertThat(assignment.teamId()).isEqualTo(nursing);
    });
    assertThat(ports.audited).singleElement().satisfies(line -> assertThat(line.added()).isFalse());
    assertThat(ports.outbox).singleElement().satisfies(published -> assertThat(published.payload())
        .containsEntry("teamIds", List.of(security.toString())).containsEntry("version", 2L));
  }

  @Test
  @DisplayName("US-18 AC-4 removing a member who is not in the team changes nothing")
  void removingAMemberWhoIsNotInTheTeamChangesNothing() {
    admin(sofia);
    regular(rita);

    service.remove(sofia, event, rita, nursing);

    assertThat(stored(rita).version()).isEqualTo(1);
    assertThat(ports.assignments).isEmpty();
    assertThat(ports.outbox).isEmpty();
    assertThat(ports.audited).isEmpty();
    // The row is cleared whether or not there is one: the call cannot tell a leftover from a team nobody was in
    assertThat(ports.cleared).containsExactly(List.of(event, rita, nursing));
  }

  @Test
  @DisplayName("US-18 AC-4 a removal needs no team copy, and the teams the member keeps are untouched")
  void aRemovalNeedsNoTeamCopy() {
    admin(sofia);
    var security = UUID.randomUUID();
    // Events deleted nursing, so D4 leaves it out of the member's teams; security is live and must not move
    regular(rita, security);

    service.remove(sofia, event, rita, nursing);

    assertThat(stored(rita).teamIds()).containsExactly(security);
    assertThat(stored(rita).version()).isEqualTo(1);
    assertThat(ports.cleared).containsExactly(List.of(event, rita, nursing));
    assertThat(ports.outbox).isEmpty();
  }

  @Test
  @DisplayName("US-18 AC-2 a user who is not a member of the event cannot be put in a team")
  void aUserWhoIsNotAMemberCannotBePutInATeam() {
    admin(sofia);
    ports.liveTeam(event, nursing);

    assertThatThrownBy(() -> service.add(sofia, event, rita, nursing))
        .isInstanceOf(EventNotFoundException.class);
    assertThat(ports.assignments).isEmpty();
    assertThat(ports.outbox).isEmpty();
    assertThat(ports.refusals).containsExactly(
        new InMemoryPorts.Refused(sofia, event, Refusal.NO_SUCH_MEMBER, NOW));
  }

  @Test
  @DisplayName("US-18 AC-1 only a live team of this event takes members")
  void onlyALiveTeamOfThisEventTakesMembers() {
    admin(sofia);
    regular(rita);
    ports.liveTeam(UUID.randomUUID(), nursing); // the same team id, in another event

    assertThatThrownBy(() -> service.add(sofia, event, rita, nursing))
        .isInstanceOf(EventNotFoundException.class);
    assertThatThrownBy(() -> service.add(sofia, event, rita, UUID.randomUUID())) // unknown or deleted
        .isInstanceOf(EventNotFoundException.class);
    assertThat(ports.assignments).isEmpty();
    assertThat(ports.outbox).isEmpty();
    assertThat(ports.refusals).extracting(InMemoryPorts.Refused::refusal)
        .containsExactly(Refusal.NO_SUCH_TEAM, Refusal.NO_SUCH_TEAM);
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(value = Operation.class)
  @DisplayName("US-18 AC-1 only an EventAdmin of the event may change its teams' members")
  void onlyAnEventAdminMayChangeTeamMembers(Operation operation) {
    regular(sofia); // a member of the event, but not an EventAdmin
    regular(rita, nursing);
    ports.liveTeam(event, nursing);

    assertThatThrownBy(() -> operation.run(service, sofia, event, rita, nursing))
        .isInstanceOf(NotAnEventAdminException.class);
    assertThat(ports.assignments).isEmpty();
    assertThat(ports.outbox).isEmpty();
    assertThat(ports.refusals).containsExactly(
        new InMemoryPorts.Refused(sofia, event, Refusal.NOT_AN_EVENT_ADMIN, NOW));
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(value = Operation.class)
  @DisplayName("US-18 AC-1 a caller who is not a member of the event gets the same not-found, and it is recorded")
  void aCallerOutsideTheEventGetsTheSameNotFound(Operation operation) {
    regular(rita, nursing);
    ports.liveTeam(event, nursing);

    assertThatThrownBy(() -> operation.run(service, sofia, event, rita, nursing))
        .isInstanceOf(EventNotFoundException.class);
    assertThat(ports.assignments).isEmpty();
    assertThat(ports.outbox).isEmpty();
    assertThat(ports.refusals).containsExactly(
        new InMemoryPorts.Refused(sofia, event, Refusal.NOT_A_MEMBER, NOW));
  }

  @Test
  @DisplayName("US-18 AC-1 an EventAdmin of another event changes nothing in this one")
  void anotherEventsAdminChangesNothing() {
    var otherEvent = UUID.randomUUID();
    ports.member(new EventMembership(otherEvent, sofia, EventRole.EventAdmin, List.of(), 1));
    regular(rita);
    ports.liveTeam(event, nursing);

    assertThatThrownBy(() -> service.add(sofia, event, rita, nursing))
        .isInstanceOf(EventNotFoundException.class);
    assertThat(ports.outbox).isEmpty();
  }

  @Test
  @DisplayName("US-18 AC-1 an EventAdmin can be put in a team, and may change their own teams")
  void anEventAdminCanBeInATeam() {
    admin(sofia);
    ports.liveTeam(event, nursing);

    service.add(sofia, event, sofia, nursing);

    assertThat(stored(sofia).teamIds()).containsExactly(nursing);
    assertThat(ports.outbox).singleElement()
        .satisfies(published -> assertThat(published.payload()).containsEntry("role", "EventAdmin"));
  }

  /** The two writes of this story, so each refusal is checked on both without copying the test. */
  enum Operation {
    add {
      @Override
      void run(TeamMemberService service, UUID actor, UUID event, UUID user, UUID team) {
        service.add(actor, event, user, team);
      }
    },
    remove {
      @Override
      void run(TeamMemberService service, UUID actor, UUID event, UUID user, UUID team) {
        service.remove(actor, event, user, team);
      }
    };

    abstract void run(TeamMemberService service, UUID actor, UUID event, UUID user, UUID team);
  }
}
