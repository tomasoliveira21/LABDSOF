package pt.isep.labdsof.authusers.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import pt.isep.labdsof.authusers.IntegrationTest;
import pt.isep.labdsof.authusers.application.EventMemberships;
import pt.isep.labdsof.authusers.domain.EventRole;

/**
 * The memberships and their team assignments through their port. The lock of US-18 D7 can only be shown here: two
 * transactions putting one member in a team at the same time.
 */
class JdbcEventMembershipsIT extends IntegrationTest {

  private static final Instant AT = Instant.parse("2027-04-30T10:15:30Z");

  @Autowired EventMemberships memberships;
  @Autowired TransactionTemplate transactions;

  private final UUID event = UUID.randomUUID();

  private UUID member(String displayName, EventRole role) {
    var userId = UUID.randomUUID();
    seedAccount(userId, displayName);
    seedMembership(event, userId, role.name());
    return userId;
  }

  /**
   * A live team of this event in the copy. The aggregate reads the copy (D4), so a test that wants a team in a
   * member's teams has to have Events' {@code TeamChanged} for it, exactly as the use case needs one to assign.
   */
  private UUID team() {
    var teamId = UUID.randomUUID();
    seedTeam(event, teamId, false);
    return teamId;
  }

  private List<UUID> teamsOf(UUID userId) {
    return jdbc.sql("""
        SELECT team_id FROM team_assignment WHERE event_id = :event AND user_id = :user ORDER BY team_id
        """).param("event", event).param("user", userId).query(UUID.class).list();
  }

  private long versionOf(UUID userId) {
    return jdbc.sql("SELECT version FROM event_membership WHERE event_id = :event AND user_id = :user")
        .param("event", event).param("user", userId).query(Long.class).single();
  }

  /** One assignment in its own transaction, taking the lock first, as the use case does. */
  private Callable<UUID> assignment(UUID userId, UUID teamId, UUID actor) {
    return () -> transactions.execute(status -> {
      var locked = memberships.lockForUpdate(event, userId).orElseThrow();
      memberships.addToTeam(locked.withTeam(teamId), teamId, actor, AT);
      return teamId;
    });
  }

  @Test
  @DisplayName("US-18 AC-1 a membership is read back with its role, its version and its teams")
  void aMembershipIsReadBackWhole() {
    var rita = member("Rita Participante", EventRole.RegularUser);
    var nursing = team();
    var actor = UUID.randomUUID();

    transactions.executeWithoutResult(status -> {
      var membership = memberships.find(event, rita).orElseThrow();
      memberships.addToTeam(membership.withTeam(nursing), nursing, actor, AT);
    });

    var stored = memberships.find(event, rita).orElseThrow();
    assertThat(stored.eventId()).isEqualTo(event);
    assertThat(stored.userId()).isEqualTo(rita);
    assertThat(stored.role()).isEqualTo(EventRole.RegularUser);
    assertThat(stored.teamIds()).containsExactly(nursing);
    assertThat(stored.version()).isEqualTo(2);
    assertThat(jdbc.sql("""
        SELECT assigned_by FROM team_assignment WHERE event_id = :event AND user_id = :user AND team_id = :team
        """).param("event", event).param("user", rita).param("team", nursing).query(UUID.class).single())
        .isEqualTo(actor);
    assertThat(jdbc.sql("SELECT updated_by FROM event_membership WHERE event_id = :event AND user_id = :user")
        .param("event", event).param("user", rita).query(UUID.class).single()).isEqualTo(actor);
  }

  @Test
  @DisplayName("US-18 AC-2 a user who is not a member of the event has no membership to lock")
  void aUserWhoIsNotAMemberHasNothingToLock() {
    assertThat(memberships.find(event, UUID.randomUUID())).isEmpty();
    assertThat(memberships.lockForUpdate(event, UUID.randomUUID())).isEmpty();
    assertThat(memberships.lockForUpdate(UUID.randomUUID(), member("Rita Outra", EventRole.RegularUser))).isEmpty();
  }

  @Test
  @DisplayName("US-18 AC-3 two assignments of one member at once get consecutive versions")
  void twoAssignmentsOfOneMemberGetConsecutiveVersions() throws Exception {
    var rita = member("Rita Participante", EventRole.RegularUser);
    var nursing = team();
    var security = team();
    var actor = UUID.randomUUID();

    // Both transactions raise the version at the same time: without the FOR UPDATE of D7 they would both read 1,
    // both write 2, and one of the two MembershipChanged would be dropped by every consumer as a replay
    try (var executor = Executors.newFixedThreadPool(2)) {
      for (var assignment : executor.invokeAll(List.of(assignment(rita, nursing, actor),
          assignment(rita, security, actor)))) {
        assertThat(assignment.get(30, TimeUnit.SECONDS)).isNotNull();
      }
    }

    assertThat(teamsOf(rita)).containsExactlyInAnyOrder(nursing, security);
    assertThat(versionOf(rita)).isEqualTo(3);
  }

