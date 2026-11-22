package pt.isep.labdsof.transports.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class TeamTest {

  private static final UUID TEAM = UUID.randomUUID();
  private static final UUID EVENT = UUID.randomUUID();

  @Test
  @DisplayName("193 AC-5 a team grants the permissions it holds")
  void aTeamGrantsWhatItHolds() {
    var team = new Team(TEAM, EVENT, Set.of(Permission.TRANSPORT_EDIT), false, 1);
    assertThat(team.grants(Permission.TRANSPORT_EDIT)).isTrue();
    assertThat(team.grants(Permission.MAP_EDIT)).isFalse();
  }

  @Test
  @DisplayName("193 AC-5 a deleted team grants nothing")
  void aDeletedTeamGrantsNothing() {
    var team = new Team(TEAM, EVENT, Set.of(Permission.TRANSPORT_EDIT), true, 2);
    assertThat(team.deleted()).isTrue();
    assertThat(team.grants(Permission.TRANSPORT_EDIT)).isFalse();
  }

  @Test
  @DisplayName("193 AC-5 a team needs its id and event and a version of at least 1")
  void aTeamNeedsItsFields() {
    var team = new Team(TEAM, EVENT, Set.of(), false, 1);
    assertThat(team.teamId()).isEqualTo(TEAM);
    assertThat(team.eventId()).isEqualTo(EVENT);
    assertThat(team.version()).isEqualTo(1);

    assertThatThrownBy(() -> new Team(null, EVENT, Set.of(), false, 1))
        .isInstanceOf(NullPointerException.class).hasMessage("teamId");
    assertThatThrownBy(() -> new Team(TEAM, null, Set.of(), false, 1))
        .isInstanceOf(NullPointerException.class).hasMessage("eventId");
    assertThatThrownBy(() -> new Team(TEAM, EVENT, null, false, 1))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new Team(TEAM, EVENT, Set.of(), false, 0))
        .isInstanceOf(IllegalArgumentException.class).hasMessage("version must be at least 1");
  }

  @Test
  @DisplayName("193 AC-5 the permissions are copied and a null permission is refused")
  void permissionsAreCopied() {
    var permissions = new HashSet<>(Set.of(Permission.MAP_EDIT));
    var team = new Team(TEAM, EVENT, permissions, false, 1);
    permissions.add(Permission.TRANSPORT_EDIT);
    assertThat(team.permissions()).containsExactly(Permission.MAP_EDIT);

    var withNull = new HashSet<Permission>();
    withNull.add(null);
    assertThatThrownBy(() -> new Team(TEAM, EVENT, withNull, false, 1)).isInstanceOf(NullPointerException.class);
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(Permission.class)
  @DisplayName("193 AC-5 every permission is read from and written as its features 4.2 name")
  void permissionWireNamesRoundTrip(Permission permission) {
    assertThat(Permission.fromWireName(permission.wireName())).isEqualTo(permission);
  }

  @Test
  @DisplayName("193 AC-5 the permission names are the fixed list of features 4.2, and another name is refused")
  void permissionNamesAreTheFixedList() {
    assertThat(Arrays.stream(Permission.values()).map(Permission::wireName)).containsExactly(
        "HelpRequests.Triage", "HelpRequests.Respond", "Reports.Review", "Reports.Respond",
        "Announcements.Publish", "Map.Edit", "Transport.Edit", "Entrances.Update");
    assertThatThrownBy(() -> Permission.fromWireName("Bus.Drive"))
        .isInstanceOf(IllegalArgumentException.class).hasMessage("unknown permission: Bus.Drive");
    assertThatThrownBy(() -> Permission.fromWireName("transport.edit"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
