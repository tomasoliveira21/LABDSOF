package pt.isep.labdsof.transports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static pt.isep.labdsof.transports.NgsiFixtures.ok;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import pt.isep.labdsof.transports.application.ImportRun;
import pt.isep.labdsof.transports.application.SourceImportService;
import pt.isep.labdsof.transports.application.SourceImports;
import pt.isep.labdsof.transports.domain.ImportOutcome;

/** The retry and the circuit breaker of J1 around the source (spec US-40 AC-5, D5), with short waits. */
class ResilienceIT {

  private static final WireMockAdmin WIREMOCK = new WireMockAdmin(Infrastructure.wiremockUrl());
  private static final Duration OPEN = Duration.ofSeconds(2);
  private static ConfigurableApplicationContext transports;

  private final List<String> stubs = new ArrayList<>();

  @BeforeAll
  static void start() {
    WIREMOCK.resetScenarios();
    var started = Instant.now();
    transports = Infrastructure.startTransports(Map.of(
        "farol.transport.source.enabled", "true",
        "farol.transport.source.url", Infrastructure.wiremockUrl(),
        "farol.transport.source.interval", "1h",
        "farol.transport.source.read-timeout", "300ms",
        "farol.transport.source.retry.first-wait", "50ms",
        "farol.transport.source.circuit-breaker.wait-in-open-state", OPEN.toMillis() + "ms"));
    await().atMost(Duration.ofSeconds(20)).until(() ->
        transports.getBean(SourceImports.class).lastSucceededAt().filter(at -> !at.isBefore(started)).isPresent());
  }

  @AfterAll
  static void stop() {
    transports.close();
  }

  @BeforeEach
  void reset() {
    WIREMOCK.resetScenarios();
    breaker().reset();
    WIREMOCK.clearJournal();
  }

  @AfterEach
  void removeStubs() {
    stubs.forEach(WIREMOCK::remove);
  }

  private static CircuitBreaker breaker() {
    return transports.getBean(CircuitBreakerRegistry.class).circuitBreaker("transport-source");
  }

  private static ImportRun run() {
    return transports.getBean(SourceImportService.class).runOnce();
  }

  private void answers(String response) {
    stubs.add(WIREMOCK.failure(response));
  }

  /** {@code responses} in turn, then the last one again, through a WireMock scenario. */
  private void inTurn(String... responses) {
    var scenario = "turns-" + UUID.randomUUID();
    for (int i = 0; i < responses.length; i++) {
      var state = i == 0 ? "Started" : "turn-" + i;
      var next = i + 1 < responses.length ? ",\"newScenarioState\":\"turn-" + (i + 1) + "\"" : "";
      stubs.add(WIREMOCK.stub("""
          {"priority":1,"scenarioName":"%s","requiredScenarioState":"%s"%s,\
          "request":{"method":"GET","urlPath":"/v2/entities"},"response":%s}"""
          .formatted(scenario, state, next, responses[i])));
    }
  }

  @Test
  @DisplayName("US-40 AC-5 a source that fails twice then answers is imported after 3 calls")
  void failsTwiceThenAnswers() {
    inTurn("{\"status\":503}", "{\"status\":503}", ok("[]"));

    var run = run();

    assertThat(run.sourceImport().outcome()).isEqualTo(ImportOutcome.Succeeded);
    assertThat(run.attempts()).isEqualTo(3);
    assertThat(WIREMOCK.requestedUrls()).hasSize(3);
  }

  @Test
  @DisplayName("US-40 AC-5 three failed calls make the run Unavailable, open the circuit, and the next run calls nothing "
      + "and records circuit open")
  void threeFailuresOpenTheCircuit() {
    answers("{\"status\":503}");

    var failed = run();

    assertThat(failed.sourceImport().outcome()).isEqualTo(ImportOutcome.Unavailable);
    assertThat(failed.sourceImport().error()).isEqualTo("HTTP 503");
    assertThat(failed.attempts()).isEqualTo(3);
    assertThat(breaker().getState()).isEqualTo(CircuitBreaker.State.OPEN);

    WIREMOCK.clearJournal();
    var paused = run();

    assertThat(paused.sourceImport().outcome()).isEqualTo(ImportOutcome.Unavailable);
    assertThat(paused.sourceImport().error()).isEqualTo("circuit open");
    assertThat(paused.attempts()).isZero();
    assertThat(WIREMOCK.requestedUrls()).isEmpty();
  }

