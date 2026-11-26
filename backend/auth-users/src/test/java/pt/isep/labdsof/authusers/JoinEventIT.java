package pt.isep.labdsof.authusers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

/** US-12: joining an event by its link through the real HTTP path, on a real PostgreSQL. */
class JoinEventIT extends IntegrationTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final UUID rita = UUID.randomUUID();
  private final UUID event = UUID.randomUUID();

  private String path() {
    return "/api/v1/events/" + event + "/members/me";
  }

  private void seedCopy(String status) {
    jdbc.sql("""
        INSERT INTO event_copy (event_id, name, status, version) VALUES (:id, :name, :status, 1)
        ON CONFLICT (event_id) DO UPDATE SET status = excluded.status
        """).param("id", event).param("name", "Queima das Fitas 2027").param("status", status).update();
  }

  private long memberships() {
    return jdbc.sql("SELECT count(*) FROM event_membership WHERE event_id = :event AND user_id = :user")
        .param("event", event).param("user", rita).query(Long.class).single();
  }

  private long published() {
    return jdbc.sql("""
        SELECT count(*) FROM outbox
        WHERE type = 'MembershipChanged' AND body->'payload'->>'userId' = :id
        """).param("id", rita.toString()).query(Long.class).single();
  }

  @Test
  @DisplayName("US-12 AC-1 a logged-in user joins a published event")
  void aLoggedInUserJoinsAPublishedEvent() {
    seedAccount(rita, "Rita Participante");
    seedCopy("Published");

    var response = new ApiClient(port).send("POST", path(), "Bearer " + JWKS.valid(rita.toString()));

    assertThat(response.statusCode()).isEqualTo(201);
    var body = JSON.readTree(response.body());
    assertThat(body.path("eventId").asString()).isEqualTo(event.toString());
    assertThat(body.path("eventName").asString()).isEqualTo("Queima das Fitas 2027");
    assertThat(body.path("eventStatus").asString()).isEqualTo("Published");
    assertThat(body.path("role").asString()).isEqualTo("RegularUser");
    assertThat(body.path("teamIds")).isEmpty();
    assertThat(memberships()).isEqualTo(1);
    assertThat(published()).isEqualTo(1);
    assertThat(jdbc.sql("""
        SELECT created_by FROM event_membership WHERE event_id = :event AND user_id = :user
        """).param("event", event).param("user", rita).query(UUID.class).single()).isEqualTo(rita);
    // The event reaches the caller's own list at once (US-01)
    assertThat(new ApiClient(port).get("/api/v1/me/memberships", JWKS.valid(rita.toString())).body())
        .contains(event.toString());
  }

  @Test
  @DisplayName("US-12 AC-1 joining twice changes nothing and answers 200")
  void joiningTwiceAnswers200() {
    seedAccount(rita, "Rita Participante");
    seedCopy("Published");
    var api = new ApiClient(port);
    var token = "Bearer " + JWKS.valid(rita.toString());
    assertThat(api.send("POST", path(), token).statusCode()).isEqualTo(201);

    var again = api.send("POST", path(), token);

    assertThat(again.statusCode()).isEqualTo(200);
    assertThat(JSON.readTree(again.body()).path("role").asString()).isEqualTo("RegularUser");
    assertThat(memberships()).isEqualTo(1);
    assertThat(published()).isEqualTo(1);
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @ValueSource(strings = {"Draft", "Suspended", "Closed"})
  @DisplayName("US-12 AC-3 only a published event can be joined")
  void onlyAPublishedEventCanBeJoined(String status) {
    seedAccount(rita, "Rita Participante");
    seedCopy(status);

    ApiClient.assertProblem(new ApiClient(port).send("POST", path(), "Bearer " + JWKS.valid(rita.toString())), 404);

    assertThat(memberships()).isZero();
    assertThat(published()).isZero();
  }

  @Test
  @DisplayName("US-12 AC-3 an event with no copy and an unknown event are the same 404")
  void anEventWithNoCopyAndAnUnknownEventAreTheSame404() {
    seedAccount(rita, "Rita Participante");
    var api = new ApiClient(port);
    var token = "Bearer " + JWKS.valid(rita.toString());

    var noCopy = api.send("POST", path(), token);
    var unknown = api.send("POST", "/api/v1/events/" + UUID.randomUUID() + "/members/me", token);

    ApiClient.assertProblem(noCopy, 404);
    ApiClient.assertProblem(unknown, 404);
    assertThat(ApiClient.json(noCopy).get("detail")).isEqualTo(ApiClient.json(unknown).get("detail"));
    assertThat(memberships()).isZero();
  }

  @Test
  @DisplayName("US-12 AC-1 an account that cannot act is refused")
  void anAccountThatCannotActIsRefused() {
    seedCopy("Published");
    var api = new ApiClient(port);
    var token = "Bearer " + JWKS.valid(rita.toString());

    // No account yet: GET /me is what creates one (D6)
    ApiClient.assertProblem(api.send("POST", path(), token), 401);

    seedAccount(rita, "Rita Participante");
    jdbc.sql("UPDATE user_account SET status = 'Disabled' WHERE user_id = :id").param("id", rita).update();
    ApiClient.assertProblem(api.send("POST", path(), token), 403);

    jdbc.sql("UPDATE user_account SET status = 'DeletionPending' WHERE user_id = :id").param("id", rita).update();
    ApiClient.assertProblem(api.send("POST", path(), token), 401);

    jdbc.sql("UPDATE user_account SET status = 'Active' WHERE user_id = :id").param("id", rita).update();
    jdbc.sql("INSERT INTO user_tombstone(user_id, deleted_at) VALUES (:id, now())").param("id", rita).update();
    ApiClient.assertProblem(api.send("POST", path(), token), 401);

    assertThat(memberships()).isZero();
    assertThat(published()).isZero();
  }

  @Test
  @DisplayName("US-12 AC-1 the join needs a token")
  void theJoinNeedsAToken() {
    assertThat(new ApiClient(port).send("POST", path(), null).statusCode()).isEqualTo(401);
  }
}
