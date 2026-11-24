package pt.isep.labdsof.reports;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import pt.isep.labdsof.reports.application.HelpRequests;
import pt.isep.labdsof.reports.domain.Category;
import pt.isep.labdsof.reports.domain.Membership;
import pt.isep.labdsof.reports.domain.Membership.Role;
import pt.isep.labdsof.reports.domain.Permission;
import pt.isep.labdsof.reports.domain.Priority;
import pt.isep.labdsof.reports.domain.Team;
import pt.isep.labdsof.reports.domain.TriageSuggestion;

/**
 * US-30: the control room closes a request over HTTP, on the real database: it leaves the active list, the participant
 * reads it Closed and the closing is in the history; the security matrix of the endpoint (spec US-30 §5).
 */
class CloseRequestIT extends IntegrationTest {

  @Autowired HelpRequests helpRequests;
  @Autowired TransactionTemplate transactions;

  final UUID event = UUID.randomUUID();
  final String participant = "participant-" + UUID.randomUUID();
  final String operator = "operator-" + UUID.randomUUID();
  final String colleague = "colleague-" + UUID.randomUUID();

  @BeforeEach
  void controlRoom() {
    var team = UUID.randomUUID();
    copies.upsertIfNewer(new Team(team, event, "Control room",
        List.of(new Team.TeamPermission(Permission.HELP_REQUESTS_TRIAGE, Set.of())), Set.of(Category.Health),
        false, 1));
    member(event, participant, Role.RegularUser);
    member(event, operator, Role.RegularUser, team);
    member(event, colleague, Role.RegularUser, team);
  }

  String requests() {
    return "/api/v1/events/" + event + "/help-requests";
  }

  String submit() throws Exception {
    var created = post(requests(), JWKS.valid(participant), submitBody(UUID.randomUUID(), "Friend fainted"));
    assertThat(created.statusCode()).isEqualTo(201);
    return JsonPath.read(created.body(), "$.id");
  }

  HttpResponse<String> close(String id, String token) throws Exception {
    return post(requests() + "/" + id + "/close", token, "");
  }

  List<Map<String, Object>> rows(String id) {
    return jdbc.sql("SELECT field, old_value, new_value, actor_user_id, actor_name FROM help_request_change"
        + " WHERE request_id = :id ORDER BY id").param("id", UUID.fromString(id)).query().listOfRows();
  }

  long version(String id) {
    return jdbc.sql("SELECT version FROM help_request WHERE id = :id").param("id", UUID.fromString(id))
        .query(Long.class).single();
  }

  String status(String id) {
    return jdbc.sql("SELECT status FROM help_request WHERE id = :id").param("id", UUID.fromString(id))
        .query(String.class).single();
  }

  @Test
  @DisplayName("US-30 AC-1 the control room closes a request and the closing is recorded")
  void close() throws Exception {
    var id = submit();
    assertThat(post(requests() + "/" + id + "/acknowledge", JWKS.valid(operator), "").statusCode()).isEqualTo(200);
    var before = Instant.now();

    // A body is ignored: the action takes none
    var response = post(requests() + "/" + id + "/close", JWKS.named(colleague, "Miguel Sousa"),
        "{\"status\":\"Seen\",\"priority\":\"Low\"}");

    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    assertThat(JsonPath.<String>read(response.body(), "$.status")).isEqualTo("Closed");
    assertThat(JsonPath.<String>read(response.body(), "$.triage.state")).isEqualTo("Pending");
    assertThat(JsonPath.<Object>read(response.body(), "$.priority")).isNull();
    assertThat(version(id)).isEqualTo(3);
    var history = get(requests() + "/" + id + "/history", JWKS.valid(operator)).body();
    List<Map<String, Object>> items = JsonPath.read(history, "$.items");
    assertThat(items).last().satisfies(last -> {
      assertThat(last).containsEntry("field", "status").containsEntry("oldValue", "Seen")
          .containsEntry("newValue", "Closed").containsEntry("actorUserId", colleague)
          .containsEntry("actorName", "Miguel Sousa");
      assertThat(Instant.parse((String) last.get("changedAt"))).isBetween(before, Instant.now());
    });
  }

