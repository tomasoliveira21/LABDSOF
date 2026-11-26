package pt.isep.labdsof.transports;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import pt.isep.labdsof.transports.domain.EventCopy;
import pt.isep.labdsof.transports.domain.EventStatus;
import pt.isep.labdsof.transports.domain.Membership;
import pt.isep.labdsof.transports.domain.Membership.Role;
import pt.isep.labdsof.transports.domain.Membership.Status;
import pt.isep.labdsof.transports.domain.Permission;
import pt.isep.labdsof.transports.domain.Team;

/** Spec US-41 AC-2 to AC-4, D2 to D4, D15: adding an external bus route through the API. */
@ExtendWith(OutputCaptureExtension.class)
class TransportOptionsIT extends IntegrationTest {

  private static final String ROUTE_500 = "{\"mode\":\"ExternalBus\",\"externalRoute\":\"500\"}";

  private UUID event(EventStatus status) {
    var eventId = UUID.randomUUID();
    copies.upsertIfNewer(new EventCopy(eventId, status, 1));
    return eventId;
  }

  private UUID member(UUID eventId, Role role, Status status, UUID... teams) {
    var userId = UUID.randomUUID();
    copies.upsertIfNewer(new Membership(eventId, userId, role, status, Set.of(teams), 1));
    return userId;
  }

  private UUID team(UUID eventId, boolean deleted, Permission... permissions) {
    var teamId = UUID.randomUUID();
    copies.upsertIfNewer(new Team(teamId, eventId, Set.of(permissions), deleted, 1));
    return teamId;
  }

  private HttpResponse<String> post(UUID eventId, UUID userId, String body, String... headers) {
    var all = new String[headers.length + 2];
    all[0] = "Content-Type";
    all[1] = "application/json";
    System.arraycopy(headers, 0, all, 2, headers.length);
    return new ApiClient(port).send("POST", "/api/v1/events/" + eventId + "/transport-options",
        "Bearer " + JWKS.valid(userId.toString()), body, all);
  }

  private static String detail(HttpResponse<String> response) {
    return (String) ApiClient.json(response).get("detail");
  }

  /** The Problem without its {@code instance}, which names the request's path. */
  private static Map<String, Object> problem(HttpResponse<String> response) {
    var body = new HashMap<>(ApiClient.json(response));
    body.remove("instance");
    return body;
  }

  @Test
  @DisplayName("US-41 AC-2 an EventAdmin adds a route")
  void eventAdminAdds() {
    var eventId = event(EventStatus.Published);
    var admin = member(eventId, Role.EventAdmin, Status.Active);

    var response = post(eventId, admin, "{\"mode\":\"ExternalBus\",\"externalRoute\":\"500\",\"name\":\"Hospital\"}");

    assertThat(response.statusCode()).isEqualTo(201);
    var body = ApiClient.json(response);
    assertThat(body).containsEntry("mode", "ExternalBus").containsEntry("externalRoute", "500")
        .containsEntry("name", "Hospital").containsEntry("stopPoiIds", List.of())
        .containsEntry("departures", List.of());
    var optionId = (String) body.get("optionId");
    assertThat(response.headers().firstValue("Location"))
        .contains("/api/v1/events/" + eventId + "/transport-options/" + optionId);
    assertThat(jdbc.sql("""
        SELECT mode || ' ' || external_route || ' ' || version || ' ' || last_updated_by
        FROM transport_option WHERE option_id = :id
        """).param("id", UUID.fromString(optionId)).query(String.class).single())
        .isEqualTo("ExternalBus 500 1 " + admin);
  }

  @Test
  @DisplayName("US-41 AC-2 a member of a team with Transport.Edit adds a route; one without it gets 403")
  void teamPermission() {
    var eventId = event(EventStatus.Draft);
    var logistics = member(eventId, Role.RegularUser, Status.Active, team(eventId, false, Permission.TRANSPORT_EDIT));
    var mapper = member(eventId, Role.RegularUser, Status.Active, team(eventId, false, Permission.MAP_EDIT));
    var deleted = member(eventId, Role.RegularUser, Status.Active, team(eventId, true, Permission.TRANSPORT_EDIT));

    assertThat(post(eventId, logistics, ROUTE_500).statusCode()).isEqualTo(201);
    var refused = post(eventId, mapper, ROUTE_500);
    ApiClient.assertProblem(refused, 403);
    assertThat(detail(refused)).isEqualTo("No permission in this event.");
    ApiClient.assertProblem(post(eventId, deleted, ROUTE_500), 403);
  }

  @Test
  @DisplayName("US-41 AC-3 an outsider, a removed member, an unknown event and a suspended event get the same 404")
  void sameNotFound() {
    var eventId = event(EventStatus.Published);
    var suspended = event(EventStatus.Suspended);
    var removed = member(eventId, Role.EventAdmin, Status.Removed);
    var suspendedAdmin = member(suspended, Role.EventAdmin, Status.Active);

    var answers = List.of(post(eventId, UUID.randomUUID(), ROUTE_500), post(eventId, removed, ROUTE_500),
        post(UUID.randomUUID(), removed, ROUTE_500), post(suspended, suspendedAdmin, ROUTE_500));

    answers.forEach(response -> ApiClient.assertProblem(response, 404));
    assertThat(answers.stream().map(TransportOptionsIT::problem).distinct()).hasSize(1);
    assertThat(detail(answers.getFirst())).isEqualTo("Event not found.");
  }

