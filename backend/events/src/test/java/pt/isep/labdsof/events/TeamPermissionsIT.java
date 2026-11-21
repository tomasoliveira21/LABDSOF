package pt.isep.labdsof.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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

/** PUT /api/v1/events/{eventId}/teams/{teamId}/permissions over HTTP with signed tokens (US-17). */
@ExtendWith(OutputCaptureExtension.class)
class TeamPermissionsIT extends IntegrationTest {

  private static final String JSON_TYPE = "application/json";
  private static final String TRIAGE = "{\"permission\":\"HelpRequests.Triage\"}";
  private static final String REVIEW_HAZARD =
      "{\"permission\":\"Reports.Review\",\"reportTypes\":[\"Hazard\",\"NoWater\"]}";

  private ApiClient api() {
    return new ApiClient(port);
  }

  private UUID openEvent() {
    return insertEvent("Published", null, "tp-" + UUID.randomUUID().toString().substring(0, 8));
  }

  private UUID admin(UUID eventId) {
    var user = UUID.randomUUID();
    member(eventId, user, Role.EventAdmin, Status.Active, 1);
    return user;
  }

  private UUID teamOf(UUID eventId, UUID user, String name) {
    var created = api().send("POST", "/api/v1/events/" + eventId + "/teams",
        "Bearer " + JWKS.valid(user.toString()), "{\"name\":\"" + name + "\"}", "Content-Type", JSON_TYPE);
    assertThat(created.statusCode()).isEqualTo(201);
    return UUID.fromString((String) ApiClient.json(created).get("teamId"));
  }

  private HttpResponse<String> put(UUID eventId, Object teamId, UUID user, String body) {
    return api().send("PUT", "/api/v1/events/" + eventId + "/teams/" + teamId + "/permissions",
        "Bearer " + JWKS.valid(user.toString()), body, "Content-Type", JSON_TYPE);
  }

