package pt.isep.labdsof.events.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class TeamPermissionTest {

  @Test
  @DisplayName("US-16 AC-1 every permission of the fixed list keeps its code")
  void codesAreTheFixedList() {
    assertThat(Permission.values()).extracting(Permission::code).containsExactly("HelpRequests.Triage",
        "HelpRequests.Respond", "Reports.Review", "Reports.Respond", "Announcements.Publish", "Map.Edit",
        "Transport.Edit", "Entrances.Update");
    for (var permission : Permission.values()) {
      assertThat(Permission.ofCode(permission.code())).isEqualTo(permission);
    }
  }

  @Test
  @DisplayName("US-16 AC-1 an unknown permission code is refused")
  void unknownCodeIsRefused() {
    assertThatThrownBy(() -> Permission.ofCode("Map.Delete")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Permission.ofCode(null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Permission.ofCode("")).isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @EnumSource(Permission.class)
  @DisplayName("US-16 AC-1 only Reports.Review carries report types")
  void onlyReviewCarriesReportTypes(Permission permission) {
    assertThat(TeamPermission.of(permission).reportTypes()).isEmpty();

    if (permission == Permission.REPORTS_REVIEW) {
      assertThat(permission.carriesReportTypes()).isTrue();
      assertThat(new TeamPermission(permission, Set.of(ReportType.Hazard)).reportTypes())
          .containsExactly(ReportType.Hazard);
    } else {
      assertThat(permission.carriesReportTypes()).isFalse();
      assertThatThrownBy(() -> new TeamPermission(permission, Set.of(ReportType.Hazard)))
          .isInstanceOf(InvalidTeamException.class);
    }
  }

  @Test
  @DisplayName("US-16 AC-1 the report types are kept in declaration order and cannot be changed afterwards")
  void reportTypesAreStableAndImmutable() {
    var given = new LinkedHashSet<>(List.of(ReportType.WcProblem, ReportType.CrowdedArea));

    var permission = new TeamPermission(Permission.REPORTS_REVIEW, given);
    given.add(ReportType.Hazard);

    assertThat(permission.reportTypes()).containsExactly(ReportType.CrowdedArea, ReportType.WcProblem);
    assertThatThrownBy(() -> permission.reportTypes().add(ReportType.Hazard))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @DisplayName("US-16 AC-1 a null permission or a null report type set is a bug")
  void nullsAreRejected() {
    assertThatThrownBy(() -> new TeamPermission(null, Set.of())).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new TeamPermission(Permission.REPORTS_REVIEW, null))
        .isInstanceOf(NullPointerException.class);
  }
}