  @Test
  @DisplayName("US-41 D3 a closed event refuses a new route")
  void closed() {
    var eventId = event(EventStatus.Closed);

    var response = post(eventId, member(eventId, Role.EventAdmin, Status.Active), ROUTE_500);

    ApiClient.assertProblem(response, 409);
    assertThat(detail(response)).isEqualTo("This event is closed.");
  }

  @Test
  @DisplayName("US-41 D4 \" 500\" then \"500\" is the same route")
  void trimmedDuplicate() {
    var eventId = event(EventStatus.Published);
    var admin = member(eventId, Role.EventAdmin, Status.Active);

    var first = post(eventId, admin, "{\"mode\":\"ExternalBus\",\"externalRoute\":\" 500\"}");
    var second = post(eventId, admin, ROUTE_500);

    assertThat(first.statusCode()).isEqualTo(201);
    assertThat(ApiClient.json(first)).containsEntry("externalRoute", "500").doesNotContainKey("name");
    ApiClient.assertProblem(second, 409);
    assertThat(detail(second)).isEqualTo("This route is already on the event.");
  }

  @Test
  @DisplayName("US-41 D4 a 21st route gets 409")
  void twentyFirst() {
    var eventId = event(EventStatus.Published);
    var admin = member(eventId, Role.EventAdmin, Status.Active);
    for (var i = 0; i < 20; i++) {
      assertThat(post(eventId, admin, "{\"mode\":\"ExternalBus\",\"externalRoute\":\"r" + i + "\"}").statusCode())
          .isEqualTo(201);
    }

    var response = post(eventId, admin, "{\"mode\":\"ExternalBus\",\"externalRoute\":\"r20\"}");

    ApiClient.assertProblem(response, 409);
    assertThat(detail(response)).isEqualTo("This event already has 20 routes.");
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @CsvSource(delimiter = '|', textBlock = """
      stops          | {"mode":"ExternalBus","externalRoute":"500","stopPoiIds":["11111111-2222-3333-4444-555555555555"]} | Stops arrive with US-37.
      departures     | {"mode":"ExternalBus","externalRoute":"500","departures":[{"destination":"Trindade","scheduledAt":"2027-05-08T23:14:05Z"}]} | Departures are for shuttles (US-37).
      blank route    | {"mode":"ExternalBus","externalRoute":"   "} | The route is blank.
      no route       | {"mode":"ExternalBus"} | The route is blank.
      blank name     | {"mode":"ExternalBus","externalRoute":"500","name":"  "} | The name is blank.
      unknown field  | {"mode":"ExternalBus","externalRoute":"500","version":2} |
      21 characters  | {"mode":"ExternalBus","externalRoute":"123456789012345678901"} |
      """)
  @DisplayName("US-41 D2 stops, departures, a blank or missing route, a blank name, an unknown field and a "
      + "21-character route get 400")
  void badRequests(String name, String body, String expectedDetail) {
    var eventId = event(EventStatus.Published);

    var response = post(eventId, member(eventId, Role.EventAdmin, Status.Active), body);

    ApiClient.assertProblem(response, 400);
    if (expectedDetail != null) {
      assertThat(detail(response)).isEqualTo(expectedDetail);
    }
  }

  @Test
  @DisplayName("US-41 D2 a shuttle answers 501 naming US-37")
  void shuttle() {
    var eventId = event(EventStatus.Published);

    var response = post(eventId, member(eventId, Role.EventAdmin, Status.Active), "{\"mode\":\"Shuttle\",\"name\":\"A\"}");

    ApiClient.assertProblem(response, 501);
    assertThat(detail(response)).isEqualTo("This operation arrives with US-37.");
  }

  @Test
  @DisplayName("US-41 D3 body checks come before access: a non-member sending stops gets 400, a shuttle 404")
  void order() {
    var eventId = event(EventStatus.Published);
    var outsider = UUID.randomUUID();

    ApiClient.assertProblem(post(eventId, outsider,
        "{\"mode\":\"ExternalBus\",\"externalRoute\":\"500\",\"stopPoiIds\":[\"11111111-2222-3333-4444-555555555555\"]}"),
        400);
    ApiClient.assertProblem(post(eventId, outsider, "{\"mode\":\"Shuttle\",\"name\":\"A\"}"), 404);
  }

  @Test
  @DisplayName("US-41 AC-4 adding a route logs one line with the correlation id")
  void logged(CapturedOutput output) {
    var eventId = event(EventStatus.Published);
    var admin = member(eventId, Role.EventAdmin, Status.Active);

    var response = post(eventId, admin, ROUTE_500, "X-Correlation-Id", "us41-route-500-log");

    var optionId = ApiClient.json(response).get("optionId");
    assertThat(logLines(output)).filteredOn(line -> "us41-route-500-log".equals(line.get("correlationId"))
            && "INFO".equals(line.get("level")) && optionId.equals(line.get("optionId")))
        .singleElement()
        .satisfies(line -> assertThat(line).containsEntry("eventId", eventId.toString())
            .containsEntry("externalRoute", "500").containsEntry("userId", admin.toString()));
  }
}
