package pt.isep.labdsof.events.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pt.isep.labdsof.events.IntegrationTest;
import pt.isep.labdsof.events.application.Teams;
import pt.isep.labdsof.events.application.Teams.Write;
import pt.isep.labdsof.events.domain.HelpCategory;
import pt.isep.labdsof.events.domain.Permission;
import pt.isep.labdsof.events.domain.ReportType;
import pt.isep.labdsof.events.domain.Team;
import pt.isep.labdsof.events.domain.TeamPermission;

/**
 * The team table through its port. The use cases read the team before they write it, so the outcomes of a lost
 * race (US-16 D3) can only be reached here, by writing a row that is already deleted.
 */
class JdbcTeamsIT extends IntegrationTest {

  private static final Instant AT = Instant.parse("2027-05-08T23:14:05Z");

  @Autowired Teams teams;

  private UUID event() {
    return insertEvent("Published", null, "jt-" + UUID.randomUUID().toString().substring(0, 8));
  }

  private Team stored(UUID eventId, String name) {
    var team = Team.created(UUID.randomUUID(), eventId, name);
    assertThat(teams.add(team, UUID.randomUUID(), AT)).isEqualTo(Write.STORED);
    return team;
  }

  @Test
  @DisplayName("US-16 AC-3 a rename or a delete of a team another request already deleted reports it gone")
  void writingADeletedRowIsGone() {
    var eventId = event();
    var team = stored(eventId, "Nursing");
    assertThat(teams.delete(team.asDeleted(), UUID.randomUUID(), AT)).isEqualTo(Write.STORED);

    assertThat(teams.delete(team.asDeleted(), UUID.randomUUID(), AT)).isEqualTo(Write.GONE);
    assertThat(teams.replace(team.renamed("Medical", Set.of()), UUID.randomUUID(), AT)).isEqualTo(Write.GONE);
    assertThat(teams.liveOfEvent(eventId)).isEmpty();
  }

  @Test
  @DisplayName("US-16 AC-2 the unique index reports a name taken on an insert and on a rename")
  void takenNameIsReported() {
    var eventId = event();
    stored(eventId, "Control room");
    var other = stored(eventId, "Nursing");

    assertThat(teams.add(Team.created(UUID.randomUUID(), eventId, "control ROOM"), UUID.randomUUID(), AT))
        .isEqualTo(Write.NAME_TAKEN);
    assertThat(teams.replace(other.renamed("CONTROL ROOM", Set.of()), UUID.randomUUID(), AT))
        .isEqualTo(Write.NAME_TAKEN);
    assertThat(teams.liveOfEvent(eventId)).extracting(Team::name).containsExactly("Control room", "Nursing");
  }

  @Test
  @DisplayName("US-16 AC-1 a team is read back with its categories, its permissions and its report types")
  void teamIsReadBackWhole() {
    var eventId = event();
    var team = stored(eventId, "Control room");
    assertThat(teams.replace(team.renamed("Control room", Set.of(HelpCategory.Other, HelpCategory.Health)),
        UUID.randomUUID(), AT)).isEqualTo(Write.STORED);
    jdbc.sql("""
        INSERT INTO team_permission (event_id, team_id, permission, report_types) VALUES
          (:event, :team, 'Reports.Review', CAST('{Hazard,NoWater}' AS text[])),
          (:event, :team, 'HelpRequests.Triage', '{}')
        """).param("event", eventId).param("team", team.teamId()).update();

    var read = teams.liveOfEvent(eventId, team.teamId()).orElseThrow();

    assertThat(read.name()).isEqualTo("Control room");
    assertThat(read.version()).isEqualTo(2);
    assertThat(read.deleted()).isFalse();
    assertThat(read.handledCategories()).containsExactly(HelpCategory.Health, HelpCategory.Other);
    assertThat(read.permissions()).containsExactly(
        TeamPermission.of(Permission.HELP_REQUESTS_TRIAGE),
        new TeamPermission(Permission.REPORTS_REVIEW, Set.of(ReportType.NoWater, ReportType.Hazard)));
    assertThat(read.grants(Permission.HELP_REQUESTS_TRIAGE)).isTrue();
  }

  @Test
  @DisplayName("US-16 AC-1 a team of another event, a deleted team and an unknown id are not read")
  void otherEventsAndDeletedTeamsAreNotRead() {
    var mine = event();
    var other = event();
    var elsewhere = stored(other, "Theirs");
    var deleted = stored(mine, "Gone");
    teams.delete(deleted.asDeleted(), UUID.randomUUID(), AT);
    var live = stored(mine, "Mine");

    assertThat(teams.liveOfEvent(mine, elsewhere.teamId())).isEmpty();
    assertThat(teams.liveOfEvent(mine, deleted.teamId())).isEmpty();
    assertThat(teams.liveOfEvent(mine, UUID.randomUUID())).isEmpty();
    assertThat(teams.liveOfEvent(UUID.randomUUID())).isEmpty();
    assertThat(teams.liveOfEvent(mine)).extracting(Team::teamId).containsExactly(live.teamId());
    assertThat(teams.liveOfEvent(mine, live.teamId())).isPresent();
    assertThat(teams.liveOfEvent(other)).extracting(Team::teamId).containsExactly(elsewhere.teamId());
  }
}
