package pt.isep.labdsof.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import pt.isep.labdsof.events.domain.Membership;
import pt.isep.labdsof.events.domain.Membership.Role;
import pt.isep.labdsof.events.domain.Membership.Status;

/** The four team operations over HTTP with signed tokens (US-16). */
@ExtendWith(OutputCaptureExtension.class)
class TeamsIT extends IntegrationTest {

  private static final String JSON_TYPE = "application/json";

  private static String code() {
    return "it-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private ApiClient api() {
    return new ApiClient(port);
  }

  private static String teamsPath(Object eventId) {
    return "/api/v1/events/" + eventId + "/teams";
  }

  private static String teamPath(Object eventId, Object teamId) {
    return teamsPath(eventId) + "/" + teamId;
  }

  /** An EventAdmin of a fresh published event. */
  private UUID admin(UUID eventId) {
    var user = UUID.randomUUID();
    member(eventId, user, Role.EventAdmin, Status.Active, 1);
    return user;
  }

  private UUID memberOfTeams(UUID eventId, Set<UUID> teamIds) {
    var user = UUID.randomUUID();
    copies.upsertIfNewer(new Membership(eventId, user, Role.RegularUser, Status.Active, teamIds, 1));
    return user;
  }

  private HttpResponse<String> list(UUID eventId, UUID user) {
    return api().get(teamsPath(eventId), JWKS.valid(user.toString()));
  }

  private HttpResponse<String> create(UUID eventId, UUID user, String name) {
    return send("POST", teamsPath(eventId), user, "{\"name\":%s}".formatted(quoted(name)));
  }

  private HttpResponse<String> rename(UUID eventId, UUID teamId, UUID user, String name, String categories) {
    return send("PATCH", teamPath(eventId, teamId), user,
        "{\"name\":%s,\"handledCategories\":%s}".formatted(quoted(name), categories));
  }

  private HttpResponse<String> delete(UUID eventId, Object teamId, UUID user) {
    return send("DELETE", teamPath(eventId, teamId), user, null);
  }

  private HttpResponse<String> send(String method, String path, UUID user, String body) {
    var headers = body == null ? new String[0] : new String[] {"Content-Type", JSON_TYPE};
    return api().send(method, path, "Bearer " + JWKS.valid(user.toString()), body, headers);
  }

  private static String quoted(String value) {
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        .replace("\t", "\\t") + "\"";
  }

  private static List<Map<String, Object>> teams(HttpResponse<String> response) {
    return ApiClient.jsonList(response);
  }

  private static UUID teamId(HttpResponse<String> response) {
    return UUID.fromString((String) ApiClient.json(response).get("teamId"));
  }

  /** The TeamChanged payloads this event has in the outbox, oldest first. */
  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> changes(UUID eventId) {
    return jdbc.sql("SELECT body::text FROM outbox WHERE type = 'TeamChanged' AND body->>'eventId' = :event "
        + "ORDER BY id").param("event", eventId.toString()).query(String.class).list().stream()
        .map(body -> (Map<String, Object>) ApiClient.readJson(body).get("payload")).toList();
  }

  private UUID openEvent() {
    return insertEvent("Published", null, code());
  }

  @Test
  @DisplayName("US-16 AC-1 a created team is in the list with no permissions and no categories")
  void createdTeamIsInTheList() {
    var event = openEvent();
    var user = admin(event);

    var created = create(event, user, "Nursing");

    assertThat(created.statusCode()).isEqualTo(201);
    assertThat(created.headers().firstValue("Content-Type")).hasValue(JSON_TYPE);
    var body = ApiClient.json(created);
    assertThat(body).containsEntry("name", "Nursing").containsEntry("permissions", List.of())
        .containsEntry("handledCategories", List.of()).containsKey("teamId");

    var listed = list(event, user);
    assertThat(listed.statusCode()).isEqualTo(200);
    assertThat(teams(listed)).containsExactly(body);
    assertThat(changes(event)).singleElement().satisfies(payload -> {
      assertThat(payload).containsEntry("name", "Nursing").containsEntry("deleted", false)
          .containsEntry("version", 1).containsEntry("permissions", List.of())
          .containsEntry("handledCategories", List.of());
      assertThat(payload.get("teamId")).isEqualTo(body.get("teamId"));
    });
    assertThat(storedTeam(teamId(created))).containsEntry("created_by", user).containsEntry("updated_by", user);
  }

