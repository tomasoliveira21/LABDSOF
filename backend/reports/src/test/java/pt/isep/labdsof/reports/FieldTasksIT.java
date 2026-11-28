package pt.isep.labdsof.reports;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.net.http.HttpResponse;
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
import org.springframework.beans.factory.annotation.Autowired;
import pt.isep.labdsof.reports.application.HelpRequests;
import pt.isep.labdsof.reports.application.LocalCopyService;
import pt.isep.labdsof.reports.domain.Category;
import pt.isep.labdsof.reports.domain.Membership;
import pt.isep.labdsof.reports.domain.Membership.Role;
import pt.isep.labdsof.reports.domain.Permission;
import pt.isep.labdsof.reports.domain.Priority;
import pt.isep.labdsof.reports.domain.TriageSuggestion;

/**
 * US-31: a field team lists the tasks routed to it over HTTP, on the real database: what each task carries, the
 * tasks it must not see, and the security matrix (spec US-31 §5).
 */
class FieldTasksIT extends IntegrationTest {

  @Autowired HelpRequests helpRequests;
  @Autowired LocalCopyService localCopies;

  final UUID event = UUID.randomUUID();
  final UUID controlRoom = UUID.randomUUID();
  final UUID nursing = UUID.randomUUID();
  final UUID firstAid = UUID.randomUUID();
  final UUID security = UUID.randomUUID();
  final String participant = "participant-" + UUID.randomUUID();
  final String operator = "operator-" + UUID.randomUUID();
  final String nurse = "nurse-" + UUID.randomUUID();
  final String guard = "guard-" + UUID.randomUUID();

  @BeforeEach
  void teams() {
    copies.upsertIfNewer(RouteRequestIT.team(controlRoom, event, "Control room", Category.Other, false, 1,
        Permission.HELP_REQUESTS_TRIAGE));
    copies.upsertIfNewer(RouteRequestIT.team(nursing, event, "Nursing", Category.Health, false, 1,
        Permission.HELP_REQUESTS_RESPOND));
    copies.upsertIfNewer(RouteRequestIT.team(firstAid, event, "First aid", Category.Health, false, 1,
        Permission.HELP_REQUESTS_RESPOND));
    copies.upsertIfNewer(RouteRequestIT.team(security, event, "Security", Category.Safety, false, 1,
        Permission.HELP_REQUESTS_RESPOND));
    member(event, participant, Role.RegularUser);
    member(event, operator, Role.RegularUser, controlRoom);
    member(event, nurse, Role.RegularUser, nursing);
    member(event, guard, Role.RegularUser, security);
  }

  String requests() {
    return "/api/v1/events/" + event + "/help-requests";
  }

  String submit(String description) throws Exception {
    var created = post(requests(), JWKS.valid(participant), submitBody(UUID.randomUUID(), description));
    assertThat(created.statusCode()).isEqualTo(201);
    return JsonPath.read(created.body(), "$.id");
  }

  /** A request submitted and routed by the control room to {@code teamId}. */
  String routed(UUID teamId) throws Exception {
    var id = submit("Friend fainted");
    route(id, teamId);
    return id;
  }

  void route(String id, UUID teamId) throws Exception {
    var routed = post(requests() + "/" + id + "/route", JWKS.valid(operator), "{\"teamId\":\"" + teamId + "\"}");
    assertThat(routed.statusCode()).as(routed.body()).isEqualTo(200);
  }

  HttpResponse<String> tasks(String token) throws Exception {
    return get("/api/v1/events/" + event + "/field-tasks", token);
  }

  /** The request IDs of the caller's list, in the order answered. */
  List<String> listed(String user) throws Exception {
    var response = tasks(JWKS.valid(user));
    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    return JsonPath.read(response.body(), "$.items[*].target.id");
  }

  @Test
  @DisplayName("US-31 AC-1 a routed request appears in the team's list with its category, point and description")
  void listed() throws Exception {
    var triaged = submit("Friend fainted near the bar");
    var request = helpRequests.find(event, UUID.fromString(triaged)).orElseThrow();
    assertThat(helpRequests.saveTriage(request.withTriage(
        TriageSuggestion.suggested(Category.Health, Priority.High, "Desmaio relatado"), Instant.now()))).isTrue();
    route(triaged, nursing);
    var untriaged = routed(nursing);

    var response = tasks(JWKS.valid(nurse));

    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    Map<String, Object> body = JsonPath.read(response.body(), "$");
    assertThat(body).containsOnlyKeys("items");
    assertThat(JsonPath.<List<String>>read(response.body(), "$.items[*].target.id"))
        .containsExactly(triaged, untriaged);
    Map<String, Object> first = JsonPath.read(response.body(), "$.items[0]");
    var task = jdbc.sql("SELECT id::text, dispatched_at FROM field_task WHERE help_request_id = :id")
        .param("id", UUID.fromString(triaged)).query().singleRow();
    assertThat(first).containsEntry("id", task.get("id")).containsEntry("purpose", "Assist")
        .containsEntry("status", "Open").containsEntry("teamId", nursing.toString())
        .containsEntry("acceptedBy", null);
    assertThat(Instant.parse((String) first.get("dispatchedAt")))
        .isEqualTo(((java.sql.Timestamp) task.get("dispatched_at")).toInstant());
    assertThat(first.get("updatedAt")).isEqualTo(first.get("dispatchedAt"));
    assertThat(JsonPath.<Map<String, Object>>read(response.body(), "$.items[0].target")).isEqualTo(Map.of(
        "kind", "HelpRequest", "id", triaged, "category", "Health",
        "location", Map.of("mapVersion", 3, "x", 0.42, "y", 0.77), "description", "Friend fainted near the bar"));
    assertThat(JsonPath.<Object>read(response.body(), "$.items[1].target.category")).isNull();
  }

