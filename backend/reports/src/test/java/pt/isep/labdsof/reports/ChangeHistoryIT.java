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
import java.util.concurrent.Callable;
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
 * US-28: the control room acknowledges a request and reads its change history over HTTP, on the real database, with
 * the security matrix of both endpoints (spec US-28 §5).
 */
class ChangeHistoryIT extends IntegrationTest {

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

  HttpResponse<String> acknowledge(String id, String token) throws Exception {
    return post(requests() + "/" + id + "/acknowledge", token, "");
  }

  HttpResponse<String> history(String id, String token) throws Exception {
    return get(requests() + "/" + id + "/history", token);
  }

  List<Map<String, Object>> rows(String id) {
    return jdbc.sql("SELECT field, old_value, new_value, actor_user_id, actor_name FROM help_request_change"
        + " WHERE request_id = :id ORDER BY id").param("id", UUID.fromString(id)).query().listOfRows();
  }

  long version(String id) {
    return jdbc.sql("SELECT version FROM help_request WHERE id = :id").param("id", UUID.fromString(id))
        .query(Long.class).single();
  }

  Instant triageUpdatedAt(String id) {
    return jdbc.sql("SELECT updated_at FROM triage_suggestion WHERE request_id = :id")
        .param("id", UUID.fromString(id)).query(java.sql.Timestamp.class).single().toInstant();
  }

  void status(String id, String status) {
    jdbc.sql("UPDATE help_request SET status = :status WHERE id = :id").param("status", status)
        .param("id", UUID.fromString(id)).update();
  }

  @Test
  @DisplayName("US-28 AC-1 the control room acknowledges a request and it reads Seen")
  void acknowledge() throws Exception {
    var id = submit();
    String cursor = JsonPath.read(get(requests(), JWKS.valid(operator)).body(), "$.serverTime");
    var triageWritten = triageUpdatedAt(id);

    var response = acknowledge(id, JWKS.named(operator, "Miguel Sousa"));

    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    assertThat(JsonPath.<String>read(response.body(), "$.status")).isEqualTo("Seen");
    assertThat(JsonPath.<String>read(response.body(), "$.triage.state")).isEqualTo("Pending");
    assertThat(version(id)).isEqualTo(2);
    // A status change leaves the triage row alone, its time included (G8 review)
    assertThat(triageUpdatedAt(id)).isEqualTo(triageWritten);
    assertThat(rows(id)).containsExactly(Map.of("field", "status", "old_value", "Submitted", "new_value", "Seen",
        "actor_user_id", operator, "actor_name", "Miguel Sousa"));
    // The next poll of the live list shows it, and the participant reads Seen
    var poll = get(requests() + "?updatedSince=" + cursor, JWKS.valid(operator)).body();
    assertThat(JsonPath.<List<String>>read(poll, "$.items[?(@.id == '" + id + "')].status")).containsExactly("Seen");
    var own = get(requests() + "/" + id, JWKS.valid(participant)).body();
    assertThat(JsonPath.<String>read(own, "$.status")).isEqualTo("Seen");
  }

  @Test
  @DisplayName("US-28 AC-1 acknowledging a request that is not Submitted is a conflict")
  void conflict() throws Exception {
    var id = submit();
    assertThat(acknowledge(id, JWKS.valid(operator)).statusCode()).isEqualTo(200);
    for (var status : List.of("Seen", "OnTheWay", "Resolved", "Closed")) {
      status(id, status);
      var response = acknowledge(id, JWKS.valid(colleague));
      assertThat(response.statusCode()).as(status).isEqualTo(409);
      assertThat(JsonPath.<String>read(response.body(), "$.detail")).isEqualTo("The request is no longer Submitted");
    }
    assertThat(rows(id)).hasSize(1);
    assertThat(version(id)).isEqualTo(2);
  }

  long waitingOnLocks() {
    // Only the members' row locks on this table: another session waiting on a lock must not count (G8 review)
    return jdbc.sql("""
        SELECT count(*) FROM pg_stat_activity
        WHERE wait_event_type = 'Lock' AND query LIKE 'SELECT 1 FROM triage_suggestion%FOR UPDATE'
        """).query(Long.class).single();
  }

