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
import pt.isep.labdsof.reports.application.HelpRequestService;
import pt.isep.labdsof.reports.application.HelpRequests;
import pt.isep.labdsof.reports.application.LocalCopyService;
import pt.isep.labdsof.reports.domain.Actor;
import pt.isep.labdsof.reports.domain.Category;
import pt.isep.labdsof.reports.domain.Membership;
import pt.isep.labdsof.reports.domain.Membership.Role;
import pt.isep.labdsof.reports.domain.Permission;
import pt.isep.labdsof.reports.domain.Priority;
import pt.isep.labdsof.reports.domain.Team;
import pt.isep.labdsof.reports.domain.TriageSuggestion;

/**
 * US-29: the control room routes a request to a team over HTTP, on the real database: the field task, the history,
 * the refusals, the races with another route and with the team's deletion, and the security matrix (spec US-29 §5).
 */
class RouteRequestIT extends IntegrationTest {

  @Autowired HelpRequests helpRequests;
  @Autowired HelpRequestService service;
  @Autowired LocalCopyService localCopies;
  @Autowired TransactionTemplate transactions;

  final UUID event = UUID.randomUUID();
  final UUID controlRoom = UUID.randomUUID();
  final UUID nursing = UUID.randomUUID();
  final UUID security = UUID.randomUUID();
  final String participant = "participant-" + UUID.randomUUID();
  final String operator = "operator-" + UUID.randomUUID();
  final String colleague = "colleague-" + UUID.randomUUID();
  final String nurse = "nurse-" + UUID.randomUUID();

  static Team team(UUID teamId, UUID eventId, String name, Category category, boolean deleted, long version,
      Permission... permissions) {
    return new Team(teamId, eventId, name,
        Stream.of(permissions).map(p -> new Team.TeamPermission(p, Set.of())).toList(), Set.of(category), deleted,
        version);
  }

  @BeforeEach
  void teams() {
    copies.upsertIfNewer(team(controlRoom, event, "Control room", Category.Other, false, 1,
        Permission.HELP_REQUESTS_TRIAGE));
    copies.upsertIfNewer(team(nursing, event, "Nursing", Category.Health, false, 1,
        Permission.HELP_REQUESTS_RESPOND));
    copies.upsertIfNewer(team(security, event, "Security", Category.Safety, false, 1,
        Permission.HELP_REQUESTS_RESPOND));
    member(event, participant, Role.RegularUser);
    member(event, operator, Role.RegularUser, controlRoom);
    member(event, colleague, Role.RegularUser, controlRoom);
    member(event, nurse, Role.RegularUser, nursing);
  }

  String requests() {
    return "/api/v1/events/" + event + "/help-requests";
  }

  String submit() throws Exception {
    var created = post(requests(), JWKS.valid(participant), submitBody(UUID.randomUUID(), "Friend fainted"));
    assertThat(created.statusCode()).isEqualTo(201);
    return JsonPath.read(created.body(), "$.id");
  }

  HttpResponse<String> route(String id, String token, UUID teamId) throws Exception {
    return route(id, token, "{\"teamId\":\"" + teamId + "\"}");
  }

  HttpResponse<String> route(String id, String token, String body) throws Exception {
    return post(requests() + "/" + id + "/route", token, body);
  }

  List<Map<String, Object>> tasks(String id) {
    return jdbc.sql("""
        SELECT event_id, team_id, purpose, status, accepted_by, dispatched_at, updated_at, version FROM field_task
        WHERE help_request_id = :id ORDER BY dispatched_at, status DESC
        """).param("id", UUID.fromString(id)).query().listOfRows();
  }

  List<String> liveTasks(String id) {
    return jdbc.sql("""
        SELECT team_id::text FROM field_task WHERE help_request_id = :id AND status IN ('Open', 'Accepted')
        """).param("id", UUID.fromString(id)).query(String.class).list();
  }

  List<Map<String, Object>> rows(String id) {
    return jdbc.sql("SELECT field, old_value, new_value, actor_user_id, actor_name FROM help_request_change"
        + " WHERE request_id = :id ORDER BY id").param("id", UUID.fromString(id)).query().listOfRows();
  }

  Map<String, Object> request(String id) {
    return jdbc.sql("SELECT status, routed_team_id, version FROM help_request WHERE id = :id")
        .param("id", UUID.fromString(id)).query().singleRow();
  }

  void status(String id, String status) {
    jdbc.sql("UPDATE help_request SET status = :status WHERE id = :id").param("status", status)
        .param("id", UUID.fromString(id)).update();
  }

