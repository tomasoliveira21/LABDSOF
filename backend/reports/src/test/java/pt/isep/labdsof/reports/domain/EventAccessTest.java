package pt.isep.labdsof.reports.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pt.isep.labdsof.reports.domain.Membership.Role;
import pt.isep.labdsof.reports.domain.Membership.Status;
import pt.isep.labdsof.reports.domain.Team.TeamPermission;

class EventAccessTest {

  static final UUID EVENT = UUID.randomUUID();
  static final UUID CONTROL_ROOM = UUID.randomUUID();

  static Membership member(Role role, Status status, UUID... teams) {
    return new Membership(EVENT, "user-1", role, status, Set.of(teams), 1);
  }

  static Team team(UUID id, UUID event, boolean deleted, Permission... permissions) {
    return new Team(id, event, "Control room",
        java.util.Arrays.stream(permissions).map(p -> new TeamPermission(p, Set.of())).toList(),
        Set.of(Category.Health), deleted, 1);
  }

  @Test
  @DisplayName("194 AC-4 no membership copy or a removed membership gives no access")
  void noAccess() {
    assertThat(EventAccess.of(null, List.of())).isEmpty();
    assertThat(EventAccess.of(member(Role.EventAdmin, Status.Removed), List.of())).isEmpty();
  }

  @Test
  @DisplayName("194 AC-4 an active member without teams is a member with no permission")
  void plainMember() {
    var access = EventAccess.of(member(Role.RegularUser, Status.Active), List.of()).orElseThrow();
    assertThat(access.has(Permission.HELP_REQUESTS_TRIAGE)).isFalse();
  }

  @Test
  @DisplayName("194 AC-4 an EventAdmin has every permission of the event")
  void eventAdmin() {
    var access = EventAccess.of(member(Role.EventAdmin, Status.Active), List.of()).orElseThrow();
    assertThat(access.has(Permission.HELP_REQUESTS_TRIAGE)).isTrue();
  }

  @Test
  @DisplayName("194 AC-4 a RegularUser gets the permissions of their own teams of the event only")
  void teamPermissions() {
    var other = UUID.randomUUID();
    var teams = List.of(team(CONTROL_ROOM, EVENT, false, Permission.HELP_REQUESTS_TRIAGE),
        team(other, EVENT, false, Permission.HELP_REQUESTS_RESPOND));
    var inControlRoom = EventAccess.of(member(Role.RegularUser, Status.Active, CONTROL_ROOM), teams)
        .orElseThrow();
    assertThat(inControlRoom.has(Permission.HELP_REQUESTS_TRIAGE)).isTrue();
    assertThat(inControlRoom.has(Permission.HELP_REQUESTS_RESPOND)).isFalse();

    var notInIt = EventAccess.of(member(Role.RegularUser, Status.Active, other), teams).orElseThrow();
    assertThat(notInIt.has(Permission.HELP_REQUESTS_TRIAGE)).isFalse();
  }

  @Test
  @DisplayName("194 AC-4 a team of another event or a deleted team grants nothing")
  void otherEventOrDeletedTeam() {
    var foreign = List.of(team(CONTROL_ROOM, UUID.randomUUID(), false, Permission.HELP_REQUESTS_TRIAGE));
    assertThat(EventAccess.of(member(Role.RegularUser, Status.Active, CONTROL_ROOM), foreign)
        .orElseThrow().has(Permission.HELP_REQUESTS_TRIAGE)).isFalse();

    var deleted = List.of(team(CONTROL_ROOM, EVENT, true, Permission.HELP_REQUESTS_TRIAGE));
    assertThat(EventAccess.of(member(Role.RegularUser, Status.Active, CONTROL_ROOM), deleted)
        .orElseThrow().has(Permission.HELP_REQUESTS_TRIAGE)).isFalse();
  }

  @Test
  @DisplayName("194 AC-3 permissions use the wire names of features.md §4.2")
  void wireNames() {
    assertThat(Permission.fromWireName("HelpRequests.Triage")).isEqualTo(Permission.HELP_REQUESTS_TRIAGE);
    assertThat(Permission.ENTRANCES_UPDATE.wireName()).isEqualTo("Entrances.Update");
    assertThat(Permission.values()).extracting(Permission::wireName).containsExactly(
        "HelpRequests.Triage", "HelpRequests.Respond", "Reports.Review", "Reports.Respond",
        "Announcements.Publish", "Map.Edit", "Transport.Edit", "Entrances.Update");
    assertThatThrownBy(() -> Permission.fromWireName("Everything"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("194 AC-3 copies need an id, a name and a version of at least 1")
  void copyInvariants() {
    assertThat(new Membership(EVENT, "u", Role.RegularUser, Status.Active, Set.of(), 1).version())
        .isEqualTo(1);
    assertThatThrownBy(() -> new Membership(EVENT, "u", Role.RegularUser, Status.Active, Set.of(), 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Membership(EVENT, " ", Role.RegularUser, Status.Active, Set.of(), 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Membership(EVENT, null, Role.RegularUser, Status.Active, Set.of(), 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(new Team(CONTROL_ROOM, EVENT, "T", List.of(), Set.of(), false, 1).version()).isEqualTo(1);
    assertThatThrownBy(() -> new Team(CONTROL_ROOM, EVENT, "T", List.of(), Set.of(), false, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Team(CONTROL_ROOM, EVENT, " ", List.of(), Set.of(), false, 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Team(CONTROL_ROOM, EVENT, null, List.of(), Set.of(), false, 1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("US-31 AC-2 only the member's own responding teams are listed")
  void respondingTeams() {
    var nursing = UUID.randomUUID();
    var security = UUID.randomUUID();
    var gone = UUID.randomUUID();
    var elsewhere = UUID.randomUUID();
    var notMine = UUID.randomUUID();
    var teams = List.of(team(CONTROL_ROOM, EVENT, false, Permission.HELP_REQUESTS_TRIAGE),
        team(nursing, EVENT, false, Permission.HELP_REQUESTS_RESPOND),
        team(security, EVENT, false, Permission.HELP_REQUESTS_TRIAGE, Permission.HELP_REQUESTS_RESPOND),
        team(gone, EVENT, true, Permission.HELP_REQUESTS_RESPOND),
        team(elsewhere, UUID.randomUUID(), false, Permission.HELP_REQUESTS_RESPOND),
        team(notMine, EVENT, false, Permission.HELP_REQUESTS_RESPOND));
    var access = EventAccess.of(member(Role.RegularUser, Status.Active, CONTROL_ROOM, nursing, security, gone,
        elsewhere), teams).orElseThrow();
    assertThat(access.respondingTeamIds()).containsExactlyInAnyOrder(nursing, security);

    var admin = EventAccess.of(member(Role.EventAdmin, Status.Active), teams).orElseThrow();
    assertThat(admin.has(Permission.HELP_REQUESTS_RESPOND)).isTrue();
    assertThat(admin.respondingTeamIds()).isEmpty();
  }
}
