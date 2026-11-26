package pt.isep.labdsof.authusers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** US-18: adding and removing a team's members through the real HTTP path, on a real PostgreSQL. */
class TeamMembersIT extends IntegrationTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final UUID event = UUID.randomUUID();
  private final UUID sofia = UUID.randomUUID();
  private final UUID rita = UUID.randomUUID();
  private final UUID nursing = UUID.randomUUID();

  private String path(UUID userId, UUID teamId) {
    return "/api/v1/events/" + event + "/members/" + userId + "/teams/" + teamId;
  }

  private void admin() {
    seedAccount(sofia, "Sofia Admin");
    seedMembership(event, sofia, "EventAdmin");
  }

  private void member() {
    seedAccount(rita, "Rita Participante");
    seedMembership(event, rita, "RegularUser");
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

  /** The MembershipChanged bodies the outbox holds for that member, oldest first, parsed (jsonb adds spaces). */
  private List<JsonNode> publishedFor(UUID userId) {
    return jdbc.sql("""
        SELECT body::text FROM outbox
        WHERE type = 'MembershipChanged' AND body->'payload'->>'userId' = :id ORDER BY id
        """).param("id", userId.toString()).query(String.class).list().stream()
        .map(body -> (JsonNode) JSON.readTree(body))
        .toList();
  }

  private static List<String> teamIdsIn(JsonNode published) {
    var ids = new ArrayList<String>();
    published.path("payload").path("teamIds").forEach(id -> ids.add(id.asString()));
    return ids;
  }

  @Test
  @DisplayName("US-18 AC-1 an EventAdmin adds a member to a team")
  void anEventAdminAddsAMemberToATeam() {
    admin();
    member();
    seedTeam(event, nursing, false);
    var api = new ApiClient(port);

    var response = api.send("PUT", path(rita, nursing), "Bearer " + JWKS.valid(sofia.toString()));

    assertThat(response.statusCode()).isEqualTo(204);
    assertThat(response.body()).isEmpty();
    assertThat(teamsOf(rita)).containsExactly(nursing);
    assertThat(versionOf(rita)).isEqualTo(2);
    assertThat(jdbc.sql("""
        SELECT assigned_by FROM team_assignment WHERE event_id = :event AND user_id = :user AND team_id = :team
        """).param("event", event).param("user", rita).param("team", nursing).query(UUID.class).single())
        .isEqualTo(sofia);
    assertThat(publishedFor(rita)).singleElement().satisfies(published -> {
      assertThat(published.path("eventId").asString()).isEqualTo(event.toString());
      assertThat(published.path("actorUserId").asString()).isEqualTo(sofia.toString());
      assertThat(published.path("payload").path("role").asString()).isEqualTo("RegularUser");
      assertThat(published.path("payload").path("status").asString()).isEqualTo("Active");
      assertThat(published.path("payload").path("version").asLong()).isEqualTo(2);
      assertThat(teamIdsIn(published)).containsExactly(nursing.toString());
    });
  }

  @Test
  @DisplayName("US-18 AC-1 adding a member twice changes nothing")
  void addingAMemberTwiceChangesNothing() {
    admin();
    member();
    seedTeam(event, nursing, false);
    var api = new ApiClient(port);
    var token = "Bearer " + JWKS.valid(sofia.toString());

    assertThat(api.send("PUT", path(rita, nursing), token).statusCode()).isEqualTo(204);
    assertThat(api.send("PUT", path(rita, nursing), token).statusCode()).isEqualTo(204);

    assertThat(teamsOf(rita)).containsExactly(nursing);
    assertThat(versionOf(rita)).isEqualTo(2);
    assertThat(publishedFor(rita)).hasSize(1);
  }

  @Test
  @DisplayName("US-18 AC-3 a member can be in two teams and both are published")
  void aMemberCanBeInTwoTeamsAndBothArePublished() {
    admin();
    member();
    var security = UUID.randomUUID();
    seedTeam(event, nursing, false);
    seedTeam(event, security, false);
    var api = new ApiClient(port);
    var token = "Bearer " + JWKS.valid(sofia.toString());

    assertThat(api.send("PUT", path(rita, nursing), token).statusCode()).isEqualTo(204);
    assertThat(api.send("PUT", path(rita, security), token).statusCode()).isEqualTo(204);

    assertThat(teamsOf(rita)).containsExactlyInAnyOrder(nursing, security);
    assertThat(versionOf(rita)).isEqualTo(3);
    var published = publishedFor(rita);
    assertThat(published).hasSize(2);
    assertThat(teamIdsIn(published.getLast()))
        .containsExactlyInAnyOrder(nursing.toString(), security.toString());
    assertThat(published.getLast().path("payload").path("version").asLong()).isEqualTo(3);
  }

  @Test
  @DisplayName("US-18 AC-4 an EventAdmin removes a member from a team")
  void anEventAdminRemovesAMemberFromATeam() {
    admin();
    member();
    var security = UUID.randomUUID();
    seedTeam(event, nursing, false);
    seedTeam(event, security, false);
    var api = new ApiClient(port);
    var token = "Bearer " + JWKS.valid(sofia.toString());
    assertThat(api.send("PUT", path(rita, nursing), token).statusCode()).isEqualTo(204);
    assertThat(api.send("PUT", path(rita, security), token).statusCode()).isEqualTo(204);

    var response = api.send("DELETE", path(rita, nursing), token);

    assertThat(response.statusCode()).isEqualTo(204);
    assertThat(teamsOf(rita)).containsExactly(security);
    assertThat(versionOf(rita)).isEqualTo(4);
    assertThat(teamIdsIn(publishedFor(rita).getLast())).containsExactly(security.toString());
    assertThat(publishedFor(rita).getLast().path("payload").path("version").asLong()).isEqualTo(4);
  }

  @Test
  @DisplayName("US-18 AC-4 removing a member who is not in the team changes nothing")
  void removingAMemberWhoIsNotInTheTeamChangesNothing() {
    admin();
    member();
    var api = new ApiClient(port);

    var response = api.send("DELETE", path(rita, nursing), "Bearer " + JWKS.valid(sofia.toString()));

    assertThat(response.statusCode()).isEqualTo(204);
    assertThat(versionOf(rita)).isEqualTo(1);
    assertThat(publishedFor(rita)).isEmpty();
  }

  @Test
  @DisplayName("US-18 AC-2 a user who is not a member of the event cannot be put in a team")
  void aUserWhoIsNotAMemberCannotBePutInATeam() {
    admin();
    seedAccount(rita, "Rita Participante"); // an account, but no membership of this event
    seedTeam(event, nursing, false);

    var response = new ApiClient(port).send("PUT", path(rita, nursing), "Bearer " + JWKS.valid(sofia.toString()));

    ApiClient.assertProblem(response, 404);
    assertThat(teamsOf(rita)).isEmpty();
    assertThat(publishedFor(rita)).isEmpty();
  }

  @Test
  @DisplayName("US-18 AC-1 a team of another event, a deleted team and an unknown team are all the same 404")
  void onlyALiveTeamOfThisEventTakesMembers() {
    admin();
    member();
    var elsewhere = UUID.randomUUID();
    var deleted = UUID.randomUUID();
    seedTeam(UUID.randomUUID(), elsewhere, false);
    seedTeam(event, deleted, true);
    var api = new ApiClient(port);
    var token = "Bearer " + JWKS.valid(sofia.toString());

    for (var team : List.of(elsewhere, deleted, UUID.randomUUID())) {
      ApiClient.assertProblem(api.send("PUT", path(rita, team), token), 404);
    }

    assertThat(teamsOf(rita)).isEmpty();
    assertThat(publishedFor(rita)).isEmpty();
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @CsvSource({"PUT", "DELETE"})
  @DisplayName("US-18 AC-1 only an EventAdmin of the event may change its teams' members")
  void onlyAnEventAdminMayChangeTeamMembers(String method) {
    admin();
    member();
    seedTeam(event, nursing, false);
    var stranger = UUID.randomUUID();
    var otherEventAdmin = UUID.randomUUID();
    seedAccount(otherEventAdmin, "Admin Elsewhere");
    seedMembership(UUID.randomUUID(), otherEventAdmin, "EventAdmin");
    var api = new ApiClient(port);

    // A member of the event who is not an EventAdmin: 403, because the event is theirs to see
    ApiClient.assertProblem(api.send(method, path(rita, nursing), "Bearer " + JWKS.valid(rita.toString())), 403);
    // Outside the event: the same 404 as an unknown event, for a stranger and for another event's admin
    ApiClient.assertProblem(api.send(method, path(rita, nursing), "Bearer " + JWKS.valid(stranger.toString())), 404);
    ApiClient.assertProblem(
        api.send(method, path(rita, nursing), "Bearer " + JWKS.valid(otherEventAdmin.toString())), 404);

    assertThat(teamsOf(rita)).isEmpty();
    assertThat(publishedFor(rita)).isEmpty();
  }

  @Test
  @DisplayName("US-18 AC-1 an unknown event is the same 404, and no token at all is 401")
  void anUnknownEventIsTheSame404() {
    var api = new ApiClient(port);
    var unknown = "/api/v1/events/" + UUID.randomUUID() + "/members/" + rita + "/teams/" + nursing;

    assertThat(api.send("PUT", unknown, null).statusCode()).isEqualTo(401);
    ApiClient.assertProblem(api.send("PUT", unknown, "Bearer " + JWKS.valid(sofia.toString())), 404);
    ApiClient.assertProblem(api.send("DELETE", unknown, "Bearer " + JWKS.valid(sofia.toString())), 404);
  }

  @ParameterizedTest(name = "[{index}] {0}/{1}/{2}")
  @CsvSource({
      "not-a-uuid, 11111111-1111-1111-1111-111111111111, 22222222-2222-2222-2222-222222222222",
      "11111111-1111-1111-1111-111111111111, not-a-uuid, 22222222-2222-2222-2222-222222222222",
      "11111111-1111-1111-1111-111111111111, 22222222-2222-2222-2222-222222222222, not-a-uuid"})
  @DisplayName("US-18 AC-1 a malformed id is refused with 400")
  void aMalformedIdIsRefused(String eventId, String userId, String teamId) {
    var path = "/api/v1/events/" + eventId + "/members/" + userId + "/teams/" + teamId;

    var response = new ApiClient(port).send("PUT", path, "Bearer " + JWKS.valid(sofia.toString()));

    ApiClient.assertProblem(response, 400);
  }
}