  @Test
  @DisplayName("US-29 AC-1 the team of the request's category is proposed and the request can be routed to it")
  void defaultTeamProposed() throws Exception {
    var id = submit();
    var request = helpRequests.find(event, UUID.fromString(id)).orElseThrow();
    assertThat(helpRequests.saveTriage(request.withTriage(
        TriageSuggestion.suggested(Category.Health, Priority.High, "Desmaio relatado"), Instant.now()))).isTrue();

    var opened = get(requests() + "/" + id, JWKS.valid(operator));
    String proposed = JsonPath.read(opened.body(), "$.defaultTeamId");
    assertThat(proposed).isEqualTo(nursing.toString());

    var routed = route(id, JWKS.valid(operator), UUID.fromString(proposed));
    assertThat(routed.statusCode()).as(routed.body()).isEqualTo(200);
    assertThat(JsonPath.<String>read(routed.body(), "$.routedTeamId")).isEqualTo(proposed);
    assertThat(JsonPath.<String>read(routed.body(), "$.defaultTeamId")).isEqualTo(proposed);
  }

  @Test
  @DisplayName("US-29 AC-2 the control room routes a request, a task is dispatched and the routing is recorded")
  void route() throws Exception {
    // No category yet, and a team that handles another one: routing by hand (D1)
    var id = submit();
    var before = Instant.now();

    var response = route(id, JWKS.named(operator, "Miguel Sousa"), security);

    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    assertThat(JsonPath.<String>read(response.body(), "$.routedTeamId")).isEqualTo(security.toString());
    assertThat(JsonPath.<String>read(response.body(), "$.status")).isEqualTo("Submitted");
    assertThat(JsonPath.<String>read(response.body(), "$.description")).isEqualTo("Friend fainted");
    assertThat(request(id)).containsEntry("status", "Submitted").containsEntry("routed_team_id", security)
        .containsEntry("version", 2L);
    var entry = new java.util.HashMap<String, Object>(Map.of("field", "routedTeamId", "new_value",
        security.toString(), "actor_user_id", operator, "actor_name", "Miguel Sousa"));
    entry.put("old_value", null);
    assertThat(rows(id)).containsExactly(entry);
    assertThat(tasks(id)).singleElement().satisfies(task -> {
      assertThat(task).containsEntry("event_id", event).containsEntry("team_id", security)
          .containsEntry("purpose", "Assist").containsEntry("status", "Open").containsEntry("accepted_by", null)
          .containsEntry("version", 1L);
      var dispatched = ((java.sql.Timestamp) task.get("dispatched_at")).toInstant();
      assertThat(dispatched).isBetween(before, Instant.now()).isEqualTo(
          ((java.sql.Timestamp) task.get("updated_at")).toInstant());
    });
    // The history over HTTP shows it with who and when
    var history = get(requests() + "/" + id + "/history", JWKS.valid(colleague)).body();
    assertThat(JsonPath.<Map<String, Object>>read(history, "$.items[0]")).containsEntry("field", "routedTeamId")
        .containsEntry("oldValue", null).containsEntry("newValue", security.toString())
        .containsEntry("actorUserId", operator).containsEntry("actorName", "Miguel Sousa");
    // A body field the contract does not have is refused, not taken (mass assignment)
    assertThat(route(id, JWKS.valid(operator), "{\"teamId\":\"" + nursing + "\",\"status\":\"Closed\"}")
        .statusCode()).isEqualTo(400);
    assertThat(request(id)).containsEntry("routed_team_id", security);
  }

  @Test
  @DisplayName("US-29 AC-2 a routed request is listed again with its team")
  void listedAgain() throws Exception {
    var id = submit();
    String cursor = JsonPath.read(get(requests(), JWKS.valid(operator)).body(), "$.serverTime");

    assertThat(route(id, JWKS.valid(operator), nursing).statusCode()).isEqualTo(200);

    var poll = get(requests() + "?updatedSince=" + cursor, JWKS.valid(operator)).body();
    assertThat(JsonPath.<List<String>>read(poll, "$.items[?(@.id == '" + id + "')].routedTeamId"))
        .containsExactly(nursing.toString());
  }

