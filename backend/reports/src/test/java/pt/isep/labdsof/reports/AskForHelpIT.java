package pt.isep.labdsof.reports;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import pt.isep.labdsof.reports.domain.Category;
import pt.isep.labdsof.reports.domain.Membership.Role;
import pt.isep.labdsof.reports.domain.Permission;
import pt.isep.labdsof.reports.domain.Team;

/**
 * US-23 (#71): a participant asks for help with a description and a point, who sees the location, and the limit of
 * 5 help requests per account in 10 minutes (L-2, in Reports since 24 November 2026), over HTTP on the real database
 * (spec US-23 §5). Every test uses its own accounts, so the counts of one never reach another. The production values
 * of the limit are set here, as the shared context lifts it; DocumentationIT checks application.properties has them.
 */
@TestPropertySource(properties = {"farol.help-requests.limit=5", "farol.help-requests.limit-window=10m"})
class AskForHelpIT extends IntegrationTest {

  final UUID event = UUID.randomUUID();
  final UUID controlRoom = UUID.randomUUID();
  final UUID responders = UUID.randomUUID();
  final String participant = "participant-" + UUID.randomUUID();
  final String operator = "operator-" + UUID.randomUUID();
  final String admin = "admin-" + UUID.randomUUID();
  final String responder = "responder-" + UUID.randomUUID();

  @BeforeEach
  void event() {
    copies.upsertIfNewer(new Team(controlRoom, event, "Control room",
        List.of(new Team.TeamPermission(Permission.HELP_REQUESTS_TRIAGE, Set.of())), Set.of(), false, 1));
    copies.upsertIfNewer(new Team(responders, event, "Nursing",
        List.of(new Team.TeamPermission(Permission.HELP_REQUESTS_RESPOND, Set.of())), Set.of(Category.Health),
        false, 1));
    member(event, participant, Role.RegularUser);
    member(event, operator, Role.RegularUser, controlRoom);
    member(event, admin, Role.EventAdmin);
    member(event, responder, Role.RegularUser, responders);
  }

  static String requests(UUID eventId) {
    return "/api/v1/events/" + eventId + "/help-requests";
  }

  HttpResponse<String> submit(UUID eventId, String user, UUID clientRequestId) throws Exception {
    return post(requests(eventId), JWKS.valid(user), submitBody(clientRequestId, "Friend fainted"));
  }

  HttpResponse<String> submit(String user) throws Exception {
    return submit(event, user, UUID.randomUUID());
  }

  long saved(String user) {
    return jdbc.sql("SELECT count(*) FROM help_request WHERE requester_id = :user").param("user", user)
        .query(Long.class).single();
  }

  @Test
  @DisplayName("US-23 AC-1 a description and a point give a confirmation, and no category is asked for")
  void confirmation() throws Exception {
    var response = submit(participant);

    assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
    Map<String, Object> body = JsonPath.read(response.body(), "$");
    assertThat(body).containsOnlyKeys("id", "description", "status", "updatedAt")
        .containsEntry("status", "Submitted").containsEntry("description", "Friend fainted");
    assertThat(response.headers().firstValue("Location")).contains(requests(event) + "/" + body.get("id"));

    // 500 characters counted as code points, as the contract counts them
    var longest = post(requests(event), JWKS.valid(participant),
        submitBody(UUID.randomUUID(), "🚑".repeat(500)));
    assertThat(longest.statusCode()).as(longest.body()).isEqualTo(201);
  }

  @Test
  @DisplayName("US-23 AC-1 an empty or too long description, or no point, is refused and nothing is saved")
  void refused() throws Exception {
    var token = JWKS.valid(participant);
    for (var description : List.of("", "   ", "a".repeat(501))) {
      var response = post(requests(event), token, submitBody(UUID.randomUUID(), description));
      assertThat(response.statusCode()).as(description.length() + " characters").isEqualTo(400);
    }
    for (var json : List.of(
        "{\"clientRequestId\":\"" + UUID.randomUUID() + "\",\"description\":\"help\"}",
        "{\"clientRequestId\":\"" + UUID.randomUUID()
            + "\",\"location\":{\"mapVersion\":3,\"x\":1.5,\"y\":0.5},\"description\":\"help\"}")) {
      assertThat(post(requests(event), token, json).statusCode()).as(json).isEqualTo(400);
    }
    assertThat(saved(participant)).isZero();
  }

