package pt.isep.labdsof.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.jayway.jsonpath.JsonPath;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
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
import pt.isep.labdsof.reports.domain.Membership;
import pt.isep.labdsof.reports.domain.Membership.Role;

/** US-24: the participant follows their own requests over HTTP, on the real database (spec US-24 §5). */
class FollowRequestIT extends IntegrationTest {

  final UUID event = UUID.randomUUID();
  final String participant = "participant-" + UUID.randomUUID();
  final String other = "other-" + UUID.randomUUID();

  @BeforeEach
  void members() {
    member(event, participant, Role.RegularUser);
    member(event, other, Role.RegularUser);
  }

  String path() {
    return "/api/v1/events/" + event + "/help-requests";
  }

  String submit(String user, String description) throws Exception {
    var created = post(path(), JWKS.valid(user), submitBody(UUID.randomUUID(), description));
    assertThat(created.statusCode()).isEqualTo(201);
    return JsonPath.read(created.body(), "$.id");
  }

  /** The body of one mine=true poll by the participant; asserts 200. */
  String poll(String updatedSince) throws Exception {
    var response = get(path() + "?mine=true" + (updatedSince == null ? "" : "&updatedSince=" + updatedSince),
        JWKS.valid(participant));
    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    return response.body();
  }

  static List<String> ids(String body) {
    return JsonPath.read(body, "$.items[*].id");
  }

  void setStatus(String id, String status) {
    jdbc.sql("UPDATE help_request SET status = :status, updated_at = :at WHERE id = :id")
        .param("status", status).param("at", Timestamp.from(Instant.now())).param("id", UUID.fromString(id))
        .update();
  }

  @Test
  @DisplayName("US-24 AC-1 the owner opens a request and sees its status and the time of the last change")
  void ownerOpens() throws Exception {
    var before = Instant.now().minusSeconds(1);
    var id = submit(participant, "Friend fainted near the bar");

    var response = get(path() + "/" + id, JWKS.valid(participant));
    assertThat(response.statusCode()).isEqualTo(200);
    Map<String, Object> body = JsonPath.read(response.body(), "$");
    // Only what the participant may see: never a category, a priority, the location or the triage
    assertThat(body).containsOnlyKeys("id", "description", "status", "updatedAt")
        .containsEntry("status", "Submitted").containsEntry("description", "Friend fainted near the bar");
    assertThat(Instant.parse((String) body.get("updatedAt"))).isAfter(before);
  }

  @Test
  @DisplayName("US-24 AC-1 every status is shown to the owner")
  void everyStatus() throws Exception {
    var id = submit(participant, "help");
    for (var status : List.of("Submitted", "Seen", "OnTheWay", "Resolved", "Closed")) {
      setStatus(id, status);
      assertThat(JsonPath.<String>read(get(path() + "/" + id, JWKS.valid(participant)).body(), "$.status"))
          .isEqualTo(status);
      assertThat(JsonPath.<String>read(poll(null), "$.items[0].status")).isEqualTo(status);
    }
  }

  @Test
  @DisplayName("US-24 AC-1 another member gets 404 for someone's request")
  void otherMember() throws Exception {
    var id = submit(participant, "help");
    assertThat(get(path() + "/" + id, JWKS.valid(other)).statusCode()).isEqualTo(404);
  }

  @Test
  @DisplayName("US-24 AC-1 mine lists only my requests, closed ones included, in the participant view")
  void mineOnly() throws Exception {
    var first = submit(participant, "first");
    var closed = submit(participant, "closed");
    submit(other, "someone else's");
    var otherEvent = UUID.randomUUID();
    member(otherEvent, participant, Role.RegularUser);
    post("/api/v1/events/" + otherEvent + "/help-requests", JWKS.valid(participant),
        submitBody(UUID.randomUUID(), "elsewhere"));
    setStatus(closed, "Closed");

    var body = poll(null);
    assertThat(ids(body)).containsExactly(first, closed);
    Map<String, Object> item = JsonPath.read(body, "$.items[1]");
    assertThat(item).containsOnlyKeys("id", "description", "status", "updatedAt")
        .containsEntry("status", "Closed");
    assertThat(JsonPath.<String>read(body, "$.serverTime")).isNotBlank();
    assertThat(ids(poll(Instant.EPOCH.toString()))).containsExactly(first, closed);
  }

  @Test
  @DisplayName("US-24 AC-1 a member with no requests gets an empty list")
  void empty() throws Exception {
    submit(other, "someone else's");
    var body = poll(null);
    assertThat(ids(body)).isEmpty();
    assertThat(JsonPath.<String>read(body, "$.serverTime")).isNotBlank();
  }

  @Test
  @DisplayName("US-24 AC-1 mine=false is the control-room list")
  void mineFalse() throws Exception {
    submit(participant, "help");
    assertThat(get(path() + "?mine=false", JWKS.valid(participant)).statusCode()).isEqualTo(403);
  }

  @Test
  @DisplayName("US-24 AC-1 injection text is listed as plain text")
  void injectionText() throws Exception {
    var text = "'); DROP TABLE help_request; -- <script>alert(1)</script>";
    submit(participant, text);
    assertThat(JsonPath.<String>read(poll(null), "$.items[0].description")).isEqualTo(text);
  }