  @Test
  @DisplayName("US-31 AC-1 a route is in the next poll")
  void nextPoll() throws Exception {
    assertThat(listed(nurse)).isEmpty();
    var id = routed(nursing);
    assertThat(listed(nurse)).containsExactly(id);
  }

  @Test
  @DisplayName("US-31 AC-2 a request routed to another team is not listed")
  void otherTeam() throws Exception {
    var mine = routed(nursing);
    var theirs = routed(security);
    assertThat(listed(nurse)).containsExactly(mine);
    assertThat(listed(guard)).containsExactly(theirs);
  }

  @Test
  @DisplayName("US-31 AC-2 a request routed away, closed or of a deleted team leaves the list")
  void leavesTheList() throws Exception {
    var medic = "medic-" + UUID.randomUUID();
    member(event, medic, Role.RegularUser, nursing, firstAid);
    var away = routed(nursing);
    var closed = routed(nursing);
    var deleted = routed(nursing);
    var kept = routed(firstAid);
    assertThat(listed(medic)).containsExactly(away, closed, deleted, kept);

    route(away, security);
    assertThat(post(requests() + "/" + closed + "/close", JWKS.valid(operator), "").statusCode()).isEqualTo(200);
    assertThat(listed(medic)).containsExactly(deleted, kept);

    localCopies.apply(UUID.randomUUID(), RouteRequestIT.team(nursing, event, "Nursing", Category.Health, true, 2,
        Permission.HELP_REQUESTS_RESPOND));
    assertThat(listed(medic)).containsExactly(kept);
    assertThat(listed(guard)).containsExactly(away);
  }

  @Test
  @DisplayName("US-31 AC-2 a member who leaves the team no longer sees its tasks")
  void leavesTheTeam() throws Exception {
    routed(nursing);
    var kept = routed(firstAid);
    copies.upsertIfNewer(new Membership(event, nurse, Role.RegularUser, Membership.Status.Active,
        Set.of(firstAid), 2));
    assertThat(listed(nurse)).containsExactly(kept);

    copies.upsertIfNewer(new Membership(event, nurse, Role.RegularUser, Membership.Status.Active, Set.of(), 3));
    assertThat(tasks(JWKS.valid(nurse)).statusCode()).isEqualTo(403);
  }

  @Test
  @DisplayName("US-31 AC-2 a member without HelpRequests.Respond gets 403")
  void forbidden() throws Exception {
    routed(nursing);
    for (var user : List.of(participant, operator)) {
      assertThat(tasks(JWKS.valid(user)).statusCode()).isEqualTo(403);
    }
    // An EventAdmin has every permission, but no team of their own to list (spec D1)
    var admin = "admin-" + UUID.randomUUID();
    member(event, admin, Role.EventAdmin);
    assertThat(listed(admin)).isEmpty();
  }

  @Test
  @DisplayName("US-31 AC-2 another event's member gets 404 without revealing it")
  void notFound() throws Exception {
    routed(nursing);
    var removed = "removed-" + UUID.randomUUID();
    copies.upsertIfNewer(new Membership(event, removed, Role.RegularUser, Membership.Status.Removed,
        Set.of(nursing), 1));
    var otherEvent = UUID.randomUUID();
    var elsewhere = "elsewhere-" + UUID.randomUUID();
    member(otherEvent, elsewhere, Role.EventAdmin);
    var reference = get("/api/v1/events/" + UUID.randomUUID() + "/field-tasks", JWKS.valid(nurse));
    assertThat(reference.statusCode()).isEqualTo(404);
    var expected = reference.body().replaceAll(ChangeHistoryIT.INSTANCE, "");

    for (var token : List.of(JWKS.valid("nobody-" + UUID.randomUUID()), JWKS.valid(removed), JWKS.valid(elsewhere),
        JWKS.withoutSubject())) {
      var response = tasks(token);
      assertThat(response.statusCode()).isEqualTo(404);
      assertThat(response.body().replaceAll(ChangeHistoryIT.INSTANCE, "")).isEqualTo(expected);
    }
  }

  static Stream<String> badTokens() {
    return ChangeHistoryIT.badTokens();
  }

  @ParameterizedTest
  @MethodSource("badTokens")
  @DisplayName("US-31 AC-2 every bad token gets 401")
  void badToken(String token) throws Exception {
    routed(nursing);
    assertThat(tasks(token).statusCode()).isEqualTo(401);
  }

  @Test
  @DisplayName("US-31 AC-2 a malformed event ID is rejected")
  void malformed() throws Exception {
    assertThat(get("/api/v1/events/not-a-uuid/field-tasks", JWKS.valid(nurse)).statusCode()).isEqualTo(400);
  }
}