  private Map<String, Object> storedTeam(UUID teamId) {
    var row = jdbc.sql("SELECT created_by, updated_by, created_at, updated_at, version FROM team "
        + "WHERE team_id = :id").param("id", teamId).query().singleRow();
    return new LinkedHashMap<>(row);
  }

  @Test
  @DisplayName("US-16 AC-2 a name already taken is refused ignoring case and blanks")
  void nameAlreadyTakenIsRefused() {
    var event = openEvent();
    var user = admin(event);
    create(event, user, "Control room");

    for (var name : List.of("Control room", "control ROOM", "  Control room  ")) {
      var response = create(event, user, name);

      ApiClient.assertProblem(response, 409);
      assertThat(ApiClient.json(response).get("detail")).isEqualTo("A team with this name already exists.");
    }
    assertThat(teams(list(event, user))).hasSize(1);
    assertThat(changes(event)).hasSize(1);
  }

  @Test
  @DisplayName("US-16 AC-2 two creations of the same name at once leave one team")
  void twoCreationsAtOnce() throws Exception {
    var event = openEvent();
    var user = admin(event);

    try (var pool = Executors.newFixedThreadPool(2)) {
      var answers = pool.invokeAll(IntStream.range(0, 2)
          .mapToObj(i -> (java.util.concurrent.Callable<Integer>) () -> create(event, user, "PCR").statusCode())
          .toList());
      var codes = new java.util.ArrayList<Integer>();
      for (var answer : answers) {
        codes.add(answer.get());
      }
      assertThat(codes).containsExactlyInAnyOrder(201, 409);
    }
    assertThat(teams(list(event, user))).hasSize(1);
    assertThat(changes(event)).hasSize(1);
  }

  @Test
  @DisplayName("US-16 AC-2 the name of a deleted team is free again")
  void nameOfADeletedTeamIsFree() {
    var event = openEvent();
    var user = admin(event);
    var first = teamId(create(event, user, "Logistics"));
    assertThat(delete(event, first, user).statusCode()).isEqualTo(204);

    var second = create(event, user, "logistics");

    assertThat(second.statusCode()).isEqualTo(201);
    assertThat(teams(list(event, user))).singleElement()
        .satisfies(team -> assertThat(team).containsEntry("name", "logistics"));
    assertThat(jdbc.sql("SELECT count(*) FROM team WHERE event_id = :event").param("event", event)
        .query(Integer.class).single()).isEqualTo(2);
  }

  @ParameterizedTest(name = "[{index}] \"{0}\"")
  @ValueSource(strings = {"", " ", "   ", "\t"})
  @DisplayName("US-16 AC-1 a name outside its bounds is refused")
  void blankNameIsRefused(String name) {
    var event = openEvent();
    var user = admin(event);

    ApiClient.assertProblem(create(event, user, name), 400);
    assertThat(changes(event)).isEmpty();
  }

  @Test
  @DisplayName("US-16 AC-1 a name outside its bounds is refused: 100 characters pass, 101 do not")
  void nameLengthBounds() {
    var event = openEvent();
    var user = admin(event);

    assertThat(create(event, user, "a".repeat(100)).statusCode()).isEqualTo(201);
    ApiClient.assertProblem(create(event, user, "b".repeat(101)), 400);
    assertThat(create(event, user, "c").statusCode()).isEqualTo(201);
  }

