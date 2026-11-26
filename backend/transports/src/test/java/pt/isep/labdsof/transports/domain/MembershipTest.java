package pt.isep.labdsof.transports.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pt.isep.labdsof.transports.domain.Membership.Role;
import pt.isep.labdsof.transports.domain.Membership.Status;

class MembershipTest {

  private static final UUID EVENT = UUID.randomUUID();
  private static final UUID USER = UUID.randomUUID();
  private static final Set<UUID> TEAMS = Set.of(UUID.randomUUID());

  @Test
  @DisplayName("193 AC-5 a membership needs its event, user, role and status and a version of at least 1")
  void aMembershipNeedsItsFields() {
    var membership = new Membership(EVENT, USER, Role.EventAdmin, Status.Active, TEAMS, 1);
    assertThat(membership.eventId()).isEqualTo(EVENT);
    assertThat(membership.userId()).isEqualTo(USER);
    assertThat(membership.role()).isEqualTo(Role.EventAdmin);
    assertThat(membership.version()).isEqualTo(1);

    assertThatThrownBy(() -> new Membership(null, USER, Role.EventAdmin, Status.Active, TEAMS, 1))
        .isInstanceOf(NullPointerException.class).hasMessage("eventId");
    assertThatThrownBy(() -> new Membership(EVENT, null, Role.EventAdmin, Status.Active, TEAMS, 1))
        .isInstanceOf(NullPointerException.class).hasMessage("userId");
    assertThatThrownBy(() -> new Membership(EVENT, USER, null, Status.Active, TEAMS, 1))
        .isInstanceOf(NullPointerException.class).hasMessage("role");
    assertThatThrownBy(() -> new Membership(EVENT, USER, Role.EventAdmin, null, TEAMS, 1))
        .isInstanceOf(NullPointerException.class).hasMessage("status");
    assertThatThrownBy(() -> new Membership(EVENT, USER, Role.EventAdmin, Status.Active, TEAMS, 0))
        .isInstanceOf(IllegalArgumentException.class).hasMessage("version must be at least 1");
  }

  @Test
  @DisplayName("193 AC-5 a removed membership is kept but is not active")
  void aRemovedMembershipIsNotActive() {
    assertThat(new Membership(EVENT, USER, Role.RegularUser, Status.Active, TEAMS, 1).isActive()).isTrue();
    assertThat(new Membership(EVENT, USER, Role.RegularUser, Status.Removed, TEAMS, 1).isActive()).isFalse();
  }

  @Test
  @DisplayName("193 AC-5 the team ids are copied and a null team id is refused")
  void teamIdsAreCopied() {
    var teams = new HashSet<UUID>(TEAMS);
    var membership = new Membership(EVENT, USER, Role.RegularUser, Status.Active, teams, 1);
    teams.add(UUID.randomUUID());
    assertThat(membership.teamIds()).isEqualTo(TEAMS);

    var withNull = new HashSet<UUID>();
    withNull.add(null);
    assertThatThrownBy(() -> new Membership(EVENT, USER, Role.RegularUser, Status.Active, withNull, 1))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new Membership(EVENT, USER, Role.RegularUser, Status.Active, null, 1))
        .isInstanceOf(NullPointerException.class);
  }
}