  @Test
  @DisplayName("US-30 AC-1 a closed request leaves the active list")
  void leavesTheActiveList() throws Exception {
    var id = submit();
    var open = submit();
    String cursor = JsonPath.read(get(requests(), JWKS.valid(operator)).body(), "$.serverTime");

    assertThat(close(id, JWKS.valid(operator)).statusCode()).isEqualTo(200);

    // The next poll sends it once, Closed, so the page drops it; a first load no longer has it
    var poll = get(requests() + "?updatedSince=" + cursor, JWKS.valid(operator)).body();
    assertThat(JsonPath.<List<String>>read(poll, "$.items[?(@.id == '" + id + "')].status"))
        .containsExactly("Closed");
    var firstLoad = get(requests(), JWKS.valid(operator)).body();
    assertThat(JsonPath.<List<String>>read(firstLoad, "$.items[*].id")).contains(open).doesNotContain(id);
  }

  @Test
  @DisplayName("US-30 AC-1 the participant sees the request Closed")
  void participantSeesClosed() throws Exception {
    var id = submit();
    assertThat(close(id, JWKS.valid(operator)).statusCode()).isEqualTo(200);

    var own = get(requests() + "/" + id, JWKS.valid(participant)).body();
    assertThat(JsonPath.<String>read(own, "$.status")).isEqualTo("Closed");
    var mine = get(requests() + "?mine=true", JWKS.valid(participant)).body();
    assertThat(JsonPath.<List<String>>read(mine, "$.items[?(@.id == '" + id + "')].status"))
        .containsExactly("Closed");
  }

  @Test
  @DisplayName("US-30 AC-1 closing a closed request is a conflict")
  void conflict() throws Exception {
    var id = submit();
    assertThat(close(id, JWKS.valid(operator)).statusCode()).isEqualTo(200);

    var again = close(id, JWKS.valid(colleague));

    assertThat(again.statusCode()).isEqualTo(409);
    assertThat(JsonPath.<String>read(again.body(), "$.detail")).isEqualTo("The request is closed");
    assertThat(rows(id)).hasSize(1);
    assertThat(version(id)).isEqualTo(2);
  }

  @Test
  @DisplayName("US-30 AC-1 a closed request refuses the control room's changes")
  void closedIsFinal() throws Exception {
    var id = submit();
    var request = helpRequests.find(event, UUID.fromString(id)).orElseThrow();
    assertThat(helpRequests.saveTriage(request.withTriage(
        TriageSuggestion.suggested(Category.Health, Priority.High, "Desmaio relatado"), Instant.now()))).isTrue();
    assertThat(close(id, JWKS.valid(operator)).statusCode()).isEqualTo(200);

    for (var response : List.of(
        patch(requests() + "/" + id, JWKS.valid(colleague), "{\"priority\":\"Critical\"}"),
        post(requests() + "/" + id + "/accept-suggestion", JWKS.valid(colleague), ""),
        post(requests() + "/" + id + "/acknowledge", JWKS.valid(colleague), ""))) {
      assertThat(response.statusCode()).as(response.body()).isEqualTo(409);
    }
    assertThat(rows(id)).hasSize(1);
    assertThat(version(id)).isEqualTo(2);
    assertThat(status(id)).isEqualTo("Closed");
  }

  long waitingOnLocks() {
    return jdbc.sql("""
        SELECT count(*) FROM pg_stat_activity
        WHERE wait_event_type = 'Lock' AND query LIKE '%triage_suggestion%FOR UPDATE%'
        """).query(Long.class).single();
  }

