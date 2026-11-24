package pt.isep.labdsof.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.jayway.jsonpath.JsonPath;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import pt.isep.labdsof.reports.domain.Category;
import pt.isep.labdsof.reports.domain.Membership;
import pt.isep.labdsof.reports.domain.Membership.Role;
import pt.isep.labdsof.reports.domain.Permission;
import pt.isep.labdsof.reports.domain.Team;

/** US-26: the control room's live list over HTTP, on the real database (spec US-26 §5). */
class LiveListIT extends IntegrationTest {

  final UUID event = UUID.randomUUID();
  final String participant = "participant-" + UUID.randomUUID();
  final String operator = "operator-" + UUID.randomUUID();

  @BeforeEach
  void controlRoom() {
    var team = UUID.randomUUID();
    copies.upsertIfNewer(new Team(team, event, "Control room",
        List.of(new Team.TeamPermission(Permission.HELP_REQUESTS_TRIAGE, Set.of())), Set.of(Category.Health),
        false, 1));
    // The team existed before the requests: a team change would list every open request again (US-34 D6)
    jdbc.sql("UPDATE team_copy SET changed_at = changed_at - interval '1 hour' WHERE team_id = :team")
        .param("team", team).update();
    member(event, participant, Role.RegularUser);
    member(event, operator, Role.RegularUser, team);
  }

  String path() {
    return "/api/v1/events/" + event + "/help-requests";
  }

  String submit(String description) throws Exception {
    var created = post(path(), JWKS.valid(participant), submitBody(UUID.randomUUID(), description));
    assertThat(created.statusCode()).isEqualTo(201);
    return JsonPath.read(created.body(), "$.id");
  }

  /** The list body of one poll by the operator; asserts 200. */
  String poll(String updatedSince) throws Exception {
    var response = get(path() + (updatedSince == null ? "" : "?updatedSince=" + updatedSince),
        JWKS.valid(operator));
    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    return response.body();
  }

  static List<String> ids(String body) {
    return JsonPath.read(body, "$.items[*].id");
  }

  void setTimes(String id, String column, Instant at) {
    jdbc.sql("UPDATE help_request SET " + column + " = :at WHERE id = :id")
        .param("at", Timestamp.from(at)).param("id", UUID.fromString(id)).update();
  }

  @Test
  @DisplayName("US-26 AC-1 a request submitted for the event is in the next poll")
  void nextPoll() throws Exception {
    String serverTime = JsonPath.read(poll(null), "$.serverTime");
    var id = submit("Friend fainted near the bar");

    var body = poll(serverTime);
    assertThat(ids(body)).containsExactly(id);
    Map<String, Object> item = JsonPath.read(body, "$.items[0]");
    assertThat(item).containsKeys("eventId", "category", "location", "priority", "submittedAt",
        "defaultTeamId", "routedTeamId", "triage");
    assertThat(item).containsEntry("description", "Friend fainted near the bar")
        .containsEntry("status", "Submitted").containsEntry("eventId", event.toString());
    assertThat(JsonPath.<String>read(body, "$.items[0].triage.state")).isEqualTo("Pending");
    assertThat(Instant.parse(JsonPath.read(body, "$.serverTime"))).isAfter(Instant.parse(serverTime));
  }

  @Test
  @DisplayName("US-26 AC-1 polling every 2 s shows a new request within 5 s")
  void withinFiveSeconds() throws Exception {
    var cursor = new AtomicReference<String>(JsonPath.read(poll(null), "$.serverTime"));
    var seen = new AtomicReference<Instant>();
    var wanted = new AtomicReference<String>();
    var failure = new AtomicReference<Throwable>();
    var poller = Executors.newSingleThreadScheduledExecutor();
    try {
      // The page polls on its own rhythm, unrelated to when the participant submits (spec D7)
      poller.scheduleAtFixedRate(() -> {
        try {
          var sent = Instant.now(); // when the page asked, so a slow runner's response time does not count
          var body = poll(cursor.get());
          if (wanted.get() != null && ids(body).contains(wanted.get())) {
            seen.compareAndSet(null, sent);
          }
          cursor.set(JsonPath.read(body, "$.serverTime"));
        } catch (Exception | AssertionError e) {
          failure.compareAndSet(null, e); // a failed poll ends the schedule: report it, not a timeout
        }
      }, 0, 2, TimeUnit.SECONDS);
      // Submit between two polls, not right after one
      await().pollDelay(Duration.ofMillis(700)).atMost(Duration.ofSeconds(1)).until(() -> true);
      var submitted = Instant.now();
      wanted.set(submit("Someone is unconscious"));

      // Wait longer than SC2 so a miss fails on the 5 s assertion below, with the measured delay, not a timeout
      await().atMost(Duration.ofSeconds(10)).until(() -> seen.get() != null || failure.get() != null);
      assertThat(failure.get()).isNull();
      assertThat(Duration.between(submitted, seen.get())).isLessThanOrEqualTo(Duration.ofSeconds(5));
    } finally {
      poller.shutdownNow();
    }
  }

  @Test
  @DisplayName("US-26 AC-1 the first load lists the open requests in order of arrival")
  void firstLoad() throws Exception {
    var first = submit("first");
    var closed = submit("closed");
    var third = submit("third");
    jdbc.sql("UPDATE help_request SET status = 'Closed' WHERE id = :id")
        .param("id", UUID.fromString(closed)).update();

    assertThat(ids(poll(null))).containsExactly(first, third);
  }