  private HttpResponse<String> list(UUID eventId, UUID user) {
    return api().get("/api/v1/events/" + eventId + "/teams", JWKS.valid(user.toString()));
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> changes(UUID eventId) {
    return jdbc.sql("SELECT body::text FROM outbox WHERE type = 'TeamChanged' AND body->>'eventId' = :event "
        + "ORDER BY id").param("event", eventId.toString()).query(String.class).list().stream()
        .map(body -> (Map<String, Object>) ApiClient.readJson(body).get("payload")).toList();
  }

  private List<Map<String, Object>> storedPermissions(UUID teamId) {
    return jdbc.sql("SELECT permission, report_types FROM team_permission WHERE team_id = :id "
        + "ORDER BY permission").param("id", teamId)
        .query((rs, row) -> Map.<String, Object>of("permission", rs.getString("permission"),
            "reportTypes", List.of((String[]) rs.getArray("report_types").getArray())))
        .list();
  }

  @Test
  @DisplayName("US-17 AC-1 an EventAdmin gives a team permissions from the fixed list, and they are stored, "
      + "answered and published")
  void permissionsAreGiven() {
    var event = openEvent();
    var user = admin(event);
    var team = teamOf(event, user, "Control room");

    var response = put(event, team, user, "[" + REVIEW_HAZARD + "," + TRIAGE + "]");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(ApiClient.json(response).get("permissions")).isEqualTo(List.of(
        Map.of("permission", "HelpRequests.Triage", "reportTypes", List.of()),
        Map.of("permission", "Reports.Review", "reportTypes", List.of("NoWater", "Hazard"))));
    assertThat(storedPermissions(team)).containsExactly(
        Map.of("permission", "HelpRequests.Triage", "reportTypes", List.of()),
        Map.of("permission", "Reports.Review", "reportTypes", List.of("NoWater", "Hazard")));
    assertThat(changes(event)).hasSize(2).last().satisfies(payload ->
        assertThat(payload).containsEntry("version", 2));
    assertThat(jdbc.sql("SELECT updated_by FROM team WHERE team_id = :id").param("id", team)
        .query(UUID.class).single()).isEqualTo(user);
  }

  @Test
  @DisplayName("US-17 AC-1 the permissions apply to this event only")
  void permissionsApplyToThisEventOnly() {
    var mine = openEvent();
    var other = openEvent();
    var user = admin(mine);
    var otherAdmin = admin(other);
    var team = teamOf(mine, user, "Control room");
    put(mine, team, user, "[" + TRIAGE + "]");
    var reader = UUID.randomUUID();
    copies.upsertIfNewer(new Membership(mine, reader, Role.RegularUser, Status.Active, Set.of(team), 1));
    copies.upsertIfNewer(new Membership(other, reader, Role.RegularUser, Status.Active, Set.of(team), 1));

    assertThat(list(mine, reader).statusCode()).isEqualTo(200);
    ApiClient.assertProblem(list(other, reader), 403);
    ApiClient.assertProblem(put(other, team, otherAdmin, "[" + TRIAGE + "]"), 404);
  }

  @Test
  @DisplayName("US-17 AC-2 Reports.Review carries the report types it reviews, and all seven are accepted")
  void reviewCarriesItsReportTypes() {
    var event = openEvent();
    var user = admin(event);
    var team = teamOf(event, user, "Control room");
    var all = "[{\"permission\":\"Reports.Review\",\"reportTypes\":[\"WrongInformation\",\"TransportProblem\","
        + "\"Hazard\",\"NoWater\",\"WcProblem\",\"CrowdedArea\",\"QueueAtEntrance\"]}]";

    var response = put(event, team, user, all);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(storedPermissions(team)).singleElement().satisfies(stored ->
        assertThat(stored.get("reportTypes")).isEqualTo(List.of("QueueAtEntrance", "CrowdedArea",
            "WcProblem", "NoWater", "Hazard", "TransportProblem", "WrongInformation")));
  }

  @Test
  @DisplayName("US-17 AC-2 Reports.Review with no report type is accepted and reviews nothing")
  void reviewWithoutTypes() {
    var event = openEvent();
    var user = admin(event);
    var team = teamOf(event, user, "Control room");

    var response = put(event, team, user, "[{\"permission\":\"Reports.Review\",\"reportTypes\":[]}]");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(storedPermissions(team)).containsExactly(
        Map.of("permission", "Reports.Review", "reportTypes", List.of()));
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @ValueSource(strings = {
      "[{\"permission\":\"Map.Edit\",\"reportTypes\":[\"Hazard\"]}]",
      "[{\"permission\":\"Map.Edit\"},{\"permission\":\"Map.Edit\"}]",
      "[{\"permission\":\"Reports.Review\",\"reportTypes\":[\"Hazard\",\"Hazard\"]}]",
      "[{\"permission\":\"Reports.Review\",\"reportTypes\":[null]}]",
      "[{\"permission\":\"Bogus\"}]",
      "[{\"permission\":\"REPORTS_REVIEW\"}]",
      "[{\"permission\":\"Reports.Review\",\"reportTypes\":[\"Bogus\"]}]",
      "[{}]",
      "[null]",
      "{\"permission\":\"Map.Edit\"}",
      "["})
  @DisplayName("US-17 AC-2 a body that breaks the fixed list or repeats a value is refused with 400")
  void invalidBodiesAreRefused(String body) {
    var event = openEvent();
    var user = admin(event);
    var team = teamOf(event, user, "Control room");

    ApiClient.assertProblem(put(event, team, user, body), 400);

    assertThat(storedPermissions(team)).isEmpty();
    assertThat(changes(event)).hasSize(1);
  }

  @Test
  @DisplayName("US-17 AC-2 more than the eight permissions of the fixed list is refused with 400")
  void moreThanEightIsRefused() {
    var event = openEvent();
    var user = admin(event);
    var team = teamOf(event, user, "Control room");
    var nine = "[" + String.join(",", java.util.Collections.nCopies(9, TRIAGE)) + "]";

    ApiClient.assertProblem(put(event, team, user, nine), 400);
  }

  @Test
  @DisplayName("US-17 AC-4 an empty array takes every permission away, and the member loses it on the next call")
  void emptyArrayTakesPermissionsAway(CapturedOutput output) {
    var event = openEvent();
    var user = admin(event);
    var team = teamOf(event, user, "Control room");
    put(event, team, user, "[" + TRIAGE + "]");
    var reader = UUID.randomUUID();
    copies.upsertIfNewer(new Membership(event, reader, Role.RegularUser, Status.Active, Set.of(team), 1));
    assertThat(list(event, reader).statusCode()).isEqualTo(200);

    var response = put(event, team, user, "[]");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(ApiClient.json(response).get("permissions")).isEqualTo(List.of());
    assertThat(storedPermissions(team)).isEmpty();
    ApiClient.assertProblem(list(event, reader), 403);
    assertThat(changes(event)).hasSize(3).last().satisfies(payload ->
        assertThat(payload).containsEntry("permissions", List.of()).containsEntry("version", 3));
    assertThat(logLines(output)).anySatisfy(line -> {
      assertThat(line).containsEntry("userId", user.toString()).containsEntry("teamId", team.toString())
          .containsEntry("eventId", event.toString());
      assertThat(line.get("message").toString()).contains("Replaced a team's permissions");
      assertThat(line.get("permissions")).isEqualTo("[]");
    });
  }

  @Test
  @DisplayName("US-17 AC-4 the change is recorded with who made it and when, and never with a name")
  void changeIsRecordedWithWhoAndWhen(CapturedOutput output) {
    var event = openEvent();
    var user = admin(event);
    var team = teamOf(event, user, "Control room");

    put(event, team, user, "[" + REVIEW_HAZARD + "]");

    var row = jdbc.sql("SELECT updated_by, updated_at FROM team WHERE team_id = :id").param("id", team)
        .query().singleRow();
    assertThat(row.get("updated_by")).isEqualTo(user);
    assertThat(row.get("updated_at")).isNotNull();
    assertThat(logLines(output)).anySatisfy(line -> {
      assertThat(line).containsEntry("userId", user.toString());
      // the report types read in the order of their fixed list, not the order the caller sent
      assertThat(line.get("permissions")).isEqualTo("[Reports.Review[NoWater, Hazard]]");
      assertThat(line).containsKey("correlationId").containsKey("at");
    });
    assertThat(output.getOut()).doesNotContain("rita@farol.test");
  }

  @Test
  @DisplayName("US-17 AC-1 the same set sent twice is answered twice, each with the next version")
  void sameSetTwice() {
    var event = openEvent();
    var user = admin(event);
    var team = teamOf(event, user, "Control room");

    assertThat(put(event, team, user, "[" + TRIAGE + "]").statusCode()).isEqualTo(200);
    var second = put(event, team, user, "[" + TRIAGE + "]");

    assertThat(second.statusCode()).isEqualTo(200);
    assertThat(storedPermissions(team)).hasSize(1);
    assertThat(changes(event)).hasSize(3).last().satisfies(payload ->
        assertThat(payload).containsEntry("version", 3));
  }

  @Test
  @DisplayName("US-17 AC-1 two teams may review the same report type and handle the same category")
  void twoTeamsMayShareATypeAndACategory() {
    var event = openEvent();
    var user = admin(event);
    var first = teamOf(event, user, "Control room");
    var second = teamOf(event, user, "Night shift");

    assertThat(put(event, first, user, "[" + REVIEW_HAZARD + "]").statusCode()).isEqualTo(200);
    assertThat(put(event, second, user, "[" + REVIEW_HAZARD + "]").statusCode()).isEqualTo(200);
    for (var team : List.of(first, second)) {
      var patched = api().send("PATCH", "/api/v1/events/" + event + "/teams/" + team,
          "Bearer " + JWKS.valid(user.toString()),
          "{\"name\":\"" + (team.equals(first) ? "Control room" : "Night shift")
              + "\",\"handledCategories\":[\"Health\"]}", "Content-Type", JSON_TYPE);
      assertThat(patched.statusCode()).isEqualTo(200);
    }

    assertThat(ApiClient.jsonList(list(event, user))).hasSize(2).allSatisfy(team ->
        assertThat(team).containsEntry("handledCategories", List.of("Health")));
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @CsvSource({"Suspended,Published", "Closed,"})
  @DisplayName("US-17 AC-1 a suspended or closed event refuses a permission change")
  void frozenEventRefuses(String status, String previous) {
    var open = openEvent();
    var user = admin(open);
    var team = teamOf(open, user, "Control room");
    jdbc.sql("UPDATE event SET status = :status, previous_status = :previous WHERE event_id = :id")
        .param("status", status).param("previous", previous == null || previous.isEmpty() ? null : previous)
        .param("id", open).update();

    var response = put(open, team, user, "[" + TRIAGE + "]");

    ApiClient.assertProblem(response, 409);
    assertThat(ApiClient.json(response).get("detail")).isEqualTo("This event is not open to changes.");
    assertThat(storedPermissions(team)).isEmpty();
  }

  @Test
  @DisplayName("US-17 AC-1 a RegularUser, even of a team with every permission, cannot change permissions")
  void regularUserCannotChangePermissions() {
    var event = openEvent();
    var user = admin(event);
    var team = teamOf(event, user, "Control room");
    put(event, team, user, "[" + TRIAGE + "]");
    var member = UUID.randomUUID();
    copies.upsertIfNewer(new Membership(event, member, Role.RegularUser, Status.Active, Set.of(team), 1));

    ApiClient.assertProblem(put(event, team, member, "[" + REVIEW_HAZARD + "]"), 403);

    assertThat(storedPermissions(team)).containsExactly(
        Map.of("permission", "HelpRequests.Triage", "reportTypes", List.of()));
  }

  @Test
  @DisplayName("US-17 AC-1 an unknown event, an unknown team, a non-member and a malformed id are refused")
  void unknownAndMalformedAreRefused() {
    var event = openEvent();
    var user = admin(event);
    var team = teamOf(event, user, "Control room");

    ApiClient.assertProblem(put(UUID.randomUUID(), team, user, "[" + TRIAGE + "]"), 404);
    ApiClient.assertProblem(put(event, UUID.randomUUID(), user, "[" + TRIAGE + "]"), 404);
    ApiClient.assertProblem(put(event, team, UUID.randomUUID(), "[" + TRIAGE + "]"), 404);
    ApiClient.assertProblem(put(event, "not-a-uuid", user, "[" + TRIAGE + "]"), 400);
    assertThat(storedPermissions(team)).isEmpty();
  }

  @Test
  @DisplayName("US-17 AC-1 a deleted team has no permissions to change")
  void deletedTeamIsRefused() {
    var event = openEvent();
    var user = admin(event);
    var team = teamOf(event, user, "Control room");
    put(event, team, user, "[" + TRIAGE + "]");
    assertThat(api().send("DELETE", "/api/v1/events/" + event + "/teams/" + team,
        "Bearer " + JWKS.valid(user.toString())).statusCode()).isEqualTo(204);

    var response = put(event, team, user, "[" + REVIEW_HAZARD + "]");

    ApiClient.assertProblem(response, 404);
    assertThat(ApiClient.json(response).get("detail")).isEqualTo("Team not found.");
    // the rows the deleted team had are kept, so TeamChanged could carry what it granted (US-16 D5)
    assertThat(storedPermissions(team)).hasSize(1);
  }

  @Test
  @DisplayName("US-17 AC-3 the handled categories of a team reach TeamChanged, which is what picks the default "
      + "team when a request is routed")
  void handledCategoriesReachTheEvent() {
    var event = openEvent();
    var user = admin(event);
    var team = teamOf(event, user, "Nursing");

    var patched = api().send("PATCH", "/api/v1/events/" + event + "/teams/" + team,
        "Bearer " + JWKS.valid(user.toString()),
        "{\"name\":\"Nursing\",\"handledCategories\":[\"Health\",\"LostPerson\"]}", "Content-Type", JSON_TYPE);
    put(event, team, user, "[{\"permission\":\"HelpRequests.Respond\"}]");

    assertThat(patched.statusCode()).isEqualTo(200);
    assertThat(changes(event)).hasSize(3).last().satisfies(payload -> {
      assertThat(payload).containsEntry("handledCategories", List.of("Health", "LostPerson"));
      assertThat(payload.get("permissions")).isEqualTo(List.of(
          Map.of("permission", "HelpRequests.Respond", "reportTypes", List.of())));
      assertThat(payload).containsEntry("deleted", false);
    });
  }
}