  @Test
  @DisplayName("US-40 AC-5 after the open period one trial call closes the circuit when it succeeds")
  void trialCallCloses() {
    var failing = WIREMOCK.failure("{\"status\":503}");
    run();
    WIREMOCK.remove(failing);
    answers(ok("[]"));
    await().pollDelay(OPEN.plusMillis(200)).atMost(OPEN.plusSeconds(2)).until(() -> true);

    var trial = run();

    assertThat(trial.sourceImport().outcome()).isEqualTo(ImportOutcome.Succeeded);
    assertThat(breaker().getState()).isEqualTo(CircuitBreaker.State.CLOSED);
  }

  @Test
  @DisplayName("US-40 AC-5 after the open period one trial call opens the circuit again when it fails")
  void trialCallReopens() {
    answers("{\"status\":503}");
    run();
    await().pollDelay(OPEN.plusMillis(200)).atMost(OPEN.plusSeconds(2)).until(() -> true);
    WIREMOCK.clearJournal();

    var trial = run();

    assertThat(trial.sourceImport().outcome()).isEqualTo(ImportOutcome.Unavailable);
    assertThat(trial.sourceImport().error()).isEqualTo("HTTP 503");
    assertThat(WIREMOCK.requestedUrls()).hasSize(1);
    assertThat(breaker().getState()).isEqualTo(CircuitBreaker.State.OPEN);
  }

  @Test
  @DisplayName("US-40 D5 a 404 is not retried but counts for the circuit")
  void notFoundIsNotRetried() {
    answers("{\"status\":404}");

    var run = run();

    assertThat(run.sourceImport().error()).isEqualTo("HTTP 404");
    assertThat(run.attempts()).isEqualTo(1);
    assertThat(WIREMOCK.requestedUrls()).hasSize(1);
    assertThat(breaker().getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
  }

  @Test
  @DisplayName("US-40 D5 an invalid answer is neither retried nor counted for the circuit")
  void invalidAnswerIsNotCounted() {
    answers(ok("{}"));

    var run = run();

    assertThat(run.sourceImport().outcome()).isEqualTo(ImportOutcome.Invalid);
    assertThat(run.attempts()).isEqualTo(1);
    assertThat(breaker().getMetrics().getNumberOfFailedCalls()).isZero();
    assertThat(breaker().getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(1);
  }

  @Test
  @DisplayName("US-40 AC-5 a source that times out on every attempt ends Unavailable after 3 attempts, inside the 30 s "
      + "interval")
  void timeoutOnEveryAttempt() {
    answers("{\"status\":200,\"body\":\"[]\",\"fixedDelayMilliseconds\":1000}");

    var run = run();
    var took = Duration.between(run.sourceImport().startedAt(), run.sourceImport().finishedAt());

    assertThat(run.sourceImport().outcome()).isEqualTo(ImportOutcome.Unavailable);
    assertThat(run.sourceImport().error()).startsWith("HttpTimeoutException");
    assertThat(run.attempts()).isEqualTo(3);
    assertThat(took).isLessThan(Duration.ofSeconds(30));
  }

  @Test
  @DisplayName("US-40 D5 a database failure while storing does not count against the source's circuit")
  void databaseFailureIsNotTheSources() {
    var jdbc = transports.getBean(JdbcClient.class);
    var n = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    jdbc.sql("CREATE FUNCTION test_fail_" + n + "() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN "
        + "RAISE EXCEPTION 'test: database unavailable'; END $$").update();
    jdbc.sql("CREATE TRIGGER test_fail_" + n + " BEFORE INSERT OR UPDATE ON bus_observation FOR EACH ROW "
        + "EXECUTE FUNCTION test_fail_" + n + "()").update();
    try {
      assertThatThrownBy(ResilienceIT::run).isInstanceOf(DataAccessException.class);
    } finally {
      jdbc.sql("DROP TRIGGER IF EXISTS test_fail_" + n + " ON bus_observation").update();
      jdbc.sql("DROP FUNCTION IF EXISTS test_fail_" + n + "()").update();
    }
    assertThat(breaker().getMetrics().getNumberOfFailedCalls()).isZero();
    assertThat(breaker().getState()).isEqualTo(CircuitBreaker.State.CLOSED);
  }
}
