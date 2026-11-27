package pt.isep.labdsof.transports;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pt.isep.labdsof.transports.application.BusData;
import pt.isep.labdsof.transports.application.SourceImports;
import pt.isep.labdsof.transports.application.TransportOptions;
import pt.isep.labdsof.transports.domain.BusRoute;
import pt.isep.labdsof.transports.domain.EventCopy;
import pt.isep.labdsof.transports.domain.EventStatus;
import pt.isep.labdsof.transports.domain.ImportOutcome;
import pt.isep.labdsof.transports.domain.Membership;
import pt.isep.labdsof.transports.domain.Membership.Role;
import pt.isep.labdsof.transports.domain.Membership.Status;
import pt.isep.labdsof.transports.domain.ObservedBus;
import pt.isep.labdsof.transports.domain.SourceImport;

/** Spec US-41 AC-1, AC-3, D5 to D8: a member reads the buses on the event's routes near the venue. */
class BusObservationsIT extends IntegrationTest {

  @Autowired private BusData buses;
  @Autowired private SourceImports imports;
  @Autowired private TransportOptions options;
  @Autowired private PlatformTransactionManager transactions;

  /** The buses in use and the import record are shared by every test: each test starts with none. */
  @BeforeEach
  void noImportYet() {
    jdbc.sql("DELETE FROM source_import").update();
    buses.replace(List.of(), Instant.now());
  }

  private UUID event(EventStatus status) {
    var eventId = UUID.randomUUID();
    copies.upsertIfNewer(new EventCopy(eventId, status, 1));
    return eventId;
  }

  private UUID member(UUID eventId, Role role, Status status) {
    var userId = UUID.randomUUID();
    copies.upsertIfNewer(new Membership(eventId, userId, role, status, Set.of(), 1));
    return userId;
  }

  private void route(UUID eventId, String number, String name, Instant at) {
    new TransactionTemplate(transactions).executeWithoutResult(status -> options.addBusRoute(
        new BusRoute(UUID.randomUUID(), eventId, number, name, at, UUID.randomUUID()), 20));
  }

  private static ObservedBus bus(String id, String fleet, String route, double latitude, Instant observedAt) {
    return new ObservedBus(id, "STCP", fleet, route, -8.6841, latitude, observedAt);
  }

  /** A valid import of {@code inUse}, finished at {@code at}. */
  private void imported(Instant at, ObservedBus... inUse) {
    buses.replace(List.of(inUse), at);
    imports.record(new SourceImport(at.minusMillis(300), at, ImportOutcome.Succeeded, inUse.length, null));
  }