  @Test
  @DisplayName("US-29 AC-2 routing again moves the task to the new team")
  void routeAgain() throws Exception {
    var id = submit();
    assertThat(route(id, JWKS.valid(operator), nursing).statusCode()).isEqualTo(200);
    assertThat(post(requests() + "/" + id + "/acknowledge", JWKS.valid(operator), "").statusCode()).isEqualTo(200);

    var response = route(id, JWKS.valid(colleague), security);

    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    assertThat(request(id)).containsEntry("status", "Seen").containsEntry("routed_team_id", security)
        .containsEntry("version", 4L);
    assertThat(tasks(id)).extracting(t -> t.get("team_id") + ":" + t.get("status") + ":" + t.get("version"))
        .containsExactly(nursing + ":Cancelled:2", security + ":Open:1");
    assertThat(rows(id)).extracting(r -> r.get("field") + ":" + r.get("old_value") + ">" + r.get("new_value"))
        .containsExactly("routedTeamId:null>" + nursing, "status:Submitted>Seen",
            "routedTeamId:" + nursing + ">" + security);
    // The same team again: nothing written
    assertThat(route(id, JWKS.valid(operator), security).statusCode()).isEqualTo(200);
    assertThat(request(id)).containsEntry("version", 4L);
    assertThat(tasks(id)).hasSize(2);
    assertThat(rows(id)).hasSize(3);
  }

  @Test
  @DisplayName("US-29 AC-2 a request that is no longer open to routing is a conflict")
  void conflict() throws Exception {
    var id = submit();
    for (var state : List.of("OnTheWay", "Resolved", "Closed")) {
      status(id, state);
      var response = route(id, JWKS.valid(operator), nursing);
      assertThat(response.statusCode()).as(state).isEqualTo(409);
      assertThat(JsonPath.<String>read(response.body(), "$.detail")).isEqualTo("The request can no longer be routed");
    }
    assertThat(tasks(id)).isEmpty();
    assertThat(rows(id)).isEmpty();
    assertThat(request(id)).containsEntry("version", 1L);
  }

  @Test
  @DisplayName("US-29 AC-3 a team without HelpRequests.Respond cannot take requests")
  void notResponding() throws Exception {
    var id = submit();
    var bare = UUID.randomUUID();
    copies.upsertIfNewer(team(bare, event, "Stewards", Category.Health, false, 1));

    for (var team : List.of(controlRoom, bare)) {
      var response = route(id, JWKS.valid(operator), team);
      assertThat(response.statusCode()).isEqualTo(422);
      assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/problem+json");
      assertThat(JsonPath.<String>read(response.body(), "$.detail")).isEqualTo("The team cannot take help requests");
    }
    assertThat(tasks(id)).isEmpty();
    assertThat(rows(id)).isEmpty();
    assertThat(request(id)).containsEntry("routed_team_id", null).containsEntry("version", 1L);
  }

  @Test
  @DisplayName("US-29 AC-3 an unknown, deleted or other event's team gets the same 422")
  void unusableTeams() throws Exception {
    var id = submit();
    var deleted = UUID.randomUUID();
    copies.upsertIfNewer(team(deleted, event, "Old nursing", Category.Health, true, 1,
        Permission.HELP_REQUESTS_RESPOND));
    var elsewhere = UUID.randomUUID();
    copies.upsertIfNewer(team(elsewhere, UUID.randomUUID(), "Nursing elsewhere", Category.Health, false, 1,
        Permission.HELP_REQUESTS_RESPOND));
    var expected = route(id, JWKS.valid(operator), controlRoom).body().replaceAll(ChangeHistoryIT.INSTANCE, "");

    for (var team : List.of(UUID.randomUUID(), deleted, elsewhere)) {
      var response = route(id, JWKS.valid(operator), team);
      assertThat(response.statusCode()).isEqualTo(422);
      assertThat(response.body().replaceAll(ChangeHistoryIT.INSTANCE, "")).isEqualTo(expected);
    }
    // The team is checked before the status (D8)
    status(id, "Closed");
    assertThat(route(id, JWKS.valid(operator), elsewhere).statusCode()).isEqualTo(422);
    assertThat(tasks(id)).isEmpty();
    assertThat(rows(id)).isEmpty();
  }

  long waitingOn(String pattern) {
    return jdbc.sql("SELECT count(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock' AND query LIKE :pattern")
        .param("pattern", pattern).query(Long.class).single();
  }

  /**
   * Runs {@code hold} in a transaction, starts the calls once it holds its locks, lets it commit once {@code waiting}
   * calls wait on {@code pattern}; the status codes of the HTTP calls, in their order.
   */
  List<Integer> whileHeld(Runnable hold, String pattern, List<Callable<?>> calls) throws Exception {
    var locked = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(calls.size() + 1)) {
      var holder = pool.submit(() -> transactions.executeWithoutResult(status -> {
        hold.run();
        locked.countDown();
        try {
          release.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }));
      assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
      var running = new ArrayList<Future<?>>();
      for (var call : calls) {
        running.add(pool.submit(call));
      }
      var deadline = Instant.now().plusSeconds(10);
      while (waitingOn(pattern) < calls.size() && Instant.now().isBefore(deadline)) {
        Thread.onSpinWait();
      }
      assertThat(waitingOn(pattern)).as("calls waiting on " + pattern).isEqualTo(calls.size());
      release.countDown();
      holder.get(10, TimeUnit.SECONDS);
      var codes = new ArrayList<Integer>();
      for (var call : running) {
        if (call.get(10, TimeUnit.SECONDS) instanceof HttpResponse<?> response) {
          codes.add(response.statusCode());
        }
      }
      return codes;
    }
  }