  @Test
  @DisplayName("US-26 AC-1 a closed request comes back in the changes and leaves the first load")
  void closedComesBack() throws Exception {
    var id = submit("help");
    String serverTime = JsonPath.read(poll(null), "$.serverTime");
    jdbc.sql("UPDATE help_request SET status = 'Closed', updated_at = :at WHERE id = :id")
        .param("at", Timestamp.from(Instant.now())).param("id", UUID.fromString(id)).update();

    var changes = poll(serverTime);
    assertThat(ids(changes)).containsExactly(id);
    assertThat(JsonPath.<String>read(changes, "$.items[0].status")).isEqualTo("Closed");
    assertThat(ids(poll(null))).isEmpty();
  }

  @Test
  @DisplayName("US-26 AC-1 a request committed late is not lost")
  void committedLate() throws Exception {
    var id = submit("help");
    var before = Instant.now().minusSeconds(60);
    setTimes(id, "submitted_at", before);
    setTimes(id, "updated_at", before);
    var polledAt = Instant.now();
    String serverTime = JsonPath.read(poll(null), "$.serverTime");
    // A change stamped before that poll read the table, but committed after it
    setTimes(id, "updated_at", polledAt.minusSeconds(1));

    assertThat(ids(poll(serverTime))).containsExactly(id);
  }

  @Test
  @DisplayName("US-26 AC-1 a request that turns NotTriaged comes back in the changes")
  void turnsNotTriaged() throws Exception {
    var id = submit("help");
    // To the microsecond, as the database stores it (it rounds), so the deadline below is exact
    var submitted = Instant.now().truncatedTo(ChronoUnit.MICROS).minusSeconds(31);
    setTimes(id, "submitted_at", submitted);
    setTimes(id, "updated_at", submitted);

    // Its deadline (submitted + 30 s, a second ago) falls after this updatedSince
    var body = poll(submitted.plusSeconds(25).toString());
    assertThat(ids(body)).containsExactly(id);
    assertThat(JsonPath.<String>read(body, "$.items[0].triage.state")).isEqualTo("NotTriaged");
    assertThat(JsonPath.<String>read(body, "$.items[0].triage.notTriagedReason")).isEqualTo("Timeout");
    // A poll that already saw the deadline does not list it again
    assertThat(ids(poll(submitted.plusSeconds(30).toString()))).isEmpty();
  }

  @Test
  @DisplayName("US-26 AC-1 requests of another event are never listed")
  void otherEvent() throws Exception {
    var otherEvent = UUID.randomUUID();
    member(otherEvent, participant, Role.RegularUser);
    member(otherEvent, operator, Role.EventAdmin);
    post("/api/v1/events/" + otherEvent + "/help-requests", JWKS.valid(participant),
        submitBody(UUID.randomUUID(), "elsewhere"));
    var id = submit("here");

    assertThat(ids(poll(null))).containsExactly(id);
    assertThat(ids(poll(Instant.EPOCH.toString()))).containsExactly(id);
  }

  @Test
  @DisplayName("US-26 AC-1 injection text is listed as plain text")
  void injectionText() throws Exception {
    var text = "'); DROP TABLE help_request; -- <script>alert(1)</script>";
    submit(text);
    assertThat(JsonPath.<String>read(poll(null), "$.items[0].description")).isEqualTo(text);
  }

  @Test
  @DisplayName("US-26 AC-1 a malformed updatedSince is rejected and a future one lists nothing")
  void updatedSinceBounds() throws Exception {
    submit("help");
    assertThat(get(path() + "?updatedSince=yesterday", JWKS.valid(operator)).statusCode()).isEqualTo(400);
    assertThat(get("/api/v1/events/not-a-uuid/help-requests", JWKS.valid(operator)).statusCode())
        .isEqualTo(400);
    assertThat(ids(poll(Instant.now().plusSeconds(3600).toString()))).isEmpty();
    // An offset is accepted too, percent-encoded as the page sends it
    assertThat(ids(poll("2000-01-01T00:00:00%2B01:00"))).hasSize(1);
  }

  @Test
  @DisplayName("US-26 AC-2 a member without HelpRequests.Triage is refused with 403")
  void forbidden() throws Exception {
    submit("help");
    var response = get(path(), JWKS.valid(participant));
    assertThat(response.statusCode()).isEqualTo(403);
    assertThat(response.body()).doesNotContain("help\"");
  }

  @Test
  @DisplayName("US-26 AC-2 a non-member, a removed member or a member of another event gets 404")
  void notFound() throws Exception {
    submit("help");
    var removed = "removed-" + UUID.randomUUID();
    copies.upsertIfNewer(new Membership(event, removed, Role.EventAdmin, Membership.Status.Removed,
        Set.of(), 1));
    var elsewhere = "elsewhere-" + UUID.randomUUID();
    member(UUID.randomUUID(), elsewhere, Role.EventAdmin);
    var unknownEvent = get("/api/v1/events/" + UUID.randomUUID() + "/help-requests", JWKS.valid(operator));
    assertThat(unknownEvent.statusCode()).isEqualTo(404);

    for (var user : List.of("nobody-" + UUID.randomUUID(), removed, elsewhere)) {
      var response = get(path(), JWKS.valid(user));
      assertThat(response.statusCode()).as(user).isEqualTo(404);
      // Same answer as for an event that does not exist: existence is not revealed
      assertThat(response.body().replace(event.toString(), ""))
          .isEqualTo(unknownEvent.body().replaceAll("events/[0-9a-f-]{36}", "events/"));
    }
  }

  static Stream<String> badTokens() {
    return Stream.of(null, JWKS.expired("operator"), JWKS.wrongSignature("operator"),
        JWKS.wrongIssuer("operator"), JWKS.wrongAudience("operator"), "not-a-jwt");
  }

  @ParameterizedTest
  @MethodSource("badTokens")
  @DisplayName("US-26 AC-2 every bad token gets 401")
  void badToken(String token) throws Exception {
    assertThat(get(path(), token).statusCode()).isEqualTo(401);
  }
}