  @Test
  @DisplayName("US-24 AC-1 a non-member, a removed member or a member of another event gets 404")
  void notFound() throws Exception {
    submit(participant, "help");
    var removed = "removed-" + UUID.randomUUID();
    copies.upsertIfNewer(new Membership(event, removed, Role.RegularUser, Membership.Status.Removed,
        Set.of(), 1));
    var elsewhere = "elsewhere-" + UUID.randomUUID();
    member(UUID.randomUUID(), elsewhere, Role.RegularUser);
    var unknownEvent = get("/api/v1/events/" + UUID.randomUUID() + "/help-requests?mine=true",
        JWKS.valid(participant));
    assertThat(unknownEvent.statusCode()).isEqualTo(404);

    for (var user : List.of("nobody-" + UUID.randomUUID(), removed, elsewhere)) {
      var response = get(path() + "?mine=true", JWKS.valid(user));
      assertThat(response.statusCode()).as(user).isEqualTo(404);
      // Same answer as for an event that does not exist: existence is not revealed
      assertThat(response.body().replace(event.toString(), ""))
          .isEqualTo(unknownEvent.body().replaceAll("events/[0-9a-f-]{36}", "events/"));
    }
  }

  static Stream<String> badTokens() {
    return Stream.of(null, JWKS.expired("participant"), JWKS.wrongSignature("participant"),
        JWKS.wrongIssuer("participant"), JWKS.wrongAudience("participant"), "not-a-jwt");
  }

  @ParameterizedTest
  @MethodSource("badTokens")
  @DisplayName("US-24 AC-1 every bad token gets 401")
  void badToken(String token) throws Exception {
    assertThat(get(path() + "?mine=true", token).statusCode()).isEqualTo(401);
  }

  @Test
  @DisplayName("US-24 AC-2 a status change is in the next poll")
  void nextPoll() throws Exception {
    var id = submit(participant, "help");
    var first = poll(null);
    String serverTime = JsonPath.read(first, "$.serverTime");
    String updatedAt = JsonPath.read(first, "$.items[0].updatedAt");
    setStatus(id, "Seen");

    var body = poll(serverTime);
    assertThat(ids(body)).containsExactly(id);
    assertThat(JsonPath.<String>read(body, "$.items[0].status")).isEqualTo("Seen");
    assertThat(Instant.parse(JsonPath.read(body, "$.items[0].updatedAt"))).isAfter(Instant.parse(updatedAt));
    assertThat(Instant.parse(JsonPath.read(body, "$.serverTime"))).isAfter(Instant.parse(serverTime));
  }

  @Test
  @DisplayName("US-24 AC-2 polling every 3 s shows a status change within 5 s")
  void withinFiveSeconds() throws Exception {
    var id = submit(participant, "help");
    var cursor = new AtomicReference<String>(JsonPath.read(poll(null), "$.serverTime"));
    var seen = new AtomicReference<Instant>();
    var changed = new AtomicReference<Boolean>(false);
    var failure = new AtomicReference<Throwable>();
    var poller = Executors.newSingleThreadScheduledExecutor();
    try {
      // The page polls on its own rhythm, unrelated to when the control room changes the status (spec D5)
      poller.scheduleAtFixedRate(() -> {
        try {
          var sent = Instant.now(); // when the page asked, so a slow runner's response time does not count
          var body = poll(cursor.get());
          if (changed.get() && ids(body).contains(id)
              && "OnTheWay".equals(JsonPath.read(body, "$.items[0].status"))) {
            seen.compareAndSet(null, sent);
          }
          cursor.set(JsonPath.read(body, "$.serverTime"));
        } catch (Exception | AssertionError e) {
          failure.compareAndSet(null, e); // a failed poll ends the schedule: report it, not a timeout
        }
      }, 0, 3, TimeUnit.SECONDS);
      // Change it between two polls, not right after one
      await().pollDelay(Duration.ofMillis(1500)).atMost(Duration.ofSeconds(2)).until(() -> true);
      var changedAt = Instant.now();
      setStatus(id, "OnTheWay");
      changed.set(true);

      // Wait longer than 5 s so a miss fails on the assertion below, with the measured delay, not a timeout
      await().atMost(Duration.ofSeconds(10)).until(() -> seen.get() != null || failure.get() != null);
      assertThat(failure.get()).isNull();
      assertThat(Duration.between(changedAt, seen.get())).isLessThanOrEqualTo(Duration.ofSeconds(5));
    } finally {
      poller.shutdownNow();
    }
  }

  @Test
  @DisplayName("US-24 AC-2 a malformed mine or updatedSince is rejected")
  void malformed() throws Exception {
    submit(participant, "help");
    assertThat(get(path() + "?mine=perhaps", JWKS.valid(participant)).statusCode()).isEqualTo(400);
    assertThat(get(path() + "?mine=true&updatedSince=yesterday", JWKS.valid(participant)).statusCode())
        .isEqualTo(400);
    assertThat(ids(poll(Instant.now().plusSeconds(3600).toString()))).isEmpty();
  }
}
