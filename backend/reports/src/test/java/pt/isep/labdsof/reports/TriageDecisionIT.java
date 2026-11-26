package pt.isep.labdsof.reports;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
import pt.isep.labdsof.reports.domain.Actor;
import pt.isep.labdsof.reports.domain.Category;
import pt.isep.labdsof.reports.domain.Membership.Role;
import pt.isep.labdsof.reports.domain.Membership;
import pt.isep.labdsof.reports.domain.Permission;
import pt.isep.labdsof.reports.domain.Priority;
import pt.isep.labdsof.reports.domain.Team;
import pt.isep.labdsof.reports.domain.TriageSuggestion;

/**
 * US-35: the control room accepts or changes the AI suggestion over HTTP, on the real database, with the security
 * matrix of both endpoints (spec US-35 §5). The shared context has no triage consumer, so each suggestion is written
 * by the test as the worker would write it.
 */
class TriageDecisionIT extends IntegrationTest {

  @Autowired HelpRequests helpRequests;
  @Autowired TransactionTemplate transactions;
  @Autowired HelpRequestService service;
  @Autowired MeterRegistry meters;

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

  String requests() {
    return "/api/v1/events/" + event + "/help-requests";
  }

  String submit() throws Exception {
    var created = post(requests(), JWKS.valid(participant), submitBody(UUID.randomUUID(), "Friend fainted"));
    assertThat(created.statusCode()).isEqualTo(201);
    return JsonPath.read(created.body(), "$.id");
  }

  /** A request with the suggestion Health / High stored, as the triage worker saves it. */
  String suggested() throws Exception {
    var id = submit();
    var request = helpRequests.find(event, UUID.fromString(id)).orElseThrow();
    assertThat(helpRequests.saveTriage(request.withTriage(
        TriageSuggestion.suggested(Category.Health, Priority.High, "Desmaio relatado"), Instant.now()))).isTrue();
    return id;
  }

  HttpResponse<String> accept(String id, String token) throws Exception {
    return post(requests() + "/" + id + "/accept-suggestion", token, "");
  }

  HttpResponse<String> change(String id, String token, String json) throws Exception {
    return patch(requests() + "/" + id, token, json);
  }

  List<Map<String, Object>> history(String id) {
    return jdbc.sql("""
        SELECT field, old_value, new_value, actor_user_id, event_id::text, changed_at
        FROM help_request_change WHERE request_id = :id ORDER BY id
        """).param("id", UUID.fromString(id)).query().listOfRows();
  }

  long version(String id) {
    return jdbc.sql("SELECT version FROM help_request WHERE id = :id").param("id", UUID.fromString(id))
        .query(Long.class).single();
  }

  @Test
  @DisplayName("US-35 AC-1 the control room accepts a suggestion and it is recorded as theirs")
  void accept() throws Exception {
    var id = suggested();
    String cursor = JsonPath.read(get(requests(), JWKS.valid(operator)).body(), "$.serverTime");
    var before = Instant.now();

    var response = accept(id, JWKS.valid(operator));

    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    Map<String, Object> body = JsonPath.read(response.body(), "$");
    assertThat(body).containsEntry("id", id).containsEntry("category", "Health").containsEntry("priority", "High")
        .containsEntry("defaultTeamId", null);
    assertThat(JsonPath.<Map<String, Object>>read(response.body(), "$.triage"))
        .containsEntry("state", "Accepted").containsEntry("suggestedCategory", "Health")
        .containsEntry("suggestedPriority", "High").containsEntry("suggestedReason", "Desmaio relatado");
    assertThat(version(id)).isEqualTo(2);
    var rows = history(id);
    assertThat(rows).extracting(r -> r.get("field") + ":" + r.get("old_value") + ">" + r.get("new_value"))
        .containsExactly("category:null>Health", "priority:null>High", "triage:Suggested>Accepted");
    assertThat(rows).allSatisfy(r -> {
      assertThat(r).containsEntry("actor_user_id", operator).containsEntry("event_id", event.toString());
      assertThat(((java.sql.Timestamp) r.get("changed_at")).toInstant()).isBetween(before, Instant.now());
    });
    // The next poll of the live list shows the decision
    var poll = get(requests() + "?updatedSince=" + cursor, JWKS.valid(operator)).body();
    assertThat(JsonPath.<List<String>>read(poll, "$.items[*].id")).contains(id);
    assertThat(JsonPath.<List<String>>read(poll, "$.items[?(@.id == '" + id + "')].triage.state"))
        .containsExactly("Accepted");
  }

