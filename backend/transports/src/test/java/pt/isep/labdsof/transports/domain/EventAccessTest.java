package pt.isep.labdsof.transports.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import pt.isep.labdsof.transports.domain.EventAccess.Edit;
import pt.isep.labdsof.transports.domain.Membership.Role;
import pt.isep.labdsof.transports.domain.Membership.Status;

class EventAccessTest {

  private static final UUID EVENT = UUID.fromString("0b6f3c1e-5a3b-4f0e-9d0e-6a1b2c3d4e5f");
  private static final UUID USER = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");
  private static final UUID TEAM = UUID.fromString("16fd2706-8baf-433b-82eb-8c7fada847da");

  private static Optional<Membership> member(Role role, Status status, UUID... teams) {
    return Optional.of(new Membership(EVENT, USER, role, status, Set.of(teams), 1));
  }

  private static Optional<EventCopy> event(EventStatus status) {
    return Optional.of(new EventCopy(EVENT, status, 1));
  }

  private static Team team(UUID eventId, boolean deleted, Permission... permissions) {
    return new Team(TEAM, eventId, Set.of(permissions), deleted, 1);
  }

  private static Edit edit(Optional<Membership> membership, Optional<EventCopy> event, Collection<Team> teams) {
    return EventAccess.canEditTransport(membership, event, teams);
  }

  @Test
  @DisplayName("US-41 D5 a RegularUser reads a published or closed event, not a draft")
  void regularUserReads() {
    var member = member(Role.RegularUser, Status.Active);

    assertThat(EventAccess.canRead(member, event(EventStatus.Published))).isTrue();
    assertThat(EventAccess.canRead(member, event(EventStatus.Closed))).isTrue();
    assertThat(EventAccess.canRead(member, event(EventStatus.Draft))).isFalse();
  }

  @Test
  @DisplayName("US-41 D5 an EventAdmin reads a draft, published or closed event")
  void eventAdminReads() {
    var admin = member(Role.EventAdmin, Status.Active);

    assertThat(EventAccess.canRead(admin, event(EventStatus.Draft))).isTrue();
    assertThat(EventAccess.canRead(admin, event(EventStatus.Published))).isTrue();
    assertThat(EventAccess.canRead(admin, event(EventStatus.Closed))).isTrue();
  }

  @ParameterizedTest
  @EnumSource(Role.class)
  @DisplayName("US-41 AC-3 nobody reads a suspended event")
  void suspendedReadsNothing(Role role) {
    assertThat(EventAccess.canRead(member(role, Status.Active), event(EventStatus.Suspended))).isFalse();
  }

  @Test
  @DisplayName("US-41 AC-3 no membership, a removed one or no event copy reads nothing")
  void outsidersReadNothing() {
    assertThat(EventAccess.canRead(Optional.empty(), event(EventStatus.Published))).isFalse();
    assertThat(EventAccess.canRead(member(Role.EventAdmin, Status.Removed), event(EventStatus.Published))).isFalse();
    assertThat(EventAccess.canRead(member(Role.EventAdmin, Status.Active), Optional.empty())).isFalse();
  }

  @ParameterizedTest
  @EnumSource(value = EventStatus.class, names = {"Draft", "Published", "Closed"})
  @DisplayName("US-41 D3 an EventAdmin edits transport in a draft, published or closed event (closed is 409 later)")
  void eventAdminEdits(EventStatus status) {
    assertThat(edit(member(Role.EventAdmin, Status.Active), event(status), List.of())).isEqualTo(Edit.ALLOWED);
  }

  @Test
  @DisplayName("US-41 D3 a member of a team with Transport.Edit edits, one without it does not")
  void teamPermission() {
    var member = member(Role.RegularUser, Status.Active, TEAM);
    var published = event(EventStatus.Published);

    assertThat(edit(member, published, List.of(team(EVENT, false, Permission.TRANSPORT_EDIT))))
        .isEqualTo(Edit.ALLOWED);
    assertThat(edit(member, published, List.of(team(EVENT, false, Permission.MAP_EDIT))))
        .isEqualTo(Edit.FORBIDDEN);
    assertThat(edit(member, published, List.of())).isEqualTo(Edit.FORBIDDEN);
  }

  @Test
  @DisplayName("US-41 D3 a deleted team, or a team of another event, grants nothing")
  void deletedOrOtherEventTeam() {
    var member = member(Role.RegularUser, Status.Active, TEAM);
    var published = event(EventStatus.Published);

    assertThat(edit(member, published, List.of(team(EVENT, true, Permission.TRANSPORT_EDIT))))
        .isEqualTo(Edit.FORBIDDEN);
    assertThat(edit(member, published, List.of(team(UUID.randomUUID(), false, Permission.TRANSPORT_EDIT))))
        .isEqualTo(Edit.FORBIDDEN);
  }

  @Test
  @DisplayName("US-41 D3 a team the member is not in grants nothing")
  void teamOfSomeoneElse() {
    var member = member(Role.RegularUser, Status.Active);

    assertThat(edit(member, event(EventStatus.Published), List.of(team(EVENT, false, Permission.TRANSPORT_EDIT))))
        .isEqualTo(Edit.FORBIDDEN);
  }

  @Test
  @DisplayName("US-41 AC-3 a membership of another event opens nothing")
  void membershipOfAnotherEvent() {
    var elsewhere = Optional.of(new Membership(UUID.randomUUID(), USER, Role.EventAdmin, Status.Active, Set.of(), 1));

    assertThat(EventAccess.canRead(elsewhere, event(EventStatus.Published))).isFalse();
    assertThat(edit(elsewhere, event(EventStatus.Published), List.of())).isEqualTo(Edit.NOT_FOUND);
  }

  @Test
  @DisplayName("US-41 AC-3 no membership, a removed one, no event copy or a suspended event is NOT_FOUND")
  void outsidersEditNothing() {
    var withPermission = List.of(team(EVENT, false, Permission.TRANSPORT_EDIT));

    assertThat(edit(Optional.empty(), event(EventStatus.Published), withPermission)).isEqualTo(Edit.NOT_FOUND);
    assertThat(edit(member(Role.EventAdmin, Status.Removed), event(EventStatus.Published), withPermission))
        .isEqualTo(Edit.NOT_FOUND);
    assertThat(edit(member(Role.EventAdmin, Status.Active), Optional.empty(), withPermission))
        .isEqualTo(Edit.NOT_FOUND);
    assertThat(edit(member(Role.EventAdmin, Status.Active), event(EventStatus.Suspended), withPermission))
        .isEqualTo(Edit.NOT_FOUND);
  }
}
