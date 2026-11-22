package pt.isep.labdsof.reports;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.ConfigurableApplicationContext;
import pt.isep.labdsof.reports.adapters.out.ai.ResilienceTriageCircuit;
import pt.isep.labdsof.reports.domain.Category;
import pt.isep.labdsof.reports.domain.Membership.Role;
import pt.isep.labdsof.reports.domain.Permission;
import pt.isep.labdsof.reports.domain.Team;
import tools.jackson.databind.json.JsonMapper;

/**
 * US-36 (SC5) end to end: with the AI stopped, slow, failing or answering invalid data, every help request still
 * reaches the control room at once, without category or priority, `NotTriaged` with its reason, in order of
 * arrival; the circuit opens and the AI gets no more calls; the status tells the back office; each fallback is
 * counted (spec US-36 §5, D10). A local stub HTTP server plays the AI (as WireMock would, `architecture.md` §9.2).
 */
class TriageFallbackIT extends IntegrationTest {

  static final JsonMapper JSON = JsonMapper.builder().build();
  static final int REQUESTS = 20;
  /** Calls before the circuit opens: minimum 4 calls, at least half failed (J1). */
  static final int CALLS_TO_OPEN = 4;
  static final Duration OPEN = Duration.ofSeconds(5);

  /** How the stub AI behaves. */
  enum Mode {
    VALID(null), SLOW("Timeout"), UNAVAILABLE_503("ProviderError"), DROPPED_CONNECTION("ProviderError"),
    INVALID_BODY("InvalidOutput");

    final String reason;

    Mode(String reason) {
      this.reason = reason;
    }
  }

  static volatile Mode mode = Mode.VALID;
  static final List<String> CALLS = new CopyOnWriteArrayList<>();
  static InetSocketAddress address;
  static HttpServer ai;
  static ConfigurableApplicationContext reports;

  final UUID event = UUID.randomUUID();
  final String participant = "participant-" + UUID.randomUUID();
  final String operator = "operator-" + UUID.randomUUID();
  final String marker = UUID.randomUUID().toString();

  static HttpServer startAi(InetSocketAddress at) throws IOException {
    var server = HttpServer.create(at, 0);
    server.createContext("/api/chat", TriageFallbackIT::answer);
    server.setExecutor(Executors.newCachedThreadPool());
    server.start();
    return server;
  }

  static void answer(HttpExchange exchange) throws IOException {
    var body = new String(exchange.getRequestBody().readAllBytes(), UTF_8);
    CALLS.add(JSON.readTree(body).get("messages").get(1).get("content").asString());
    var current = mode;
    if (current == Mode.DROPPED_CONNECTION) {
      exchange.close(); // no status line: the connection is closed under the client
      return;
    }
    if (current == Mode.SLOW) {
      TriageIT.sleep(3000); // over the 2 s response timeout of this test
    }
    var content = current == Mode.INVALID_BODY
        ? "{\"category\":\"Fire\",\"priority\":\"High\",\"reason\":\"Fogo.\",\"abstain\":false}"
        : "{\"category\":\"Health\",\"priority\":\"High\",\"reason\":\"Desmaio relatado.\",\"abstain\":false}";
    var reply = JSON.writeValueAsBytes(Map.of("done", true, "message", Map.of("role", "assistant", "content", content)));
    exchange.sendResponseHeaders(current == Mode.UNAVAILABLE_503 ? 503 : 200, reply.length);
    try (var out = exchange.getResponseBody()) {
      out.write(reply);
    } catch (IOException e) {
      // the client gave up after its timeout
    }
  }

  @BeforeAll
  static void start() throws Exception {
    ai = startAi(new InetSocketAddress("127.0.0.1", 0));
    address = ai.getAddress();
    reports = Infrastructure.startReports(Map.of(
        "farol.triage.enabled", "true",
        "farol.outbox.enabled", "true",
        "farol.outbox.poll-interval", "100ms",
        "farol.ai.url", "http://127.0.0.1:" + address.getPort(),
        "farol.ai.response-timeout", "2s",
        "resilience4j.circuitbreaker.instances.ai-provider.wait-duration-in-open-state", OPEN.toSeconds() + "s"));
  }