  @Test
  @DisplayName("US-30 AC-1 two closes at the same time record one closing")
  void concurrentCloses() throws Exception {
    var id = submit();
    var locked = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(3)) {
      // A transaction holds the request's locks until both closes wait on them, so they are in flight together
      var holder = pool.submit(() -> transactions.executeWithoutResult(s -> {
        helpRequests.findForUpdate(event, UUID.fromString(id));
        locked.countDown();
        try {
          release.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }));
      assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
      var running = new ArrayList<Future<HttpResponse<String>>>();
      for (var user : List.of(operator, colleague)) {
        running.add(pool.submit(() -> close(id, JWKS.valid(user))));
      }
      var deadline = Instant.now().plusSeconds(10);
      while (waitingOnLocks() < 2 && Instant.now().isBefore(deadline)) {
        Thread.onSpinWait();
      }
      assertThat(waitingOnLocks()).as("closes waiting on the triage_suggestion row lock").isEqualTo(2);
      release.countDown();
      holder.get(10, TimeUnit.SECONDS);
      var codes = new ArrayList<Integer>();
      for (var call : running) {
        codes.add(call.get(10, TimeUnit.SECONDS).statusCode());
      }
      assertThat(codes).containsExactlyInAnyOrder(200, 409);
    }
    assertThat(rows(id)).hasSize(1);
    assertThat(version(id)).isEqualTo(2);
  }

  @Test
  @DisplayName("US-30 AC-1 a member without HelpRequests.Triage gets 403")
  void forbidden() throws Exception {
    var id = submit();
    assertThat(close(id, JWKS.valid(participant)).statusCode()).isEqualTo(403);
    assertThat(rows(id)).isEmpty();
    assertThat(status(id)).isEqualTo("Submitted");
  }

  /** The problem's instance is the path asked for; the rest of a 404 body must be the same in every case. */
  static final String INSTANCE = "\"instance\":\"[^\"]*\"";

  @Test
  @DisplayName("US-30 AC-1 another event's member or request gets 404 without revealing it")
  void notFound() throws Exception {
    var removed = "removed-" + UUID.randomUUID();
    copies.upsertIfNewer(new Membership(event, removed, Role.EventAdmin, Membership.Status.Removed, Set.of(), 1));
    var otherEvent = UUID.randomUUID();
    var elsewhere = "elsewhere-" + UUID.randomUUID();
    member(otherEvent, elsewhere, Role.EventAdmin);
    var id = submit();
    var reference = post("/api/v1/events/" + UUID.randomUUID() + "/help-requests/" + id + "/close",
        JWKS.valid(operator), "");
    assertThat(reference.statusCode()).isEqualTo(404);
    var expected = reference.body().replaceAll(INSTANCE, "");

    var responses = new ArrayList<HttpResponse<String>>();
    for (var token : List.of(JWKS.valid("nobody-" + UUID.randomUUID()), JWKS.valid(removed), JWKS.valid(elsewhere),
        JWKS.withoutSubject())) {
      responses.add(close(id, token));
    }
    // The other event's EventAdmin, asking through their own event, and an unknown request
    responses.add(post("/api/v1/events/" + otherEvent + "/help-requests/" + id + "/close", JWKS.valid(elsewhere),
        ""));
    responses.add(close(UUID.randomUUID().toString(), JWKS.valid(operator)));
    assertThat(responses).allSatisfy(response -> {
      assertThat(response.statusCode()).isEqualTo(404);
      assertThat(response.body().replaceAll(INSTANCE, "")).isEqualTo(expected);
    });
    assertThat(rows(id)).isEmpty();
    assertThat(status(id)).isEqualTo("Submitted");
  }

  static Stream<String> badTokens() {
    return Stream.of(null, JWKS.expired("operator"), JWKS.wrongSignature("operator"),
        JWKS.wrongIssuer("operator"), JWKS.wrongAudience("operator"), "not-a-jwt");
  }

  @ParameterizedTest
  @MethodSource("badTokens")
  @DisplayName("US-30 AC-1 every bad token gets 401")
  void badToken(String token) throws Exception {
    var id = submit();
    assertThat(close(id, token).statusCode()).isEqualTo(401);
    assertThat(rows(id)).isEmpty();
  }

  @Test
  @DisplayName("US-30 AC-1 a malformed ID is rejected")
  void malformedIds() throws Exception {
    var id = submit();
    assertThat(close("not-a-uuid", JWKS.valid(operator)).statusCode()).isEqualTo(400);
    assertThat(post("/api/v1/events/not-a-uuid/help-requests/" + id + "/close", JWKS.valid(operator), "")
        .statusCode()).isEqualTo(400);
    assertThat(rows(id)).isEmpty();
  }
}