  /**
   * Runs the calls while a transaction holds the request's locks, releases them once every call waits on them, so
   * the calls are in flight together; the status codes, in the order of the calls.
   */
  List<Integer> together(String id, List<Callable<HttpResponse<String>>> calls) throws Exception {
    var locked = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(calls.size() + 1)) {
      var holder = pool.submit(() -> transactions.executeWithoutResult(status -> {
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
      for (var call : calls) {
        running.add(pool.submit(call));
      }
      var deadline = Instant.now().plusSeconds(10);
      while (waitingOnLocks() < calls.size() && Instant.now().isBefore(deadline)) {
        Thread.onSpinWait();
      }
      assertThat(waitingOnLocks()).isEqualTo(calls.size());
      release.countDown();
      holder.get(10, TimeUnit.SECONDS);
      var codes = new ArrayList<Integer>();
      for (var call : running) {
        codes.add(call.get(10, TimeUnit.SECONDS).statusCode());
      }
      return codes;
    }
  }

  @Test
  @DisplayName("US-28 AC-1 two acknowledges at the same time record one change")
  void concurrentAcknowledges() throws Exception {
    var id = submit();
    var codes = together(id, List.of(() -> acknowledge(id, JWKS.valid(operator)),
        () -> acknowledge(id, JWKS.valid(colleague))));
    assertThat(codes).containsExactlyInAnyOrder(200, 409);
    assertThat(rows(id)).hasSize(1);
    assertThat(version(id)).isEqualTo(2);
  }

  @Test
  @DisplayName("US-28 AC-1 an acknowledge and a change at the same time both apply")
  void acknowledgeAndChangeTogether() throws Exception {
    var id = submit();
    var codes = together(id, List.of(() -> acknowledge(id, JWKS.valid(operator)),
        () -> patch(requests() + "/" + id, JWKS.valid(colleague), "{\"priority\":\"High\"}")));
    assertThat(codes).containsExactly(200, 200);
    assertThat(version(id)).isEqualTo(3);
    assertThat(rows(id)).extracting(r -> r.get("field")).containsExactlyInAnyOrder("status", "priority", "triage");
    var read = get(requests() + "/" + id, JWKS.valid(operator)).body();
    assertThat(JsonPath.<String>read(read, "$.status")).isEqualTo("Seen");
    assertThat(JsonPath.<String>read(read, "$.priority")).isEqualTo("High");
  }

  @Test
  @DisplayName("US-28 AC-2 the history lists every change oldest first with who and when")
  void fullHistory() throws Exception {
    var id = submit();
    var request = helpRequests.find(event, UUID.fromString(id)).orElseThrow();
    assertThat(helpRequests.saveTriage(request.withTriage(
        TriageSuggestion.suggested(Category.Health, Priority.High, "Desmaio relatado"), Instant.now()))).isTrue();
    var markup = "<b>Ana</b>'); DROP TABLE help_request_change;--";
    var before = Instant.now();

    assertThat(post(requests() + "/" + id + "/accept-suggestion", JWKS.named(operator, "Miguel Sousa"), "")
        .statusCode()).isEqualTo(200);
    assertThat(patch(requests() + "/" + id, JWKS.named(colleague, markup), "{\"priority\":\"Critical\"}")
        .statusCode()).isEqualTo(200);
    assertThat(acknowledge(id, JWKS.named(operator, "Miguel Sousa")).statusCode()).isEqualTo(200);

    var response = history(id, JWKS.valid(colleague));
    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    List<Map<String, Object>> items = JsonPath.read(response.body(), "$.items");
    assertThat(items).allSatisfy(item -> assertThat(item.keySet())
        .containsExactlyInAnyOrder("field", "oldValue", "newValue", "actorUserId", "actorName", "changedAt"));
    assertThat(items).extracting(i -> i.get("field") + ":" + i.get("oldValue") + ">" + i.get("newValue")
        + " by " + i.get("actorUserId") + " (" + i.get("actorName") + ")").containsExactly(
            "category:null>Health by " + operator + " (Miguel Sousa)",
            "priority:null>High by " + operator + " (Miguel Sousa)",
            "triage:Suggested>Accepted by " + operator + " (Miguel Sousa)",
            "priority:High>Critical by " + colleague + " (" + markup + ")",
            "triage:Accepted>Changed by " + colleague + " (" + markup + ")",
            "status:Submitted>Seen by " + operator + " (Miguel Sousa)");
    var times = items.stream().map(i -> Instant.parse((String) i.get("changedAt"))).toList();
    assertThat(times).isSorted().allSatisfy(t -> assertThat(t).isBetween(before, Instant.now()));
    assertThat(jdbc.sql("SELECT count(*) FROM help_request_change").query(Long.class).single()).isPositive();
  }

  @Test
  @DisplayName("US-28 AC-2 a request nobody changed has an empty history")
  void emptyHistory() throws Exception {
    var response = history(submit(), JWKS.valid(operator));
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(JsonPath.<List<Object>>read(response.body(), "$.items")).isEmpty();
  }

  @Test
  @DisplayName("US-28 AC-1 a token without a name records the change without one")
  void withoutName() throws Exception {
    var id = submit();
    assertThat(acknowledge(id, JWKS.valid(operator)).statusCode()).isEqualTo(200);
    var body = history(id, JWKS.valid(operator)).body();
    assertThat(JsonPath.<Map<String, Object>>read(body, "$.items[0]")).containsEntry("actorUserId", operator)
        .containsEntry("actorName", null);
  }

  @Test
  @DisplayName("US-28 AC-1 a member without HelpRequests.Triage gets 403")
  void forbidden() throws Exception {
    var id = submit();
    assertThat(acknowledge(id, JWKS.valid(participant)).statusCode()).isEqualTo(403);
    assertThat(history(id, JWKS.valid(participant)).statusCode()).isEqualTo(403);
    assertThat(rows(id)).isEmpty();
    assertThat(version(id)).isEqualTo(1);
  }

  /** The problem's instance is the path asked for; the rest of a 404 body must be the same in every case. */
  static final String INSTANCE = "\"instance\":\"[^\"]*\"";

  @Test
  @DisplayName("US-28 AC-1 another event's member or request gets 404 without revealing it")
  void notFound() throws Exception {
    var removed = "removed-" + UUID.randomUUID();
    copies.upsertIfNewer(new Membership(event, removed, Role.EventAdmin, Membership.Status.Removed, Set.of(), 1));
    var otherEvent = UUID.randomUUID();
    var elsewhere = "elsewhere-" + UUID.randomUUID();
    member(otherEvent, elsewhere, Role.EventAdmin);
    var id = submit();
    var reference = post("/api/v1/events/" + UUID.randomUUID() + "/help-requests/" + id + "/acknowledge",
        JWKS.valid(operator), "");
    assertThat(reference.statusCode()).isEqualTo(404);
    var expected = reference.body().replaceAll(INSTANCE, "");

    // A valid token without a sub names nobody: a non-member, not a server error (G8 review)
    for (var token : List.of(JWKS.valid("nobody-" + UUID.randomUUID()), JWKS.valid(removed), JWKS.valid(elsewhere),
        JWKS.withoutSubject())) {
      for (var response : List.of(acknowledge(id, token), history(id, token),
          post(requests() + "/" + id + "/accept-suggestion", token, ""),
          patch(requests() + "/" + id, token, "{\"priority\":\"High\"}"))) {
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body().replaceAll(INSTANCE, "")).isEqualTo(expected);
      }
    }
    // The other event's EventAdmin, asking through their own event, and an unknown request
    var otherPath = "/api/v1/events/" + otherEvent + "/help-requests/";
    for (var path : List.of(otherPath + id, requests() + "/" + UUID.randomUUID())) {
      var token = path.startsWith(otherPath) ? JWKS.valid(elsewhere) : JWKS.valid(operator);
      for (var response : List.of(post(path + "/acknowledge", token, ""), get(path + "/history", token))) {
        assertThat(response.statusCode()).as(path).isEqualTo(404);
        assertThat(response.body().replaceAll(INSTANCE, "")).isEqualTo(expected);
      }
    }
    assertThat(rows(id)).isEmpty();
    assertThat(version(id)).isEqualTo(1);
  }

  static Stream<String> badTokens() {
    return Stream.of(null, JWKS.expired("operator"), JWKS.wrongSignature("operator"),
        JWKS.wrongIssuer("operator"), JWKS.wrongAudience("operator"), "not-a-jwt");
  }

  @ParameterizedTest
  @MethodSource("badTokens")
  @DisplayName("US-28 AC-1 every bad token gets 401")
  void badToken(String token) throws Exception {
    var id = submit();
    assertThat(acknowledge(id, token).statusCode()).isEqualTo(401);
    assertThat(history(id, token).statusCode()).isEqualTo(401);
    assertThat(rows(id)).isEmpty();
  }

  @Test
  @DisplayName("US-28 AC-1 a malformed ID is rejected")
  void malformedIds() throws Exception {
    var id = submit();
    assertThat(acknowledge("not-a-uuid", JWKS.valid(operator)).statusCode()).isEqualTo(400);
    assertThat(history("not-a-uuid", JWKS.valid(operator)).statusCode()).isEqualTo(400);
    var otherEvent = "/api/v1/events/not-a-uuid/help-requests/" + id;
    assertThat(post(otherEvent + "/acknowledge", JWKS.valid(operator), "").statusCode()).isEqualTo(400);
    assertThat(get(otherEvent + "/history", JWKS.valid(operator)).statusCode()).isEqualTo(400);
    assertThat(rows(id)).isEmpty();
  }
}