  private HttpResponse<String> get(UUID eventId, UUID userId) {
    return new ApiClient(port).send("GET", "/api/v1/events/" + eventId + "/bus-observations",
        "Bearer " + JWKS.valid(userId.toString()), null);
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> list(Map<String, Object> body, String key) {
    return (List<Map<String, Object>>) body.get(key);
  }

  private static Map<String, Object> problem(HttpResponse<String> response) {
    var body = new HashMap<>(ApiClient.json(response));
    body.remove("instance");
    return body;
  }

  @Test
  @DisplayName("US-41 AC-1 a member sees the buses on the event's routes near the venue with the time of their "
      + "position")
  void memberSeesTheEventsBuses() {
    var eventId = event(EventStatus.Published);
    var now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    route(eventId, "500", null, now.minusSeconds(60));
    route(eventId, "502", "Hospital S. João", now.minusSeconds(30));
    var observed = now.minusSeconds(57);
    imported(now.minusSeconds(20), bus("stcp:far", "3398", "502", 41.1824, observed),
        bus("stcp:near", "3412", "500", 41.1734, observed), bus("stcp:other", "1126", "200", 41.1725, observed));

    var response = get(eventId, member(eventId, Role.RegularUser, Status.Active));

    assertThat(response.statusCode()).isEqualTo(200);
    var body = ApiClient.json(response);
    assertThat(body).containsEntry("venue", Map.of("longitude", -8.6841, "latitude", 41.1724))
        .containsEntry("stale", false);
    assertThat(Instant.parse((String) body.get("dataAsOf"))).isEqualTo(now.minusSeconds(20));
    assertThat(list(body, "routes")).containsExactly(Map.of("externalRoute", "500", "busCount", 1),
        Map.of("externalRoute", "502", "name", "Hospital S. João", "busCount", 1));
    var shown = list(body, "buses");
    assertThat(shown).extracting(b -> b.get("vehicleId")).containsExactly("stcp:near", "stcp:far");
    assertThat(shown.getFirst()).containsEntry("fleetVehicleId", "3412").containsEntry("routeId", "500")
        .containsEntry("distanceMetres", 111)
        .containsEntry("position", Map.of("longitude", -8.6841, "latitude", 41.1734));
    assertThat(Instant.parse((String) shown.getFirst().get("observedAt"))).isEqualTo(observed);
  }

  @Test
  @DisplayName("US-41 D6 a route the source never sent shows no bus")
  void routeWithoutBuses() {
    var eventId = event(EventStatus.Published);
    var now = Instant.now();
    route(eventId, "999", null, now);
    imported(now, bus("stcp:a", "1", "500", 41.1734, now.minusSeconds(30)));

    var body = ApiClient.json(get(eventId, member(eventId, Role.EventAdmin, Status.Active)));

    assertThat(list(body, "routes")).containsExactly(Map.of("externalRoute", "999", "busCount", 0));
    assertThat(list(body, "buses")).isEmpty();
  }

  @Test
  @DisplayName("US-41 D6 a bus without a fleet number is shown without it")
  void noFleetNumber() {
    var eventId = event(EventStatus.Published);
    var now = Instant.now();
    route(eventId, "500", null, now);
    imported(now, bus("stcp:nofleet", null, "500", 41.1734, now.minusSeconds(30)));

    var shown = list(ApiClient.json(get(eventId, member(eventId, Role.RegularUser, Status.Active))), "buses");

    assertThat(shown).singleElement().satisfies(b -> assertThat(b).doesNotContainKey("fleetVehicleId")
        .containsEntry("vehicleId", "stcp:nofleet"));
  }

  @Test
  @DisplayName("US-41 D6 no routes give no buses; routes but no valid import yet give stale and no data time")
  void emptyStates() {
    var noRoutes = event(EventStatus.Published);
    var noImport = event(EventStatus.Published);
    route(noImport, "500", null, Instant.now());

    var empty = ApiClient.json(get(noRoutes, member(noRoutes, Role.RegularUser, Status.Active)));
    var waiting = ApiClient.json(get(noImport, member(noImport, Role.RegularUser, Status.Active)));

    assertThat(list(empty, "routes")).isEmpty();
    assertThat(list(empty, "buses")).isEmpty();
    assertThat(waiting).doesNotContainKey("dataAsOf").containsEntry("stale", true);
    assertThat(list(waiting, "routes")).containsExactly(Map.of("externalRoute", "500", "busCount", 0));
  }

  @Test
  @DisplayName("US-41 D5 a RegularUser of a draft gets 404, its EventAdmin 200; a closed event still answers 200")
  void eventStatuses() {
    var draft = event(EventStatus.Draft);
    var closed = event(EventStatus.Closed);

    ApiClient.assertProblem(get(draft, member(draft, Role.RegularUser, Status.Active)), 404);
    assertThat(get(draft, member(draft, Role.EventAdmin, Status.Active)).statusCode()).isEqualTo(200);
    assertThat(get(closed, member(closed, Role.RegularUser, Status.Active)).statusCode()).isEqualTo(200);
  }

  @Test
  @DisplayName("US-41 AC-3 an outsider, a removed member, an unknown event and a suspended event get the same 404")
  void sameNotFound() {
    var eventId = event(EventStatus.Published);
    var suspended = event(EventStatus.Suspended);

    var answers = List.of(get(eventId, UUID.randomUUID()), get(eventId, member(eventId, Role.EventAdmin, Status.Removed)),
        get(UUID.randomUUID(), member(eventId, Role.EventAdmin, Status.Active)),
        get(suspended, member(suspended, Role.EventAdmin, Status.Active)));

    answers.forEach(response -> ApiClient.assertProblem(response, 404));
    assertThat(answers.stream().map(BusObservationsIT::problem).distinct()).hasSize(1);
  }

  @Test
  @ExtendWith(OutputCaptureExtension.class)
  @DisplayName("US-14 AC-1 transport reads refuse and audit an administrator from a different event")
  void auditCrossEventRead(CapturedOutput output) {
    var first = event(EventStatus.Published);
    var second = event(EventStatus.Published);
    var admin = member(first, Role.EventAdmin, Status.Active);
    ApiClient.assertProblem(get(second, admin), 404);
    assertThat(output.getAll().lines().filter(line -> line.contains("Transport event access refused"))
        .filter(line -> line.contains(admin.toString())).toList())
        .singleElement().satisfies(line -> assertThat(line).contains(second.toString(), "EVENT_NOT_VISIBLE", "\"at\""));
  }

  @Test
  @DisplayName("US-41 D6 the next import replaces the buses shown")
  void nextImportReplaces() {
    var eventId = event(EventStatus.Published);
    var member = member(eventId, Role.RegularUser, Status.Active);
    var now = Instant.now();
    route(eventId, "500", null, now);
    imported(now.minusSeconds(30), bus("stcp:old", "1", "500", 41.1734, now.minusSeconds(60)));
    assertThat(list(ApiClient.json(get(eventId, member)), "buses")).extracting(b -> b.get("vehicleId"))
        .containsExactly("stcp:old");

    imported(now, bus("stcp:new", "2", "500", 41.1744, now.minusSeconds(20)));

    assertThat(list(ApiClient.json(get(eventId, member)), "buses")).extracting(b -> b.get("vehicleId"))
        .containsExactly("stcp:new");
  }
}
