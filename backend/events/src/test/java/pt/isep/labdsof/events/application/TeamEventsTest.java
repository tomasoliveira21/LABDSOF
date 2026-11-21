package pt.isep.labdsof.events.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pt.isep.labdsof.events.domain.HelpCategory;
import pt.isep.labdsof.events.domain.Permission;
import pt.isep.labdsof.events.domain.ReportType;
import pt.isep.labdsof.events.domain.Team;
import pt.isep.labdsof.events.domain.TeamPermission;

/** The payload of TeamChanged (API outline §7.4); EventContractIT checks it against the JSON Schema. */
class TeamEventsTest {

  private static final Instant NOW = Instant.parse("2027-05-08T23:14:05Z");
  private static final UUID TEAM = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID EVENT = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID ACTOR = UUID.fromString("33333333-3333-3333-3333-333333333333");

  @Test
  @DisplayName("US-16 AC-1 TeamChanged carries the team's full state, its permissions and its version")
  void payloadIsTheFullState() {
    var team = new Team(TEAM, EVENT, "Control room",
        List.of(new TeamPermission(Permission.REPORTS_REVIEW, Set.of(ReportType.Hazard, ReportType.NoWater)),
            TeamPermission.of(Permission.HELP_REQUESTS_TRIAGE)),
        Set.of(HelpCategory.Other, HelpCategory.Health), false, 7);

    var event = TeamEvents.changed(team, ACTOR, NOW);

    assertThat(event.type()).isEqualTo("TeamChanged");
    assertThat(event.routingKey()).isEqualTo("team.changed");
    assertThat(event.exchange()).isEqualTo(OutgoingEvent.EXCHANGE);
    assertThat(event.schemaVersion()).isEqualTo(1);
    assertThat(event.occurredAt()).isEqualTo(NOW);
    assertThat(event.eventId()).isEqualTo(EVENT);
    assertThat(event.actorUserId()).isEqualTo(ACTOR.toString());
    assertThat(event.messageId()).isNotNull();
    assertThat(event.payload()).containsOnlyKeys("teamId", "name", "permissions", "handledCategories", "deleted",
        "version");
    assertThat(event.payload()).containsEntry("teamId", TEAM.toString()).containsEntry("name", "Control room")
        .containsEntry("handledCategories", List.of("Health", "Other")).containsEntry("deleted", false)
        .containsEntry("version", 7L);
    assertThat(event.payload().get("permissions")).isEqualTo(List.of(
        Map.of("permission", "HelpRequests.Triage", "reportTypes", List.of()),
        Map.of("permission", "Reports.Review", "reportTypes", List.of("NoWater", "Hazard"))));
  }

  @Test
  @DisplayName("US-16 AC-1 every TeamChanged has its own messageId")
  void messageIdIsNew() {
    var team = Team.created(TEAM, EVENT, "Nursing");

    assertThat(TeamEvents.changed(team, ACTOR, NOW).messageId())
        .isNotEqualTo(TeamEvents.changed(team, ACTOR, NOW).messageId());
  }

  @Test
  @DisplayName("US-16 AC-3 TeamChanged of a deleted team carries deleted true and the permissions it had")
  void deletedPayload() {
    var team = new Team(TEAM, EVENT, "Nursing", List.of(TeamPermission.of(Permission.HELP_REQUESTS_RESPOND)),
        Set.of(HelpCategory.Health), false, 1).asDeleted();

    var payload = TeamEvents.changed(team, ACTOR, NOW).payload();

    assertThat(payload).containsEntry("deleted", true).containsEntry("version", 2L);
    assertThat(payload.get("permissions")).isEqualTo(List.of(
        Map.of("permission", "HelpRequests.Respond", "reportTypes", List.of())));
  }

  @Test
  @DisplayName("US-16 AC-1 a team with nothing set carries empty lists, never null")
  void emptyListsAreEmptyNotNull() {
    var payload = TeamEvents.changed(Team.created(TEAM, EVENT, "Nursing"), ACTOR, NOW).payload();

    assertThat(payload.get("permissions")).isEqualTo(List.of());
    assertThat(payload.get("handledCategories")).isEqualTo(List.of());
  }
}