  @AfterAll
  static void stop() {
    reports.close();
    ai.stop(0);
  }

  static CircuitBreaker circuit() {
    return reports.getBean(CircuitBreakerRegistry.class).circuitBreaker(ResilienceTriageCircuit.NAME);
  }

  static double fallbacks(String reason) {
    var counter = reports.getBean(MeterRegistry.class).find("farol.triage.fallback").tag("reason", reason).counter();
    return counter == null ? 0 : counter.count();
  }

  static double suggested() {
    var counter = reports.getBean(MeterRegistry.class).find("farol.triage.suggested").counter();
    return counter == null ? 0 : counter.count();
  }

  /** Every earlier message is triaged before the circuit is reset, so a case starts from a closed, empty circuit. */
  @BeforeEach
  void controlRoom() throws Exception {
    port = Integer.parseInt(reports.getEnvironment().getProperty("local.server.port"));
    var team = UUID.randomUUID();
    copies.upsertIfNewer(new Team(team, event, "Control room",
        List.of(new Team.TeamPermission(Permission.HELP_REQUESTS_TRIAGE, Set.of())), Set.of(Category.Health),
        false, 1));
    member(event, participant, Role.RegularUser);
    member(event, operator, Role.RegularUser, team);
    mode = Mode.VALID;
    circuit().reset();
    var drained = submit("drain " + marker);
    await().atMost(Duration.ofSeconds(35)).pollInterval(Duration.ofMillis(200))
        .until(() -> !"Pending".equals(JsonPath.read(read(drained), "$.triage.state")));
    circuit().reset();
  }

  String path() {
    return "/api/v1/events/" + event + "/help-requests";
  }

  String submit(String description) throws Exception {
    var created = post(path(), JWKS.valid(participant), submitBody(UUID.randomUUID(), description));
    assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
    return JsonPath.read(created.body(), "$.id");
  }

  String read(String id) throws Exception {
    var response = get(path() + "/" + id, JWKS.valid(operator));
    assertThat(response.statusCode()).isEqualTo(200);
    return response.body();
  }

  String status() throws Exception {
    var response = get("/api/v1/events/" + event + "/triage-status", JWKS.valid(operator));
    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    return response.body();
  }

  long calls(String text) {
    return CALLS.stream().filter(c -> c.contains(text)).count();
  }

  /** Submits {@code count} requests at once; returns their ids in order of submission. */
  List<String> submitAll(String label, int count) throws Exception {
    var ids = new ArrayList<String>();
    for (int i = 0; i < count; i++) {
      ids.add(submit(label + " " + marker + " #" + i));
    }
    return ids;
  }

  /** The control room's first load right after the submissions, then each request once the worker has run. */
  List<String> handledByTheWorker(List<String> ids) throws Exception {
    var list = get(path(), JWKS.valid(operator));
    assertThat(list.statusCode()).isEqualTo(200);
    List<String> listed = JsonPath.read(list.body(), "$.items[*].id");
    assertThat(listed).as("every request is listed at once, in order of arrival").containsSubsequence(ids);
    var bodies = new ArrayList<String>();
    for (var id : ids) {
      bodies.add(await().atMost(Duration.ofSeconds(35)).pollInterval(Duration.ofMillis(200))
          .until(() -> read(id), body -> JsonPath.read(body, "$.triage.notTriagedReason") != null
              || "Suggested".equals(JsonPath.read(body, "$.triage.state"))));
    }
    return bodies;
  }

  /** Each request as the operator sees it: description, no category or priority, NotTriaged with its reason. */
  static List<String> reasons(List<String> bodies, String label) {
    var reasons = new ArrayList<String>();
    for (var body : bodies) {
      assertThat(JsonPath.<String>read(body, "$.description")).startsWith(label);
      assertThat(JsonPath.<Object>read(body, "$.category")).isNull();
      assertThat(JsonPath.<Object>read(body, "$.priority")).isNull();
      assertThat(JsonPath.<String>read(body, "$.triage.state")).isEqualTo("NotTriaged");
      reasons.add(JsonPath.read(body, "$.triage.notTriagedReason"));
    }
    return reasons;
  }