  @Test
  @DisplayName("US-29 AC-2 two routes at the same time leave one live task")
  void concurrentRoutes() throws Exception {
    var id = submit();
    var codes = whileHeld(() -> helpRequests.findForUpdate(event, UUID.fromString(id)),
        "%triage_suggestion%FOR UPDATE%", List.of(() -> route(id, JWKS.valid(operator), nursing),
            () -> route(id, JWKS.valid(colleague), security)));

    assertThat(codes).containsExactly(200, 200);
    var routedTo = request(id).get("routed_team_id").toString();
    assertThat(liveTasks(id)).containsExactly(routedTo);
    assertThat(tasks(id)).hasSize(2);
    assertThat(rows(id)).hasSize(2).last().satisfies(row -> assertThat(row).containsEntry("new_value", routedTo));
    assertThat(request(id)).containsEntry("version", 3L);
  }

  Team deletedNursing() {
    return team(nursing, event, "Nursing", Category.Health, true, 2, Permission.HELP_REQUESTS_RESPOND);
  }

  @Test
  @DisplayName("US-29 AC-2 a route never leaves a task with a team deleted at the same time")
  void routeAndDeletionTogether() throws Exception {
    // The deletion first: the route waits on the team's copy, then reads it deleted
    var first = submit();
    var codes = whileHeld(() -> localCopies.apply(UUID.randomUUID(), deletedNursing()),
        "%team_copy%FOR SHARE%", List.of(() -> route(first, JWKS.valid(operator), nursing)));
    assertThat(codes).containsExactly(422);
    assertThat(liveTasks(first)).isEmpty();

    // The route first: the deletion waits for it, then cancels the task it dispatched
    var other = UUID.randomUUID();
    copies.upsertIfNewer(team(other, event, "Medics", Category.Health, false, 1, Permission.HELP_REQUESTS_RESPOND));
    var second = submit();
    whileHeld(() -> service.route(event, new Actor(operator, null), UUID.fromString(second), other),
        "%INSERT INTO team_copy%", List.of(() -> localCopies.apply(UUID.randomUUID(),
            team(other, event, "Medics", Category.Health, true, 2, Permission.HELP_REQUESTS_RESPOND))));
    assertThat(liveTasks(second)).isEmpty();
    assertThat(request(second)).containsEntry("routed_team_id", null).containsEntry("status", "Submitted");
  }

  @Test
  @DisplayName("US-29 AC-2 a deleted team's requests go back to the control room")
  void deletedTeam() throws Exception {
    var seen = submit();
    var onTheWay = submit();
    var resolved = submit();
    var elsewhere = submit();
    for (var id : List.of(seen, onTheWay, resolved)) {
      assertThat(route(id, JWKS.valid(operator), nursing).statusCode()).isEqualTo(200);
    }
    assertThat(route(elsewhere, JWKS.valid(operator), security).statusCode()).isEqualTo(200);
    status(seen, "Seen");
    // What US-32 and US-33 will write: the task accepted, then resolved
    status(onTheWay, "OnTheWay");
    status(resolved, "Resolved");
    jdbc.sql("UPDATE field_task SET status = 'Accepted', accepted_by = :nurse WHERE help_request_id = :id")
        .param("nurse", nurse).param("id", UUID.fromString(onTheWay)).update();
    jdbc.sql("UPDATE field_task SET status = 'Resolved' WHERE help_request_id = :id")
        .param("id", UUID.fromString(resolved)).update();
    String cursor = JsonPath.read(get(requests(), JWKS.valid(operator)).body(), "$.serverTime");

    localCopies.apply(UUID.randomUUID(), deletedNursing());

    assertThat(request(seen)).containsEntry("status", "Seen").containsEntry("routed_team_id", null)
        .containsEntry("version", 3L);
    assertThat(request(onTheWay)).containsEntry("status", "Seen").containsEntry("routed_team_id", null);
    assertThat(request(resolved)).containsEntry("status", "Resolved").containsEntry("routed_team_id", nursing);
    assertThat(request(elsewhere)).containsEntry("routed_team_id", security);
    assertThat(liveTasks(seen)).isEmpty();
    assertThat(liveTasks(onTheWay)).isEmpty();
    assertThat(tasks(resolved)).singleElement().satisfies(t -> assertThat(t).containsEntry("status", "Resolved"));
    assertThat(liveTasks(elsewhere)).containsExactly(security.toString());
    // No history entry for a system change (US-28 D6): only the members' routes
    assertThat(rows(seen)).extracting(r -> r.get("field")).containsExactly("routedTeamId");
    // Back in the control room's live list, without a team
    var poll = get(requests() + "?updatedSince=" + cursor, JWKS.valid(operator)).body();
    assertThat(JsonPath.<List<Object>>read(poll, "$.items[?(@.id == '" + onTheWay + "')].routedTeamId"))
        .containsExactly((Object) null);
  }