  @Test
  @DisplayName("US-18 AC-3 assignments of different members do not block each other")
  void assignmentsOfDifferentMembersDoNotBlockEachOther() throws Exception {
    var rita = member("Rita Participante", EventRole.RegularUser);
    var pedro = member("Pedro Voluntario", EventRole.RegularUser);
    var nursing = team();
    var actor = UUID.randomUUID();

    try (var executor = Executors.newFixedThreadPool(2)) {
      for (var assignment : executor.invokeAll(List.of(assignment(rita, nursing, actor),
          assignment(pedro, nursing, actor)))) {
        assertThat(assignment.get(30, TimeUnit.SECONDS)).isNotNull();
      }
    }

    assertThat(versionOf(rita)).isEqualTo(2);
    assertThat(versionOf(pedro)).isEqualTo(2);
  }

  @Test
  @DisplayName("US-18 AC-4 an assignment is removed and the membership's version follows")
  void anAssignmentIsRemoved() {
    var rita = member("Rita Participante", EventRole.RegularUser);
    var nursing = team();
    var actor = UUID.randomUUID();
    transactions.executeWithoutResult(status -> {
      var membership = memberships.find(event, rita).orElseThrow();
      memberships.addToTeam(membership.withTeam(nursing), nursing, actor, AT);
    });

    transactions.executeWithoutResult(status -> {
      var membership = memberships.find(event, rita).orElseThrow();
      memberships.removeFromTeam(membership.withoutTeam(nursing), nursing, actor, AT);
    });

    assertThat(teamsOf(rita)).isEmpty();
    assertThat(versionOf(rita)).isEqualTo(3);
  }

  @Test
  @DisplayName("US-18 AC-4 a team Events deletes after the assignment leaves the member's teams and the list")
  void aTeamDeletedAfterTheAssignmentLeavesTheMembersTeams() {
    var rita = member("Rita Participante", EventRole.RegularUser);
    var nursing = team();
    var security = team();
    var actor = UUID.randomUUID();
    transactions.executeWithoutResult(status -> {
      var membership = memberships.find(event, rita).orElseThrow();
      memberships.addToTeam(membership.withTeam(nursing), nursing, actor, AT);
    });

    // Events deletes the team: the TeamChanged consumer only flips `deleted` in the copy, the assignment row stays
    seedTeam(event, nursing, true);

    assertThat(teamsOf(rita)).containsExactly(nursing); // the row is still there
    assertThat(memberships.find(event, rita).orElseThrow().teamIds()).isEmpty();
    assertThat(memberships.list(event, null, null)).singleElement()
        .satisfies(member -> assertThat(member.teamIds()).isEmpty());
    assertThat(memberships.list(event, null, nursing)).isEmpty(); // nor does the filter match its old members
    // What the next MembershipChanged for this member would carry: the live team only, never the deleted one
    transactions.executeWithoutResult(status -> {
      var membership = memberships.lockForUpdate(event, rita).orElseThrow();
      memberships.addToTeam(membership.withTeam(security), security, actor, AT);
    });
    assertThat(memberships.find(event, rita).orElseThrow().teamIds()).containsExactly(security);
    assertThat(versionOf(rita)).isEqualTo(3);

    // The DELETE of the assignment clears the row the deleted team left behind, without a version
    transactions.executeWithoutResult(status -> memberships.clearAssignment(event, rita, nursing));
    assertThat(teamsOf(rita)).containsExactly(security);
    assertThat(versionOf(rita)).isEqualTo(3);
  }

  @Test
  @DisplayName("US-18 AC-1 the list holds this event's members only, with their teams and display names")
  void theListHoldsThisEventsMembersOnly() {
    var sofia = member("Sofia Admin", EventRole.EventAdmin);
    var rita = member("Rita Participante", EventRole.RegularUser);
    var nursing = team();
    var elsewhere = UUID.randomUUID();
    seedAccount(elsewhere, "Admin Elsewhere");
    seedMembership(UUID.randomUUID(), elsewhere, "EventAdmin");
    transactions.executeWithoutResult(status -> {
      var membership = memberships.find(event, rita).orElseThrow();
      memberships.addToTeam(membership.withTeam(nursing), nursing, sofia, AT);
    });

    var all = memberships.list(event, null, null);

    assertThat(all).extracting(EventMemberships.Member::userId).containsExactly(rita, sofia);
    assertThat(all.get(0).displayName()).isEqualTo("Rita Participante");
    assertThat(all.get(0).teamIds()).containsExactly(nursing);
    assertThat(memberships.list(event, EventRole.EventAdmin, null))
        .extracting(EventMemberships.Member::userId).containsExactly(sofia);
    assertThat(memberships.list(event, null, nursing))
        .extracting(EventMemberships.Member::userId).containsExactly(rita);
    assertThat(memberships.list(event, EventRole.EventAdmin, nursing)).isEmpty();
    assertThat(memberships.list(UUID.randomUUID(), null, null)).isEmpty();
  }
}
