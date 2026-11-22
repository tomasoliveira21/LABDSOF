package pt.isep.labdsof.reports;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import pt.isep.labdsof.reports.domain.Category;
import pt.isep.labdsof.reports.domain.Membership;
import pt.isep.labdsof.reports.domain.Membership.Role;
import pt.isep.labdsof.reports.domain.Permission;
import pt.isep.labdsof.reports.domain.Team;

/**
 * US-36 AC-2: GET …/triage-status and its security matrix (spec US-36 D4, D5). The shared test context has no AI
 * configured (FAROL_AI_URL empty, as the default stack and CI), so the status is unavailable.
 */
class TriageStatusIT extends IntegrationTest {

  final UUID event = UUID.randomUUID();
  final String participant = "participant-" + UUID.randomUUID();
  final String operator = "operator-" + UUID.randomUUID();

  @BeforeEach
  void controlRoom() {
    var team = UUID.randomUUID();
    copies.upsertIfNewer(new Team(team, event, "Control room",
        List.of(new Team.TeamPermission(Permission.HELP_REQUESTS_TRIAGE, Set.of())), Set.of(Category.Health),
        false, 1));
    member(event, participant, Role.RegularUser);
    member(event, operator, Role.RegularUser, team);
  }

  String path() {
    return "/api/v1/events/" + event + "/triage-status";
  }

  @Test
  @DisplayName("US-36 AC-2 the control room reads the triage status")
  void status() throws Exception {
    var before = Instant.now();
    var response = get(path(), JWKS.valid(operator));

    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    Map<String, Object> body = JsonPath.read(response.body(), "$");
    assertThat(body).containsOnlyKeys("available", "reason", "checkedAt")
        .containsEntry("available", false).containsEntry("reason", "Unavailable");
    assertThat(Instant.parse((String) body.get("checkedAt"))).isBetween(before.minusSeconds(1), Instant.now());
  }

  @Test
  @DisplayName("US-36 AC-2 a member without HelpRequests.Triage gets 403")
  void forbidden() throws Exception {
    var response = get(path(), JWKS.valid(participant));
    assertThat(response.statusCode()).isEqualTo(403);
    assertThat(response.body()).doesNotContain("available");
  }

  @Test
  @DisplayName("US-36 AC-2 a non-member, a removed member or a member of another event gets 404")
  void notFound() throws Exception {
    var removed = "removed-" + UUID.randomUUID();
    copies.upsertIfNewer(new Membership(event, removed, Role.EventAdmin, Membership.Status.Removed,
        Set.of(), 1));
    var elsewhere = "elsewhere-" + UUID.randomUUID();
    member(UUID.randomUUID(), elsewhere, Role.EventAdmin);
    var unknownEvent = get("/api/v1/events/" + UUID.randomUUID() + "/triage-status", JWKS.valid(operator));
    assertThat(unknownEvent.statusCode()).isEqualTo(404);

    for (var user : List.of("nobody-" + UUID.randomUUID(), removed, elsewhere)) {
      var response = get(path(), JWKS.valid(user));
      assertThat(response.statusCode()).as(user).isEqualTo(404);
      // Same answer as for an event that does not exist: existence is not revealed
      assertThat(response.body().replace(event.toString(), ""))
          .isEqualTo(unknownEvent.body().replaceAll("events/[0-9a-f-]{36}", "events/"));
    }
  }

  @Test
  @DisplayName("US-36 AC-2 a malformed event id is rejected")
  void malformed() throws Exception {
    assertThat(get("/api/v1/events/not-a-uuid/triage-status", JWKS.valid(operator)).statusCode()).isEqualTo(400);
  }

  static Stream<String> badTokens() {
    return Stream.of(null, JWKS.expired("operator"), JWKS.wrongSignature("operator"),
        JWKS.wrongIssuer("operator"), JWKS.wrongAudience("operator"), "not-a-jwt");
  }

  @ParameterizedTest
  @MethodSource("badTokens")
  @DisplayName("US-36 AC-2 every bad token gets 401")
  void badToken(String token) throws Exception {
    assertThat(get(path(), token).statusCode()).isEqualTo(401);
  }
}