  @Test
  @DisplayName("US-29 AC-2 a member without HelpRequests.Triage gets 403")
  void forbidden() throws Exception {
    var id = submit();
    for (var user : List.of(participant, nurse)) {
      assertThat(route(id, JWKS.valid(user), nursing).statusCode()).isEqualTo(403);
    }
    assertThat(tasks(id)).isEmpty();
    assertThat(rows(id)).isEmpty();
  }

  @Test
  @DisplayName("US-29 AC-2 another event's member or request gets 404 without revealing it")
  void notFound() throws Exception {
    var removed = "removed-" + UUID.randomUUID();
    copies.upsertIfNewer(new Membership(event, removed, Role.EventAdmin, Membership.Status.Removed, Set.of(), 1));
    var otherEvent = UUID.randomUUID();
    var elsewhere = "elsewhere-" + UUID.randomUUID();
    member(otherEvent, elsewhere, Role.EventAdmin);
    var id = submit();
    var reference = post("/api/v1/events/" + UUID.randomUUID() + "/help-requests/" + id + "/route",
        JWKS.valid(operator), "{\"teamId\":\"" + nursing + "\"}");
    assertThat(reference.statusCode()).isEqualTo(404);
    var expected = reference.body().replaceAll(ChangeHistoryIT.INSTANCE, "");

    for (var token : List.of(JWKS.valid("nobody-" + UUID.randomUUID()), JWKS.valid(removed), JWKS.valid(elsewhere),
        JWKS.withoutSubject())) {
      var response = route(id, token, nursing);
      assertThat(response.statusCode()).isEqualTo(404);
      assertThat(response.body().replaceAll(ChangeHistoryIT.INSTANCE, "")).isEqualTo(expected);
    }
    // The other event's EventAdmin through their own event, and an unknown request even with an unusable team
    var other = post("/api/v1/events/" + otherEvent + "/help-requests/" + id + "/route", JWKS.valid(elsewhere),
        "{\"teamId\":\"" + nursing + "\"}");
    var unknown = route(UUID.randomUUID().toString(), JWKS.valid(operator), controlRoom);
    for (var response : List.of(other, unknown)) {
      assertThat(response.statusCode()).isEqualTo(404);
      assertThat(response.body().replaceAll(ChangeHistoryIT.INSTANCE, "")).isEqualTo(expected);
    }
    assertThat(tasks(id)).isEmpty();
    assertThat(rows(id)).isEmpty();
  }

  static Stream<String> badTokens() {
    return ChangeHistoryIT.badTokens();
  }

  @ParameterizedTest
  @MethodSource("badTokens")
  @DisplayName("US-29 AC-2 every bad token gets 401")
  void badToken(String token) throws Exception {
    var id = submit();
    assertThat(route(id, token, nursing).statusCode()).isEqualTo(401);
    assertThat(tasks(id)).isEmpty();
  }

  @Test
  @DisplayName("US-29 AC-2 a malformed ID or body is rejected")
  void malformed() throws Exception {
    var id = submit();
    var token = JWKS.valid(operator);
    assertThat(route("not-a-uuid", token, nursing).statusCode()).isEqualTo(400);
    assertThat(post("/api/v1/events/not-a-uuid/help-requests/" + id + "/route", token,
        "{\"teamId\":\"" + nursing + "\"}").statusCode()).isEqualTo(400);
    for (var body : List.of("", "{}", "{\"teamId\":null}", "{\"teamId\":\"not-a-uuid\"}", "{\"teamId\":42}",
        "not json", "[]", "{\"teamId\":\"" + nursing + "\",\"routedTeamId\":\"" + security + "\"}")) {
      assertThat(route(id, token, body).statusCode()).as(body).isEqualTo(400);
    }
    assertThat(tasks(id)).isEmpty();
    assertThat(rows(id)).isEmpty();
  }
}