  static List<String> expected(String reason) {
    var expected = new ArrayList<>(Collections.nCopies(CALLS_TO_OPEN, reason));
    expected.addAll(Collections.nCopies(REQUESTS - CALLS_TO_OPEN, "Unavailable"));
    return expected;
  }

  @ParameterizedTest
  @EnumSource(value = Mode.class, names = "VALID", mode = EnumSource.Mode.EXCLUDE)
  @DisplayName("US-36 AC-1 a slow, failing or invalid AI leaves every request to the operator")
  void failingAi(Mode failure) throws Exception {
    mode = failure;
    var label = failure.name();
    var ids = submitAll(label, REQUESTS);

    var reasons = reasons(handledByTheWorker(ids), label);

    assertThat(reasons).as("100 % shown to the operator, with the reason").containsExactlyElementsOf(
        expected(failure.reason));
    assertThat(calls(label + " " + marker)).as("the circuit stops the calls").isEqualTo(CALLS_TO_OPEN);
    assertThat(circuit().getState()).isEqualTo(CircuitBreaker.State.OPEN);
    var status = status();
    assertThat(JsonPath.<Boolean>read(status, "$.available")).isFalse();
    assertThat(JsonPath.<String>read(status, "$.reason")).isEqualTo(failure.reason);
  }

  @Test
  @DisplayName("US-36 AC-1 with the AI stopped every request reaches the operator in order of arrival")
  void aiStopped() throws Exception {
    ai.stop(0);
    try {
      var ids = submitAll("stopped", REQUESTS);

      assertThat(reasons(handledByTheWorker(ids), "stopped")).containsExactlyElementsOf(expected("ProviderError"));
      List<String> listed = JsonPath.read(get(path(), JWKS.valid(operator)).body(), "$.items[*].id");
      assertThat(listed).containsSubsequence(ids);
      assertThat(JsonPath.<Boolean>read(status(), "$.available")).isFalse();
    } finally {
      ai = startAi(address);
    }
  }

  @Test
  @DisplayName("US-36 AC-2 the status reads unavailable while the AI is down and available after a successful probe")
  void recovery() throws Exception {
    assertThat(JsonPath.<Boolean>read(status(), "$.available")).isTrue();
    mode = Mode.UNAVAILABLE_503;
    var missed = reasons(handledByTheWorker(submitAll("down", CALLS_TO_OPEN + 1)), "down");
    assertThat(missed).containsExactly("ProviderError", "ProviderError", "ProviderError", "ProviderError",
        "Unavailable");
    assertThat(JsonPath.<Boolean>read(status(), "$.available")).isFalse();

    mode = Mode.VALID;
    TriageIT.sleep(OPEN.plusSeconds(1).toMillis());
    var probe = submit("back " + marker);
    var body = await().atMost(Duration.ofSeconds(35)).pollInterval(Duration.ofMillis(200))
        .until(() -> read(probe), b -> !"Pending".equals(JsonPath.read(b, "$.triage.state")));

    assertThat(JsonPath.<String>read(body, "$.triage.state")).isEqualTo("Suggested");
    assertThat(circuit().getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    var status = status();
    assertThat(JsonPath.<Boolean>read(status, "$.available")).isTrue();
    assertThat(JsonPath.<Object>read(status, "$.reason")).isNull();
  }

  @Test
  @DisplayName("US-36 AC-3 every fallback is counted by reason")
  void counted() throws Exception {
    var invalid = fallbacks("InvalidOutput");
    var unavailable = fallbacks("Unavailable");
    var ok = suggested();
    handledByTheWorker(submitAll("valid", 1));
    circuit().reset(); // so the next 4 calls alone open it
    mode = Mode.INVALID_BODY;
    reasons(handledByTheWorker(submitAll("invalid", CALLS_TO_OPEN + 2)), "invalid");

    assertThat(suggested() - ok).isEqualTo(1);
    assertThat(fallbacks("InvalidOutput") - invalid).isEqualTo(CALLS_TO_OPEN);
    assertThat(fallbacks("Unavailable") - unavailable).isEqualTo(2);
  }
}
