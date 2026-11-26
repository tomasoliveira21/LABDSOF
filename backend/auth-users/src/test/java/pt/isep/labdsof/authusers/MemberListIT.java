package pt.isep.labdsof.authusers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** US-18 D10: the member list an EventAdmin picks from, through the real HTTP path. */
class MemberListIT extends IntegrationTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final UUID event = UUID.randomUUID();
  private final UUID sofia = UUID.randomUUID();
  private final UUID rita = UUID.randomUUID();
  private final UUID pedro = UUID.randomUUID();
  private final UUID nursing = UUID.randomUUID();

  private String path(String query) {
    return "/api/v1/events/" + event + "/members" + query;
  }

  private JsonNode list(String query, UUID caller) {
    var response = new ApiClient(port).get(path(query), JWKS.valid(caller.toString()));
    assertThat(response.statusCode()).isEqualTo(200);
    return JSON.readTree(response.body());
  }

  private void seedEvent() {
    seedAccount(sofia, "Sofia Admin");
    seedMembership(event, sofia, "EventAdmin");
    seedAccount(rita, "Rita Participante");
    seedMembership(event, rita, "RegularUser");
    seedAccount(pedro, "Pedro Voluntario");
    seedMembership(event, pedro, "RegularUser");
    seedTeam(event, nursing, false);
    assertThat(new ApiClient(port).send("PUT",
        "/api/v1/events/" + event + "/members/" + rita + "/teams/" + nursing,
        "Bearer " + JWKS.valid(sofia.toString())).statusCode()).isEqualTo(204);
  }

  @Test
  @DisplayName("US-18 AC-1 the list carries every member of the event with role and teams")
  void theListCarriesEveryMemberOfTheEvent() {
    seedEvent();
    var elsewhere = UUID.randomUUID();
    seedAccount(elsewhere, "Admin Elsewhere");
    seedMembership(UUID.randomUUID(), elsewhere, "EventAdmin");

    var members = list("", sofia);

    assertThat(members.size()).isEqualTo(3);
    assertThat(members.get(0).path("displayName").asString()).isEqualTo("Pedro Voluntario");
    assertThat(members.get(1).path("userId").asString()).isEqualTo(rita.toString());
    assertThat(members.get(1).path("role").asString()).isEqualTo("RegularUser");
    assertThat(members.get(1).path("teamIds").size()).isEqualTo(1);
    assertThat(members.get(1).path("teamIds").get(0).asString()).isEqualTo(nursing.toString());
    assertThat(members.get(2).path("userId").asString()).isEqualTo(sofia.toString());
    assertThat(members.get(2).path("role").asString()).isEqualTo("EventAdmin");
    assertThat(members.get(2).path("teamIds")).isEmpty();
    assertThat(members.toString()).doesNotContain(elsewhere.toString()).doesNotContain("@farol.test");
  }

  @Test
  @DisplayName("US-18 AC-1 the list can be filtered by role and by team")
  void theListCanBeFilteredByRoleAndByTeam() {
    seedEvent();

    assertThat(list("?role=EventAdmin", sofia).size()).isEqualTo(1);
    assertThat(list("?role=RegularUser", sofia).size()).isEqualTo(2);
    assertThat(list("?teamId=" + nursing, sofia).get(0).path("userId").asString()).isEqualTo(rita.toString());
    assertThat(list("?role=RegularUser&teamId=" + nursing, sofia).size()).isEqualTo(1);
    assertThat(list("?role=EventAdmin&teamId=" + nursing, sofia)).isEmpty();
    assertThat(list("?teamId=" + UUID.randomUUID(), sofia)).isEmpty(); // a team of another event
  }

  @Test
  @DisplayName("US-18 AC-1 a disabled account is still a member of the event")
  void aDisabledAccountIsStillAMember() {
    seedEvent();
    jdbc.sql("UPDATE user_account SET status = 'Disabled' WHERE user_id = :id").param("id", pedro).update();

    assertThat(list("", sofia).size()).isEqualTo(3);
  }

  @Test
  @DisplayName("US-18 AC-1 only an EventAdmin of the event may list its members")
  void onlyAnEventAdminMayListTheMembers() {
    seedEvent();
    var stranger = UUID.randomUUID();
    var api = new ApiClient(port);

    assertThat(api.get(path(""), null).statusCode()).isEqualTo(401);
    ApiClient.assertProblem(api.get(path(""), JWKS.valid(rita.toString())), 403);
    ApiClient.assertProblem(api.get(path(""), JWKS.valid(stranger.toString())), 404);
    ApiClient.assertProblem(
        new ApiClient(port).get("/api/v1/events/" + UUID.randomUUID() + "/members",
            JWKS.valid(sofia.toString())), 404);
  }
}
