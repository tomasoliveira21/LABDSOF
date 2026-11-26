package pt.isep.labdsof.events.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TeamTest {

  private static final UUID TEAM = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID EVENT = UUID.fromString("22222222-2222-2222-2222-222222222222");

  private static Team team(String name) {
    return Team.created(TEAM, EVENT, name);
  }

  @Test
  @DisplayName("US-16 AC-1 a created team has no permissions, no categories and version 1")
  void createdTeamIsEmpty() {
    var team = team("Nursing");

    assertThat(team.teamId()).isEqualTo(TEAM);
    assertThat(team.eventId()).isEqualTo(EVENT);
    assertThat(team.name()).isEqualTo("Nursing");
    assertThat(team.permissions()).isEmpty();
    assertThat(team.handledCategories()).isEmpty();
    assertThat(team.deleted()).isFalse();
    assertThat(team.version()).isEqualTo(1);
  }

  @ParameterizedTest(name = "[{index}] \"{0}\"")
  @ValueSource(strings = {"", " ", "   ", "\t", "\n"})
  @DisplayName("US-16 AC-1 a name outside its bounds is refused")
  void blankNameIsRefused(String name) {
    assertThatThrownBy(() -> team(name)).isInstanceOf(InvalidTeamException.class);
  }

  @Test
  @DisplayName("US-16 AC-1 a name outside its bounds is refused: 1 and 100 characters pass, 101 does not")
  void nameLengthBounds() {
    assertThat(team("a").name()).isEqualTo("a");
    assertThat(team("a".repeat(100)).name()).hasSize(100);
    assertThatThrownBy(() -> team("a".repeat(101))).isInstanceOf(InvalidTeamException.class);
    // 100 characters with blanks around them still fit, because the name is trimmed first
    assertThat(team("  " + "a".repeat(100) + "  ").name()).hasSize(100);
  }

  @Test
  @DisplayName("US-16 AC-1 a name is stored trimmed")
  void nameIsTrimmed() {
    assertThat(team("  Control room  ").name()).isEqualTo("Control room");
  }

  @Test
  @DisplayName("US-16 AC-1 a null name, event, team or version is a bug, not a bad request")
  void nullsAndVersionAreRejected() {
    assertThatThrownBy(() -> team(null)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> Team.created(null, EVENT, "T")).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> Team.created(TEAM, null, "T")).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new Team(TEAM, EVENT, "T", null, Set.of(), false, 1))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new Team(TEAM, EVENT, "T", List.of(), null, false, 1))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new Team(TEAM, EVENT, "T", List.of(), Set.of(), false, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("US-16 AC-2 a name is compared ignoring case and blanks around it")
  void namesAreComparedIgnoringCaseAndBlanks() {
    var team = team("Control room");

    assertThat(team.isNamed("control ROOM")).isTrue();
    assertThat(team.isNamed("  Control room ")).isTrue();
    assertThat(team.isNamed("Control  room")).isFalse();
    assertThat(team.isNamed("Nursing")).isFalse();
  }

  @Test
  @DisplayName("US-16 AC-1 a rename keeps the team and its permissions, one version later")
  void renameKeepsTheTeam() {
    var team = new Team(TEAM, EVENT, "Nursing", List.of(TeamPermission.of(Permission.HELP_REQUESTS_RESPOND)),
        Set.of(HelpCategory.Health), false, 3);

    var renamed = team.renamed("Medical", Set.of(HelpCategory.Safety, HelpCategory.Health));

    assertThat(renamed.name()).isEqualTo("Medical");
    assertThat(renamed.version()).isEqualTo(4);
    assertThat(renamed.permissions()).isEqualTo(team.permissions());
    assertThat(renamed.handledCategories()).containsExactly(HelpCategory.Health, HelpCategory.Safety);
    assertThat(renamed.deleted()).isFalse();
    assertThat(renamed.teamId()).isEqualTo(TEAM);
    assertThat(renamed.eventId()).isEqualTo(EVENT);
  }

  @Test
  @DisplayName("US-16 AC-1 handled categories are kept in declaration order, whatever order they arrive in")
  void categoriesAreInDeclarationOrder() {
    var sent = new LinkedHashSet<>(List.of(HelpCategory.Other, HelpCategory.Health, HelpCategory.LostPerson));

    assertThat(team("T").renamed("T", sent).handledCategories())
        .containsExactly(HelpCategory.Health, HelpCategory.LostPerson, HelpCategory.Other);
    assertThat(Team.categoriesOf(List.of(HelpCategory.Other, HelpCategory.Safety)))
        .containsExactly(HelpCategory.Safety, HelpCategory.Other);
  }

  @Test
  @DisplayName("US-16 AC-1 a repeated handled category is refused, and an empty list is accepted")
  void repeatedCategoryIsRefused() {
    assertThatThrownBy(() -> Team.categoriesOf(List.of(HelpCategory.Health, HelpCategory.Health)))
        .isInstanceOf(InvalidTeamException.class);
    // A null item is the caller's input too, so it is a bad request and not a 500
    assertThatThrownBy(() -> Team.categoriesOf(Arrays.asList(HelpCategory.Health, null)))
        .isInstanceOf(InvalidTeamException.class);
    assertThatThrownBy(() -> Team.categoriesOf(Arrays.asList((HelpCategory) null)))
        .isInstanceOf(InvalidTeamException.class);
    assertThatThrownBy(() -> Team.categoriesOf(null)).isInstanceOf(NullPointerException.class);
    assertThat(Team.categoriesOf(List.of())).isEmpty();
    assertThat(Team.categoriesOf(List.of(HelpCategory.values()))).containsExactly(HelpCategory.values());
  }

  @Test
  @DisplayName("US-16 AC-1 the handled categories cannot be changed through the set that was given")
  void categoriesAreImmutable() {
    var given = new LinkedHashSet<>(List.of(HelpCategory.Health));
    var team = team("T").renamed("T", given);

    given.add(HelpCategory.Safety);

    assertThat(team.handledCategories()).containsExactly(HelpCategory.Health);
    assertThatThrownBy(() -> team.handledCategories().add(HelpCategory.Other))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @DisplayName("US-16 AC-1 the permissions are listed in the order of the fixed list, whatever order they arrive in")
  void permissionsAreSorted() {
    var team = new Team(TEAM, EVENT, "Control room", List.of(TeamPermission.of(Permission.MAP_EDIT),
        TeamPermission.of(Permission.HELP_REQUESTS_TRIAGE)), Set.of(), false, 1);

    assertThat(team.permissions()).extracting(TeamPermission::permission)
        .containsExactly(Permission.HELP_REQUESTS_TRIAGE, Permission.MAP_EDIT);
  }

  @Test
  @DisplayName("US-16 AC-3 a live team grants what it has, and a deleted team grants nothing")
  void deletedTeamGrantsNothing() {
    var team = new Team(TEAM, EVENT, "Control room", List.of(TeamPermission.of(Permission.HELP_REQUESTS_TRIAGE)),
        Set.of(HelpCategory.Health), false, 1);

    assertThat(team.grants(Permission.HELP_REQUESTS_TRIAGE)).isTrue();
    assertThat(team.grants(Permission.MAP_EDIT)).isFalse();
    assertThat(team.grantsTeamReading()).isTrue();

    var deleted = team.asDeleted();

    assertThat(deleted.deleted()).isTrue();
    assertThat(deleted.version()).isEqualTo(2);
    assertThat(deleted.permissions()).isEqualTo(team.permissions());
    assertThat(deleted.handledCategories()).isEqualTo(team.handledCategories());
    assertThat(deleted.grants(Permission.HELP_REQUESTS_TRIAGE)).isFalse();
    assertThat(deleted.grantsTeamReading()).isFalse();
  }

  @Test
  @DisplayName("US-16 AC-1 only HelpRequests.Triage and Reports.Review let a member read the teams")
  void onlyTriageAndReviewReadTheTeams() {
    for (var permission : Permission.values()) {
      var team = new Team(TEAM, EVENT, "T", List.of(TeamPermission.of(permission)), Set.of(), false, 1);
      var reads = permission == Permission.HELP_REQUESTS_TRIAGE || permission == Permission.REPORTS_REVIEW;

      assertThat(team.grantsTeamReading()).as(permission.code()).isEqualTo(reads);
      assertThat(permission.readsTeams()).as(permission.code()).isEqualTo(reads);
    }
  }

  @Test
  @DisplayName("US-16 AC-1 a team with no permission grants nothing")
  void noPermissionGrantsNothing() {
    assertThat(team("T").grantsTeamReading()).isFalse();
    assertThat(team("T").grants(Permission.HELP_REQUESTS_TRIAGE)).isFalse();
  }
}
