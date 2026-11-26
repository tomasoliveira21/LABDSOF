package pt.isep.labdsof.events.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The rules US-17 adds to the team: the whole permission set, with the report types of Reports.Review. */
class TeamPermissionsTest {

  private static final UUID TEAM = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID EVENT = UUID.fromString("22222222-2222-2222-2222-222222222222");

  private static Team team() {
    return Team.created(TEAM, EVENT, "Control room").renamed("Control room", Set.of(HelpCategory.Health));
  }

  private static TeamPermission review(ReportType... types) {
    return new TeamPermission(Permission.REPORTS_REVIEW, Set.of(types));
  }

  @Test
  @DisplayName("US-17 AC-1 the permissions replace the whole set, one version later, categories untouched")
  void permissionsReplaceTheWholeSet() {
    var team = team();

    var given = team.withPermissions(List.of(TeamPermission.of(Permission.MAP_EDIT),
        TeamPermission.of(Permission.HELP_REQUESTS_TRIAGE)));

    assertThat(given.version()).isEqualTo(team.version() + 1);
    assertThat(given.name()).isEqualTo(team.name());
    assertThat(given.handledCategories()).isEqualTo(team.handledCategories());
    assertThat(given.deleted()).isFalse();
    assertThat(given.permissions()).extracting(TeamPermission::permission)
        .containsExactly(Permission.HELP_REQUESTS_TRIAGE, Permission.MAP_EDIT);
    assertThat(given.grants(Permission.MAP_EDIT)).isTrue();

    var taken = given.withPermissions(List.of());

    assertThat(taken.permissions()).isEmpty();
    assertThat(taken.grants(Permission.MAP_EDIT)).isFalse();
    assertThat(taken.version()).isEqualTo(given.version() + 1);
  }

  @Test
  @DisplayName("US-17 AC-1 a repeated permission is refused, whatever its report types")
  void repeatedPermissionIsRefused() {
    assertThatThrownBy(() -> Team.permissionsOf(List.of(TeamPermission.of(Permission.MAP_EDIT),
        TeamPermission.of(Permission.MAP_EDIT)))).isInstanceOf(InvalidTeamException.class);
    assertThatThrownBy(() -> Team.permissionsOf(List.of(review(ReportType.Hazard), review(ReportType.NoWater))))
        .isInstanceOf(InvalidTeamException.class);
    assertThatThrownBy(() -> Team.permissionsOf(Arrays.asList(TeamPermission.of(Permission.MAP_EDIT), null)))
        .isInstanceOf(InvalidTeamException.class);
    assertThatThrownBy(() -> Team.permissionsOf(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("US-17 AC-1 the eight permissions of the fixed list are accepted at once, and an empty set too")
  void everyPermissionAtOnce() {
    var all = Arrays.stream(Permission.values())
        .map(permission -> permission.carriesReportTypes() ? review(ReportType.values())
            : TeamPermission.of(permission))
        .toList();

    assertThat(Team.permissionsOf(all)).hasSize(8);
    assertThat(team().withPermissions(all).permissions()).extracting(TeamPermission::permission)
        .containsExactly(Permission.values());
    assertThat(Team.permissionsOf(List.of())).isEmpty();
  }

  @Test
  @DisplayName("US-17 AC-2 Reports.Review carries the report types it reviews, in a stable order")
  void reviewCarriesItsReportTypes() {
    var given = team().withPermissions(List.of(review(ReportType.WcProblem, ReportType.CrowdedArea)));

    assertThat(given.permissions()).singleElement().satisfies(permission -> {
      assertThat(permission.permission()).isEqualTo(Permission.REPORTS_REVIEW);
      assertThat(permission.reportTypes()).containsExactly(ReportType.CrowdedArea, ReportType.WcProblem);
    });
  }

  @Test
  @DisplayName("US-17 AC-2 Reports.Review with no report type is accepted and reviews nothing")
  void reviewWithoutTypesIsAccepted() {
    var given = team().withPermissions(List.of(TeamPermission.of(Permission.REPORTS_REVIEW)));

    assertThat(given.grants(Permission.REPORTS_REVIEW)).isTrue();
    assertThat(given.permissions()).singleElement()
        .satisfies(permission -> assertThat(permission.reportTypes()).isEmpty());
  }

  @Test
  @DisplayName("US-17 AC-2 only Reports.Review carries report types, and a repeated or missing one is refused")
  void reportTypesBelongToReviewOnly() {
    assertThatThrownBy(() -> new TeamPermission(Permission.MAP_EDIT, Set.of(ReportType.Hazard)))
        .isInstanceOf(InvalidTeamException.class);
    assertThatThrownBy(() -> TeamPermission.reportTypesOf(List.of(ReportType.Hazard, ReportType.Hazard)))
        .isInstanceOf(InvalidTeamException.class);
    assertThatThrownBy(() -> TeamPermission.reportTypesOf(Arrays.asList(ReportType.Hazard, null)))
        .isInstanceOf(InvalidTeamException.class);
    assertThatThrownBy(() -> TeamPermission.reportTypesOf(null)).isInstanceOf(NullPointerException.class);
    assertThat(TeamPermission.reportTypesOf(List.of())).isEmpty();
    assertThat(TeamPermission.reportTypesOf(List.of(ReportType.values())))
        .containsExactly(ReportType.values());
  }

  @Test
  @DisplayName("US-17 AC-4 a deleted team grants nothing, whatever permissions it was given")
  void deletedTeamGrantsNothing() {
    var given = team().withPermissions(List.of(TeamPermission.of(Permission.ANNOUNCEMENTS_PUBLISH)));

    assertThat(given.asDeleted().grants(Permission.ANNOUNCEMENTS_PUBLISH)).isFalse();
  }
}
