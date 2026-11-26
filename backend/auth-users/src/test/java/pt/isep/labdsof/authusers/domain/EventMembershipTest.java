package pt.isep.labdsof.authusers.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** US-18: the membership whose teams and version every change publishes (spec D6). */
class EventMembershipTest {

  private static final UUID EVENT = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID USER = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID NURSING = UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final UUID SECURITY = UUID.fromString("44444444-4444-4444-4444-444444444444");

  private static EventMembership member(UUID... teams) {
    return new EventMembership(EVENT, USER, EventRole.RegularUser, List.of(teams), 1);
  }

  @Test
  @DisplayName("US-18 AC-1 a team is added with the next version")
  void aTeamIsAddedWithTheNextVersion() {
    var updated = member().withTeam(NURSING);

    assertThat(updated.teamIds()).containsExactly(NURSING);
    assertThat(updated.version()).isEqualTo(2);
    assertThat(updated.isIn(NURSING)).isTrue();
    assertThat(updated.role()).isEqualTo(EventRole.RegularUser);
  }

  @Test
  @DisplayName("US-18 AC-3 a member in two teams carries both, sorted")
  void aMemberInTwoTeamsCarriesBoth() {
    var updated = member(SECURITY).withTeam(NURSING);

    assertThat(updated.teamIds()).containsExactly(NURSING, SECURITY); // by id, not by insertion
    assertThat(updated.version()).isEqualTo(2);
  }

  @Test
  @DisplayName("US-18 AC-1 adding a team the member is already in changes nothing, version included")
  void addingATeamTwiceChangesNothing() {
    var membership = member(NURSING);

    assertThat(membership.withTeam(NURSING)).isSameAs(membership);
  }

  @Test
  @DisplayName("US-18 AC-4 a team is removed with the next version, and the others stay")
  void aTeamIsRemovedWithTheNextVersion() {
    var updated = member(NURSING, SECURITY).withoutTeam(NURSING);

    assertThat(updated.teamIds()).containsExactly(SECURITY);
    assertThat(updated.version()).isEqualTo(2);
    assertThat(updated.isIn(NURSING)).isFalse();
  }

  @Test
  @DisplayName("US-18 AC-4 removing a team the member is not in changes nothing, version included")
  void removingATeamThatIsNotThereChangesNothing() {
    var membership = member(SECURITY);

    assertThat(membership.withoutTeam(NURSING)).isSameAs(membership);
    var inNoTeam = member();
    assertThat(inNoTeam.withoutTeam(NURSING)).isSameAs(inNoTeam);
  }

  @Test
  @DisplayName("US-18 AC-1 an EventAdmin is recognised, a RegularUser is not")
  void theRoleIsReadFromTheMembership() {
    assertThat(new EventMembership(EVENT, USER, EventRole.EventAdmin, List.of(), 1).isEventAdmin()).isTrue();
    assertThat(member().isEventAdmin()).isFalse();
  }

  @Test
  @DisplayName("US-18 AC-1 the teams are deduplicated and the list is a copy of what was given")
  void theTeamsAreDeduplicatedAndCopied() {
    var teams = new ArrayList<>(List.of(NURSING, NURSING, SECURITY));

    var membership = new EventMembership(EVENT, USER, EventRole.RegularUser, teams, 3);
    teams.clear();

    assertThat(membership.teamIds()).containsExactly(NURSING, SECURITY);
    assertThat(membership.version()).isEqualTo(3);
  }

  @Test
  @DisplayName("US-18 AC-1 a membership without its parts, or before version 1, is not a membership")
  void aMembershipNeedsItsParts() {
    assertThatThrownBy(() -> new EventMembership(null, USER, EventRole.RegularUser, List.of(), 1))
        .isInstanceOf(NullPointerException.class).hasMessage("eventId");
    assertThatThrownBy(() -> new EventMembership(EVENT, null, EventRole.RegularUser, List.of(), 1))
        .isInstanceOf(NullPointerException.class).hasMessage("userId");
    assertThatThrownBy(() -> new EventMembership(EVENT, USER, null, List.of(), 1))
        .isInstanceOf(NullPointerException.class).hasMessage("role");
    assertThatThrownBy(() -> new EventMembership(EVENT, USER, EventRole.RegularUser, null, 1))
        .isInstanceOf(NullPointerException.class).hasMessage("teamIds");
    assertThatThrownBy(() -> new EventMembership(EVENT, USER, EventRole.RegularUser, List.of(), 0))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("version starts at 1");
    assertThatThrownBy(() -> member().withTeam(null))
        .isInstanceOf(NullPointerException.class).hasMessage("teamId");
  }
}