  @Test
  @DisplayName("US-23 AC-3 only the control room and the EventAdmin see the location")
  void location() throws Exception {
    String id = JsonPath.read(submit(participant).body(), "$.id");
    var one = requests(event) + "/" + id;

    for (var reader : List.of(operator, admin)) {
      var read = get(one, JWKS.valid(reader));
      assertThat(read.statusCode()).as(reader).isEqualTo(200);
      assertThat(JsonPath.<Map<String, Object>>read(read.body(), "$.location"))
          .containsEntry("mapVersion", 3).containsEntry("x", 0.42).containsEntry("y", 0.77);
      var list = get(requests(event), JWKS.valid(reader)).body();
      assertThat(JsonPath.<List<Object>>read(list, "$.items[?(@.id == '" + id + "')].location")).hasSize(1);
    }
    var own = get(one, JWKS.valid(participant));
    assertThat(own.statusCode()).isEqualTo(200);
    assertThat(JsonPath.<Map<String, Object>>read(own.body(), "$")).doesNotContainKey("location");
    // A responding team that the request is not routed to (routing is US-29), and another participant
    var other = "participant-" + UUID.randomUUID();
    member(event, other, Role.RegularUser);
    for (var reader : List.of(responder, other)) {
      var read = get(one, JWKS.valid(reader));
      assertThat(read.statusCode()).as(reader).isEqualTo(404);
      assertThat(read.body()).doesNotContain("0.42");
    }
    assertThat(get(requests(event), JWKS.valid(responder)).statusCode()).isEqualTo(403);
  }

  @Test
  @DisplayName("US-23 AC-4 the 6th help request in 10 minutes is refused with Retry-After and nothing is saved")
  void sixth() throws Exception {
    for (int i = 0; i < 5; i++) {
      assertThat(submit(participant).statusCode()).isEqualTo(201);
    }
    var outbox = jdbc.sql("SELECT count(*) FROM outbox").query(Long.class).single();

    var refused = submit(participant);

    assertThat(refused.statusCode()).as(refused.body()).isEqualTo(429);
    assertThat(refused.headers().firstValue("Content-Type")).hasValueSatisfying(
        type -> assertThat(type).startsWith("application/problem+json"));
    var retryAfter = Long.parseLong(refused.headers().firstValue("Retry-After").orElseThrow());
    assertThat(retryAfter).isBetween(590L, 600L);
    assertThat(JsonPath.<String>read(refused.body(), "$.detail")).contains(retryAfter + " seconds");
    assertThat(saved(participant)).isEqualTo(5);
    assertThat(jdbc.sql("SELECT count(*) FROM outbox").query(Long.class).single()).isEqualTo(outbox);
  }

  @Test
  @DisplayName("US-23 AC-4 requests in another event count, and another account is not limited")
  void acrossEvents() throws Exception {
    var other = UUID.randomUUID();
    member(other, participant, Role.RegularUser);
    for (int i = 0; i < 3; i++) {
      assertThat(submit(participant).statusCode()).isEqualTo(201);
    }
    for (int i = 0; i < 2; i++) {
      assertThat(submit(other, participant, UUID.randomUUID()).statusCode()).isEqualTo(201);
    }

    assertThat(submit(participant).statusCode()).isEqualTo(429);
    assertThat(submit(other, participant, UUID.randomUUID()).statusCode()).isEqualTo(429);
    var neighbour = "participant-" + UUID.randomUUID();
    member(event, neighbour, Role.RegularUser);
    assertThat(submit(neighbour).statusCode()).isEqualTo(201);
  }

  @Test
  @DisplayName("US-23 AC-4 a retry of a saved request is answered at the limit")
  void retryAtTheLimit() throws Exception {
    var first = UUID.randomUUID();
    String id = JsonPath.read(submit(event, participant, first).body(), "$.id");
    for (int i = 0; i < 4; i++) {
      submit(participant);
    }

    var retried = submit(event, participant, first);

    assertThat(retried.statusCode()).as(retried.body()).isEqualTo(200);
    assertThat(JsonPath.<String>read(retried.body(), "$.id")).isEqualTo(id);
    assertThat(saved(participant)).isEqualTo(5);
  }

  @Test
  @DisplayName("US-23 AC-4 two submissions at once with 4 saved give one 201 and one 429")
  void concurrent() throws Exception {
    for (int i = 0; i < 4; i++) {
      submit(participant);
    }
    var start = new CountDownLatch(1);
    Callable<Integer> one = () -> {
      start.await();
      return submit(participant).statusCode();
    };
    try (var pool = Executors.newFixedThreadPool(2)) {
      var a = pool.submit(one);
      var b = pool.submit(one);
      start.countDown();
      assertThat(List.of(a.get(), b.get())).containsExactlyInAnyOrder(201, 429);
    }
    assertThat(saved(participant)).isEqualTo(5);
  }
}