  @Test
  @DisplayName("US-16 AC-1 a name is stored trimmed")
  void nameIsTrimmed() {
    var event = openEvent();
    var user = admin(event);

    var created = create(event, user, "  Control room  ");

    assertThat(ApiClient.json(created)).containsEntry("name", "Control room");
    assertThat(jdbc.sql("SELECT name FROM team WHERE team_id = :id").param("id", teamId(created))
        .query(String.class).single()).isEqualTo("Control room");
  }

  @Test
  @DisplayName("US-16 AC-1 injection text is stored as plain text")
  void injectionTextIsPlainText() {
    var event = openEvent();
    var user = admin(event);
    var name = "Robert'); DROP TABLE team;-- <script>alert(1)</script>";

    var created = create(event, user, name);

    assertThat(created.statusCode()).isEqualTo(201);
    assertThat(ApiClient.json(created)).containsEntry("name", name);
    assertThat(teams(list(event, user))).singleElement()
        .satisfies(team -> assertThat(team).containsEntry("name", name));
    assertThat(changes(event)).singleElement().satisfies(payload ->
        assertThat(payload).containsEntry("name", name));
  }

  @Test
  @DisplayName("US-16 AC-2 a rename to a name already taken is refused")
  void renameToATakenName() {
    var event = openEvent();
    var user = admin(event);
    var nursing = teamId(create(event, user, "Nursing"));
    create(event, user, "Security");

    ApiClient.assertProblem(rename(event, nursing, user, "SECURITY", "[]"), 409);

    assertThat(teams(list(event, user))).extracting(team -> team.get("name"))
        .containsExactly("Nursing", "Security");
    assertThat(changes(event)).hasSize(2);
  }

