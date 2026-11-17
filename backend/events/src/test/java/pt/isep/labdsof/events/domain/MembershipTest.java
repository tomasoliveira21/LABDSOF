package pt.isep.labdsof.events.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pt.isep.labdsof.events.domain.Membership.Role;
import pt.isep.labdsof.events.domain.Membership.Status;

class MembershipTest {

  private static final UUID EVENT = UUID.randomUUID();
  private static final UUID USER = UUID.randomUUID();

  private static Membership membership(Role role, Status status) {
    return new Membership(EVENT, USER, role, status, Set.of(), 1);
  }

  @ParameterizedTest(name = "{0} sees {1}: {2}")
  @CsvSource({
      "EventAdmin,Draft,true", "EventAdmin,Published,true", "EventAdmin,Suspended,true", "EventAdmin,Closed,true",
      "RegularUser,Draft,false", "RegularUser,Published,true", "RegularUser,Suspended,false",
      "RegularUser,Closed,true"})
  @DisplayName("192 AC-5 an EventAdmin sees the event in every status, a RegularUser only when Published or Closed")
  void visibilityMatrix(Role role, EventStatus status, boolean sees) {
    assertThat(membership(role, Status.Active).canSee(status)).isEqualTo(sees);
  }

  @Test
  @DisplayName("192 AC-5 only an Active membership counts, a Removed one is kept but gives no access")
  void onlyActiveCounts() {
    assertThat(membership(Role.RegularUser, Status.Active).isActive()).isTrue();
    assertThat(membership(Role.RegularUser, Status.Removed).isActive()).isFalse();
  }

  @Test
  @DisplayName("192 AC-6 a membership needs event, user, role, status, team ids and a version of at least 1")
  void aMembershipNeedsItsFields() {
    var teams = Set.of(UUID.randomUUID());
    var withNullElement = new HashSet<UUID>();
    withNullElement.add(null);

    assertThat(new Membership(EVENT, USER, Role.EventAdmin, Status.Active, teams, 1).version()).isEqualTo(1);
    assertThatThrownBy(() -> new Membership(null, USER, Role.EventAdmin, Status.Active, teams, 1))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new Membership(EVENT, null, Role.EventAdmin, Status.Active, teams, 1))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new Membership(EVENT, USER, null, Status.Active, teams, 1))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new Membership(EVENT, USER, Role.EventAdmin, null, teams, 1))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new Membership(EVENT, USER, Role.EventAdmin, Status.Active, null, 1))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new Membership(EVENT, USER, Role.EventAdmin, Status.Active, withNullElement, 1))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new Membership(EVENT, USER, Role.EventAdmin, Status.Active, teams, 0))
        .isInstanceOf(IllegalArgumentException.class).hasMessage("version must be at least 1");
  }
}