  double accepted() {
    var counter = meters.find("farol.triage.decision").tag("decision", "Accepted").counter();
    return counter == null ? 0 : counter.count();
  }

  @Test
  @DisplayName("US-35 AC-1 a decision is counted after its commit, and one rolled back is not")
  void countedAfterCommit() throws Exception {
    var rolledBack = UUID.fromString(suggested());
    var before = accepted();
    transactions.executeWithoutResult(status -> {
      service.acceptSuggestion(event, new Actor(operator, null), rolledBack);
      status.setRollbackOnly();
    });
    assertThat(accepted()).isEqualTo(before);
    assertThat(helpRequests.find(event, rolledBack).orElseThrow().triage().state().name()).isEqualTo("Suggested");

    assertThat(accept(rolledBack.toString(), JWKS.valid(operator)).statusCode()).isEqualTo(200);
    assertThat(accepted()).isEqualTo(before + 1);
  }

  @Test
  @DisplayName("US-35 AC-1 accepting without a suggestion is a conflict")
  void acceptWithoutSuggestion() throws Exception {
    var pending = submit();
    var failed = submit();
    var request = helpRequests.find(event, UUID.fromString(failed)).orElseThrow();
    helpRequests.saveTriage(request.withTriage(
        TriageSuggestion.notTriaged(pt.isep.labdsof.reports.domain.NotTriagedReason.ProviderError), Instant.now()));
    var accepted = suggested();
    assertThat(accept(accepted, JWKS.valid(operator)).statusCode()).isEqualTo(200);

    for (var id : List.of(pending, failed, accepted)) {
      var response = accept(id, JWKS.valid(operator));
      assertThat(response.statusCode()).as(id).isEqualTo(409);
      assertThat(response.body()).contains("no suggestion");
    }
    assertThat(history(pending)).isEmpty();
    assertThat(history(failed)).isEmpty();
    assertThat(history(accepted)).hasSize(3);
    assertThat(version(pending)).isEqualTo(1);
  }

  @Test
  @DisplayName("US-35 AC-1 two accepts at the same time record one decision")
  void concurrentAccepts() throws Exception {
    var id = suggested();
    var start = new CountDownLatch(1);
    var statuses = new ArrayList<Integer>();
    try (var pool = Executors.newFixedThreadPool(2)) {
      var futures = Stream.of(1, 2).map(i -> pool.submit(() -> {
        start.await();
        return accept(id, JWKS.valid(operator)).statusCode();
      })).toList();
      start.countDown();
      for (var future : futures) {
        statuses.add(future.get(20, TimeUnit.SECONDS));
      }
    }
    assertThat(statuses).containsExactlyInAnyOrder(200, 409);
    assertThat(history(id)).hasSize(3);
    assertThat(version(id)).isEqualTo(2);
  }

  @Test
  @DisplayName("US-35 AC-2 the control room changes a suggestion and it reads Changed")
  void change() throws Exception {
    var id = suggested();

    var response = change(id, JWKS.valid(operator), "{\"priority\":\"Critical\"}");

    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    assertThat(JsonPath.<String>read(response.body(), "$.priority")).isEqualTo("Critical");
    assertThat(JsonPath.<Object>read(response.body(), "$.category")).isNull();
    assertThat(JsonPath.<Map<String, Object>>read(response.body(), "$.triage"))
        .containsEntry("state", "Changed").containsEntry("suggestedCategory", "Health")
        .containsEntry("suggestedPriority", "High");
    assertThat(history(id)).extracting(r -> r.get("field") + ":" + r.get("old_value") + ">" + r.get("new_value"))
        .containsExactly("priority:null>Critical", "triage:Suggested>Changed");

    // Both values, then the same values again: only what changed is recorded, and nothing the second time
    assertThat(change(id, JWKS.valid(operator), "{\"category\":\"Safety\",\"priority\":\"Critical\"}")
        .statusCode()).isEqualTo(200);
    assertThat(change(id, JWKS.valid(operator), "{\"category\":\"Safety\"}").statusCode()).isEqualTo(200);
    assertThat(history(id)).hasSize(3).last().satisfies(r -> assertThat(r).containsEntry("field", "category")
        .containsEntry("new_value", "Safety"));
    assertThat(version(id)).isEqualTo(3);
    // A member's values are not a suggestion to accept
    assertThat(accept(id, JWKS.valid(operator)).statusCode()).isEqualTo(409);
  }