  @Test
  @DisplayName("US-16 AC-1 a rename and its handled categories are one change with the next version")
  void renameWithCategories() {
    var event = openEvent();
    var user = admin(event);
    var team = teamId(create(event, user, "Nursing"));

    var renamed = rename(event, team, user, "  Medical  ", "[\"Safety\",\"Health\"]");

    assertThat(renamed.statusCode()).isEqualTo(200);
    assertThat(ApiClient.json(renamed)).containsEntry("name", "Medical")
        .containsEntry("handledCategories", List.of("Health", "Safety"));
    assertThat(teams(list(event, user))).singleElement().satisfies(listed ->
        assertThat(listed).containsEntry("handledCategories", List.of("Health", "Safety")));
    assertThat(changes(event)).hasSize(2).last().satisfies(payload ->
        assertThat(payload).containsEntry("name", "Medical").containsEntry("version", 2)
            .containsEntry("handledCategories", List.of("Health", "Safety")));
    assertThat(storedTeam(team)).containsEntry("version", 2L);
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @ValueSource(strings = {"[\"Health\",\"Health\"]", "[\"Bogus\"]", "[\"health\"]", "[null]", "[\"\"]"})
  @DisplayName("US-16 AC-1 handled categories are a set and an unknown one is refused")
  void badCategoriesAreRefused(String categories) {
    var event = openEvent();
    var user = admin(event);
    var team = teamId(create(event, user, "Nursing"));

    ApiClient.assertProblem(rename(event, team, user, "Nursing", categories), 400);

    assertThat(changes(event)).hasSize(1);
  }

  @Test
  @DisplayName("US-16 AC-1 the five handled categories are accepted and an empty list clears them")
  void everyCategoryIsAccepted() {
    var event = openEvent();
    var user = admin(event);
    var team = teamId(create(event, user, "Control room"));

    var all = rename(event, team, user, "Control room",
        "[\"Other\",\"Infrastructure\",\"LostPerson\",\"Safety\",\"Health\"]");
    assertThat(ApiClient.json(all)).containsEntry("handledCategories",
        List.of("Health", "Safety", "LostPerson", "Infrastructure", "Other"));

    var none = rename(event, team, user, "Control room", "[]");
    assertThat(ApiClient.json(none)).containsEntry("handledCategories", List.of());
  }

  @Test
  @DisplayName("US-16 AC-3 a deleted team leaves the list")
  void deletedTeamLeavesTheList() {
    var event = openEvent();
    var user = admin(event);
    var team = teamId(create(event, user, "Nursing"));

    var deleted = delete(event, team, user);

    assertThat(deleted.statusCode()).isEqualTo(204);
    assertThat(deleted.body()).isEmpty();
    assertThat(teams(list(event, user))).isEmpty();
    assertThat(changes(event)).hasSize(2).last().satisfies(payload ->
        assertThat(payload).containsEntry("deleted", true).containsEntry("version", 2));
    assertThat(jdbc.sql("SELECT deleted_at IS NOT NULL FROM team WHERE team_id = :id").param("id", team)
        .query(Boolean.class).single()).isTrue();
  }

  @Test
  @DisplayName("US-16 AC-3 deleting a deleted team is 404")
  void deletingTwiceIsNotFound() {
    var event = openEvent();
    var user = admin(event);
    var team = teamId(create(event, user, "Nursing"));
    delete(event, team, user);

    var again = delete(event, team, user);

    ApiClient.assertProblem(again, 404);
    assertThat(ApiClient.json(again).get("detail")).isEqualTo("Team not found.");
    assertThat(changes(event)).hasSize(2);
  }

  @Test
  @DisplayName("US-16 AC-1 a team of another event is 404")
  void teamOfAnotherEventIsNotFound() {
    var mine = openEvent();
    var other = openEvent();
    var user = admin(mine);
    var otherAdmin = admin(other);
    var elsewhere = teamId(create(other, otherAdmin, "Nursing"));

    ApiClient.assertProblem(rename(mine, elsewhere, user, "Medical", "[]"), 404);
    ApiClient.assertProblem(delete(mine, elsewhere, user), 404);

    assertThat(teams(list(other, otherAdmin))).singleElement()
        .satisfies(team -> assertThat(team).containsEntry("name", "Nursing"));
    assertThat(changes(mine)).isEmpty();
  }

  @Test
  @DisplayName("US-16 AC-1 an unknown team is 404")
  void unknownTeamIsNotFound() {
    var event = openEvent();
    var user = admin(event);

    ApiClient.assertProblem(delete(event, UUID.randomUUID(), user), 404);
    ApiClient.assertProblem(rename(event, UUID.randomUUID(), user, "Medical", "[]"), 404);
  }

  @ParameterizedTest(name = "[{index}] {0} {1}")
  @CsvSource({"GET,/api/v1/events/not-a-uuid/teams", "POST,/api/v1/events/not-a-uuid/teams",
      "PATCH,/api/v1/events/not-a-uuid/teams/{team}", "DELETE,/api/v1/events/not-a-uuid/teams/{team}",
      "PATCH,/api/v1/events/{event}/teams/not-a-uuid", "DELETE,/api/v1/events/{event}/teams/not-a-uuid"})
  @DisplayName("US-16 AC-1 a malformed id is 400")
  void malformedIdIsBadRequest(String method, String path) {
    var event = openEvent();
    var user = admin(event);
    var body = method.equals("GET") || method.equals("DELETE") ? null
        : "{\"name\":\"Medical\",\"handledCategories\":[]}";

    var response = send(method, path.replace("{event}", event.toString())
        .replace("{team}", UUID.randomUUID().toString()), user, body);

    ApiClient.assertProblem(response, 400);
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @CsvSource({"Suspended,Published", "Closed,"})
  @DisplayName("US-16 AC-1 a suspended or closed event refuses team changes")
  void suspendedOrClosedRefusesChanges(String status, String previous) {
    var open = openEvent();
    var user = admin(open);
    var team = teamId(create(open, user, "Nursing"));
    var frozen = insertEvent(status, previous == null || previous.isEmpty() ? null : previous, code());
    var frozenAdmin = admin(frozen);
    var frozenTeam = jdbc.sql("""
        INSERT INTO team (team_id, event_id, name, handled_categories, version, created_at, created_by,
                          updated_at, updated_by)
        VALUES (:id, :event, 'Nursing', '{}', 1, now(), :by, now(), :by) RETURNING team_id
        """).param("id", UUID.randomUUID()).param("event", frozen).param("by", frozenAdmin)
        .query(UUID.class).single();

    ApiClient.assertProblem(create(frozen, frozenAdmin, "Security"), 409);
    var refused = rename(frozen, frozenTeam, frozenAdmin, "Medical", "[]");
    ApiClient.assertProblem(refused, 409);
    assertThat(ApiClient.json(refused).get("detail")).isEqualTo("This event is not open to changes.");
    ApiClient.assertProblem(delete(frozen, frozenTeam, frozenAdmin), 409);

    // the EventAdmin still reads them, and the open event is untouched
    assertThat(teams(list(frozen, frozenAdmin))).singleElement()
        .satisfies(listed -> assertThat(listed).containsEntry("name", "Nursing"));
    assertThat(changes(frozen)).isEmpty();
    assertThat(teams(list(open, user))).hasSize(1);
    assertThat(team).isNotNull();
  }

  @Test
  @DisplayName("US-16 AC-1 a draft event accepts team changes")
  void draftEventAcceptsChanges() {
    var event = insertEvent("Draft", null, null);
    var user = admin(event);

    var created = create(event, user, "Nursing");

    assertThat(created.statusCode()).isEqualTo(201);
    assertThat(rename(event, teamId(created), user, "Medical", "[\"Health\"]").statusCode()).isEqualTo(200);
    assertThat(delete(event, teamId(created), user).statusCode()).isEqualTo(204);
  }

  @Test
  @DisplayName("US-16 AC-1 an unknown event is 404")
  void unknownEventIsNotFound() {
    var user = UUID.randomUUID();
    var unknown = UUID.randomUUID();

    ApiClient.assertProblem(list(unknown, user), 404);
    ApiClient.assertProblem(create(unknown, user, "Nursing"), 404);
    ApiClient.assertProblem(rename(unknown, UUID.randomUUID(), user, "Nursing", "[]"), 404);
    ApiClient.assertProblem(delete(unknown, UUID.randomUUID(), user), 404);
  }

  @Test
  @DisplayName("US-16 AC-1 a non-member gets the same 404 as an unknown event, and the refusal is logged")
  void nonMemberGetsTheSameAsUnknown(CapturedOutput output) {
    var event = openEvent();
    var stranger = UUID.randomUUID();

    var nonMember = list(event, stranger);
    var unknown = list(UUID.randomUUID(), stranger);

    ApiClient.assertProblem(nonMember, 404);
    assertThat(comparable(nonMember)).isEqualTo(comparable(unknown));
    assertThat(logLines(output)).anySatisfy(line -> {
      assertThat(line).containsEntry("userId", stranger.toString()).containsEntry("eventId", event.toString());
      assertThat(line.get("refusal")).isEqualTo("NOT_A_MEMBER");
    });
  }

  private static Map<String, Object> comparable(HttpResponse<String> response) {
    var map = new LinkedHashMap<>(ApiClient.json(response));
    map.remove("instance");
    map.put("contentType", response.headers().firstValue("Content-Type").orElse(""));
    return map;
  }

  @Test
  @DisplayName("US-16 AC-1 a removed member and a member of another event get 404")
  void removedAndForeignMembersGet404() {
    var event = openEvent();
    var other = openEvent();
    var removed = UUID.randomUUID();
    member(event, removed, Role.EventAdmin, Status.Removed, 1);
    var foreign = admin(other);

    ApiClient.assertProblem(list(event, removed), 404);
    ApiClient.assertProblem(create(event, removed, "Nursing"), 404);
    ApiClient.assertProblem(list(event, foreign), 404);
    ApiClient.assertProblem(create(event, foreign, "Nursing"), 404);
  }

  @Test
  @DisplayName("US-16 AC-1 a RegularUser of a draft event gets 404, not 403")
  void regularUserOfADraftEvent() {
    var event = insertEvent("Draft", null, null);
    var user = memberOfTeams(event, Set.of());

    ApiClient.assertProblem(list(event, user), 404);
  }

  @Test
  @DisplayName("US-16 AC-1 a member without a reading permission is refused")
  void memberWithoutAReadingPermissionIsRefused() {
    var event = openEvent();
    var admin = admin(event);
    var team = teamId(create(event, admin, "Logistics"));
    var user = memberOfTeams(event, Set.of(team));

    var response = list(event, user);

    ApiClient.assertProblem(response, 403);
    assertThat(ApiClient.json(response).get("detail")).isEqualTo("No permission in this event.");
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @ValueSource(strings = {"HelpRequests.Triage", "Reports.Review"})
  @DisplayName("US-16 AC-1 a member of a team with HelpRequests.Triage or Reports.Review reads the list")
  void triageAndReviewMembersReadTheList(String permission) {
    var event = openEvent();
    var admin = admin(event);
    var team = teamId(create(event, admin, "Control room " + permission));
    jdbc.sql("INSERT INTO team_permission (event_id, team_id, permission) VALUES (:event, :team, :permission)")
        .param("event", event).param("team", team).param("permission", permission).update();
    var user = memberOfTeams(event, Set.of(team));

    var response = list(event, user);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(teams(response)).singleElement().satisfies(listed ->
        assertThat(listed.get("permissions")).isEqualTo(List.of(Map.of("permission", permission,
            "reportTypes", List.of()))));
  }

  @Test
  @DisplayName("US-16 AC-1 only an EventAdmin changes teams")
  void onlyAnEventAdminChangesTeams() {
    var event = openEvent();
    var admin = admin(event);
    var team = teamId(create(event, admin, "Control room"));
    jdbc.sql("INSERT INTO team_permission (event_id, team_id, permission) "
        + "VALUES (:event, :team, 'HelpRequests.Triage')").param("event", event).param("team", team).update();
    var user = memberOfTeams(event, Set.of(team));

    ApiClient.assertProblem(create(event, user, "Nursing"), 403);
    ApiClient.assertProblem(rename(event, team, user, "Room", "[]"), 403);
    ApiClient.assertProblem(delete(event, team, user), 403);

    assertThat(teams(list(event, user))).hasSize(1);
    assertThat(changes(event)).hasSize(1);
  }

  @Test
  @DisplayName("US-16 AC-1 an event with no team lists nothing")
  void eventWithNoTeamListsNothing() {
    var event = openEvent();
    var user = admin(event);

    var response = list(event, user);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).isEqualTo("[]");
  }

  @Test
  @DisplayName("US-16 AC-1 the list is ordered by name ignoring case, with each team's permissions")
  void listIsOrdered() {
    var event = openEvent();
    var user = admin(event);
    List.of("security", "Nursing", "aaa", "PCR").forEach(name -> create(event, user, name));
    var deleted = teamId(create(event, user, "Logistics"));
    delete(event, deleted, user);

    assertThat(teams(list(event, user))).extracting(team -> team.get("name"))
        .containsExactly("aaa", "Nursing", "PCR", "security");
  }

  @Test
  @DisplayName("US-16 AC-1 the teams of another event are never listed")
  void teamsOfAnotherEventAreNeverListed() {
    var mine = openEvent();
    var other = openEvent();
    var user = admin(mine);
    create(other, admin(other), "Theirs");
    create(mine, user, "Mine");

    assertThat(teams(list(mine, user))).extracting(team -> team.get("name")).containsExactly("Mine");
  }
}
