package pt.isep.labdsof.transports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static pt.isep.labdsof.transports.NgsiFixtures.answer;
import static pt.isep.labdsof.transports.NgsiFixtures.ok;
import static pt.isep.labdsof.transports.NgsiFixtures.vehicle;

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
import pt.isep.labdsof.transports.application.BusData;
import pt.isep.labdsof.transports.application.SourceImportService;
import pt.isep.labdsof.transports.application.SourceImports;
import pt.isep.labdsof.transports.domain.ImportOutcome;

/** The import end to end: the simulator, the rules, the buses in use and the record (spec US-40 AC-1 to AC-4). */
class ImportIT {

  private static final WireMockAdmin WIREMOCK = new WireMockAdmin(Infrastructure.wiremockUrl());
  private static final String BAD = "urn:ngsi-ld:Vehicle:porto:stcp:bus:9999";
  private static ConfigurableApplicationContext transports;

  private final List<String> stubs = new ArrayList<>();

  @BeforeAll
  static void start() {
    WIREMOCK.resetScenarios();
    var started = Instant.now();
    transports = Infrastructure.startTransports(Map.of(
        "farol.transport.source.enabled", "true",
        "farol.transport.source.url", Infrastructure.wiremockUrl(),
        "farol.transport.source.interval", "1h"));
    // The read at start-up runs on the scheduler's thread: wait for it, so no test's stub can reach it
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
  }

  @AfterEach
  void removeStubs() {
    stubs.forEach(WIREMOCK::remove);
  }

  private static SourceImportService imports() {
    return transports.getBean(SourceImportService.class);
  }

  private static BusData buses() {
    return transports.getBean(BusData.class);
  }

  private static JdbcClient jdbc() {
    return transports.getBean(JdbcClient.class);
  }

  private void answers(String body) {
    stubs.add(WIREMOCK.failure(ok(body)));
  }

  private static long runs() {
    return jdbc().sql("SELECT count(*) FROM source_import").query(Long.class).single();
  }

  @Test
  @DisplayName("US-40 AC-1 the recorded answer is stored: every bus in use with the time of the import, which is the "
      + "run's finished_at")
  void recordedAnswerIsStored() {
    var run = imports().runOnce();

    assertThat(run.sourceImport().outcome()).isEqualTo(ImportOutcome.Succeeded);
    assertThat(run.sourceImport().vehicleCount()).isBetween(2, 13);
    assertThat(buses().inUse()).hasSize(run.sourceImport().vehicleCount())
        .allSatisfy(bus -> assertThat(bus.routeId()).isNotBlank());
    assertThat(jdbc().sql("""
        SELECT count(*) FROM bus_observation
        WHERE retrieved_at <> (SELECT finished_at FROM source_import ORDER BY import_id DESC LIMIT 1)
        """).query(Long.class).single()).isZero();
  }

  @Test
  @DisplayName("US-40 AC-2 an answer with one broken bus is recorded Invalid naming it, and the previous buses stay in "
      + "use")
  void brokenBusKeepsThePreviousBuses() {
    imports().runOnce();
    var previous = buses().inUse();
    var now = Instant.now();
    answers(answer(vehicle("urn:ngsi-ld:Vehicle:porto:stcp:bus:1001", "205", -8.68, 41.17, now),
        vehicle(BAD, null, -8.67, 41.16, now)));

    var run = imports().runOnce();

    assertThat(run.sourceImport().outcome()).isEqualTo(ImportOutcome.Invalid);
    assertThat(run.sourceImport().error()).isEqualTo("entity 1 (" + BAD + "): no route");
    assertThat(buses().inUse()).isNotEmpty().isEqualTo(previous);
  }

  @Test
  @DisplayName("US-40 AC-3 an answer of late buses only is Succeeded with 0 and leaves no bus in use")
  void lateBusesOnly() {
    imports().runOnce();
    answers(answer(vehicle("urn:ngsi-ld:Vehicle:porto:stcp:bus:1002", "205", -8.68, 41.17,
        Instant.now().minus(Duration.ofMinutes(10)))));

    var run = imports().runOnce();

    assertThat(run.sourceImport().outcome()).isEqualTo(ImportOutcome.Succeeded);
    assertThat(run.sourceImport().vehicleCount()).isZero();
    assertThat(run.late()).isEqualTo(1);
    assertThat(buses().inUse()).isEmpty();
  }

  @Test
  @DisplayName("US-40 AC-4 an empty answer leaves no bus in use")
  void emptyAnswer() {
    imports().runOnce();
    answers("[]");

    var run = imports().runOnce();

    assertThat(run.sourceImport().outcome()).isEqualTo(ImportOutcome.Succeeded);
    assertThat(buses().inUse()).isEmpty();
  }

  @Test
  @DisplayName("US-40 AC-2 a database failure while storing changes nothing and records no run")
  void databaseFailureChangesNothing() {
    imports().runOnce();
    var previous = buses().inUse();
    var before = runs();

    whileWritesFail("BEFORE INSERT OR UPDATE ON bus_observation FOR EACH ROW", () ->
        assertThatThrownBy(() -> imports().runOnce()).isInstanceOf(DataAccessException.class)
            .hasMessageContaining("database unavailable"));

    assertThat(runs()).isEqualTo(before);
    assertThat(buses().inUse()).isEqualTo(previous);
  }

  @Test
  @DisplayName("US-40 D4 the buses and the record are one transaction: when the record fails after the buses are "
      + "written, the previous buses stay in use")
  void recordFailureRollsTheBusesBack() {
    imports().runOnce();
    var previous = buses().inUse();
    var before = runs();
    answers(answer(vehicle("urn:ngsi-ld:Vehicle:porto:stcp:bus:1003", "205", -8.68, 41.17, Instant.now())));

    whileWritesFail("BEFORE INSERT ON source_import FOR EACH ROW WHEN (NEW.outcome = 'Succeeded')", () ->
        assertThatThrownBy(() -> imports().runOnce()).isInstanceOf(DataAccessException.class)
            .hasMessageContaining("database unavailable"));

    assertThat(runs()).isEqualTo(before);
    assertThat(buses().inUse()).isNotEmpty().isEqualTo(previous);
  }

  /** Runs {@code check} while a trigger ({@code when}: its timing, table and condition) makes those writes fail. */
  private static void whileWritesFail(String when, Runnable check) {
    var n = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    var table = when.replaceAll(".* ON (\\w+) .*", "$1");
    jdbc().sql("CREATE FUNCTION test_fail_" + n + "() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN "
        + "RAISE EXCEPTION 'test: database unavailable'; END $$").update();
    jdbc().sql("CREATE TRIGGER test_fail_" + n + " " + when + " EXECUTE FUNCTION test_fail_" + n + "()").update();
    try {
      check.run();
    } finally {
      jdbc().sql("DROP TRIGGER IF EXISTS test_fail_" + n + " ON " + table).update();
      jdbc().sql("DROP FUNCTION IF EXISTS test_fail_" + n + "()").update();
    }
  }
}