  @Test
  @DisplayName("US-35 AC-2 a suggestion after the member's values is refused by the database")
  void suggestionAfterTheMember() throws Exception {
    var id = submit();
    assertThat(change(id, JWKS.valid(operator), "{\"category\":\"Safety\",\"priority\":\"Medium\"}").statusCode())
        .isEqualTo(200);
    var request = helpRequests.find(event, UUID.fromString(id)).orElseThrow();

    assertThat(helpRequests.saveTriage(request.withTriage(
        TriageSuggestion.suggested(Category.Health, Priority.Critical, "x"), Instant.now()))).isFalse();

    var body = get(requests() + "/" + id, JWKS.valid(operator)).body();
    assertThat(JsonPath.<String>read(body, "$.category")).isEqualTo("Safety");
    assertThat(JsonPath.<String>read(body, "$.priority")).isEqualTo("Medium");
    assertThat(JsonPath.<String>read(body, "$.triage.state")).isEqualTo("Changed");
    assertThat(JsonPath.<Object>read(body, "$.triage.suggestedCategory")).isNull();
  }

  @Test
  @DisplayName("US-35 AC-2 the worker waits for a member's decision and then writes nothing")
  void workerWaitsForTheLock() throws Exception {
    var id = UUID.fromString(submit());
    var pending = helpRequests.find(event, id).orElseThrow();
    var locked = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      // A member's decision holds the lock until it is told to commit
      var member = pool.submit(() -> transactions.executeWithoutResult(status -> {
        var request = helpRequests.findForUpdate(event, id).orElseThrow();
        var decision = request.change(Category.Safety, Priority.High, Instant.now());
        helpRequests.saveDecision(decision.request(), new Actor(operator, null), decision.changes());
        locked.countDown();
        try {
          assertThat(release.await(20, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }));
      assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
      var worker = pool.submit(() -> helpRequests.saveTriage(pending.withTriage(
          TriageSuggestion.suggested(Category.Health, Priority.Critical, "x"), Instant.now())));

      // The worker's save waits on the member's lock ...
      try {
        worker.get(1, TimeUnit.SECONDS);
        throw new AssertionError("the worker's save did not wait for the member's decision");
      } catch (TimeoutException expected) {
        // waiting, as it should
      }
      release.countDown();
      member.get(10, TimeUnit.SECONDS);
      // ... then finds a decided triage and writes nothing
      assertThat(worker.get(10, TimeUnit.SECONDS)).isFalse();
    }
    var saved = helpRequests.find(event, id).orElseThrow();
    assertThat(saved.category()).isEqualTo(Category.Safety);
    assertThat(saved.triage().state().name()).isEqualTo("Changed");
    assertThat(saved.triage().suggestedCategory()).isNull();
  }

  @Test
  @DisplayName("US-35 AC-2 a member's decision locks the triage before the request, as the worker does")
  void lockOrder() throws Exception {
    // The worker's save updates triage_suggestion, then help_request; the member must take them in the same
    // order, or the two wait on each other (deadlock, G8 review)
    var id = UUID.fromString(submit());
    var triageLocked = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var worker = pool.submit(() -> transactions.executeWithoutResult(status -> {
        jdbc.sql("SELECT 1 FROM triage_suggestion WHERE request_id = :id FOR UPDATE").param("id", id).query()
            .listOfRows();
        triageLocked.countDown();
        try {
          assertThat(release.await(20, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }));
      assertThat(triageLocked.await(10, TimeUnit.SECONDS)).isTrue();
      var member = pool.submit(() -> transactions.execute(status -> helpRequests.findForUpdate(event, id)));
      try {
        member.get(1, TimeUnit.SECONDS);
        throw new AssertionError("the member did not wait for the triage lock");
      } catch (TimeoutException expected) {
        // waiting on the triage, as it should
      }
      // ... without holding the request: the worker's next statement would get it
      assertThat(jdbc.sql("SELECT id FROM help_request WHERE id = :id FOR UPDATE NOWAIT").param("id", id)
          .query(UUID.class).single()).isEqualTo(id);
      release.countDown();
      worker.get(10, TimeUnit.SECONDS);
      assertThat(member.get(10, TimeUnit.SECONDS)).isPresent();
    }
  }

  @Test
  @DisplayName("US-35 AC-2 a malformed change is rejected")
  void malformedChange() throws Exception {
    var id = suggested();
    for (var json : List.of("{}", "{\"category\":null}", "{\"category\":null,\"priority\":null}",
        "{\"category\":null,\"priority\":\"High\"}",
        "{\"category\":\"Medical\"}", "{\"priority\":\"Urgent\"}",
        "{\"category\":\"Health'); DROP TABLE help_request;--\"}",
        "{\"category\":\"Health\",\"routedTeamId\":\"" + UUID.randomUUID() + "\"}",
        "{\"priority\":\"High\",\"triage\":{\"state\":\"Accepted\"}}", "not json", "")) {
      var response = change(id, JWKS.valid(operator), json);
      assertThat(response.statusCode()).as(json).isEqualTo(400);
    }
    assertThat(change("not-a-uuid", JWKS.valid(operator), "{\"category\":\"Health\"}").statusCode()).isEqualTo(400);
    assertThat(accept("not-a-uuid", JWKS.valid(operator)).statusCode()).isEqualTo(400);
    assertThat(post("/api/v1/events/not-a-uuid/help-requests/" + id + "/accept-suggestion", JWKS.valid(operator),
        "").statusCode()).isEqualTo(400);
    assertThat(history(id)).isEmpty();
    assertThat(version(id)).isEqualTo(1);
    assertThat(jdbc.sql("SELECT count(*) FROM help_request").query(Long.class).single()).isPositive();
  }

  @Test
  @DisplayName("US-35 AC-1 a member without HelpRequests.Triage gets 403")
  void forbidden() throws Exception {
    var id = suggested();
    assertThat(accept(id, JWKS.valid(participant)).statusCode()).isEqualTo(403);
    assertThat(change(id, JWKS.valid(participant), "{\"category\":\"Other\"}").statusCode()).isEqualTo(403);
    assertThat(history(id)).isEmpty();
  }

  /** The problem's instance is the path asked for; the rest of a 404 body must be the same in every case. */
  static final String INSTANCE = "\"instance\":\"[^\"]*\"";

  @Test
  @DisplayName("US-35 AC-1 another event's member or request gets 404 without revealing it")
  void notFound() throws Exception {
    var removed = "removed-" + UUID.randomUUID();
    copies.upsertIfNewer(new Membership(event, removed, Role.EventAdmin, Membership.Status.Removed, Set.of(), 1));
    var otherEvent = UUID.randomUUID();
    var elsewhere = "elsewhere-" + UUID.randomUUID();
    member(otherEvent, elsewhere, Role.EventAdmin);
    var fresh = suggested();
    // The answer for an event that does not exist: the cases below must not tell more
    var reference = post("/api/v1/events/" + UUID.randomUUID() + "/help-requests/" + fresh + "/accept-suggestion",
        JWKS.valid(operator), "");
    assertThat(reference.statusCode()).isEqualTo(404);
    var expected = reference.body().replaceAll(INSTANCE, "");

    for (var user : List.of("nobody-" + UUID.randomUUID(), removed, elsewhere)) {
      for (var response : List.of(accept(fresh, JWKS.valid(user)),
          change(fresh, JWKS.valid(user), "{\"category\":\"Other\"}"))) {
        assertThat(response.statusCode()).as(user).isEqualTo(404);
        assertThat(response.body().replaceAll(INSTANCE, "")).isEqualTo(expected);
      }
    }
    // The other event's EventAdmin, asking through their own event, and an unknown request
    var otherPath = "/api/v1/events/" + otherEvent + "/help-requests/";
    for (var path : List.of(otherPath + fresh, "/api/v1/events/" + event + "/help-requests/" + UUID.randomUUID())) {
      var token = path.startsWith(otherPath) ? JWKS.valid(elsewhere) : JWKS.valid(operator);
      var accepted = post(path + "/accept-suggestion", token, "");
      var changed = patch(path, token, "{\"category\":\"Other\"}");
      assertThat(accepted.statusCode()).as(path).isEqualTo(404);
      assertThat(changed.statusCode()).as(path).isEqualTo(404);
      assertThat(accepted.body().replaceAll(INSTANCE, "")).isEqualTo(expected);
      assertThat(changed.body().replaceAll(INSTANCE, "")).isEqualTo(expected);
    }
    assertThat(history(fresh)).isEmpty();
    assertThat(version(fresh)).isEqualTo(1);
  }

  static Stream<String> badTokens() {
    return Stream.of(null, JWKS.expired("operator"), JWKS.wrongSignature("operator"),
        JWKS.wrongIssuer("operator"), JWKS.wrongAudience("operator"), "not-a-jwt");
  }

  @ParameterizedTest
  @MethodSource("badTokens")
  @DisplayName("US-35 AC-1 every bad token gets 401")
  void badToken(String token) throws Exception {
    var id = suggested();
    assertThat(accept(id, token).statusCode()).isEqualTo(401);
    assertThat(change(id, token, "{\"category\":\"Other\"}").statusCode()).isEqualTo(401);
    assertThat(history(id)).isEmpty();
  }
}
