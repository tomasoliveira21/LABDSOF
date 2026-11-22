package pt.isep.labdsof.transports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.context.ConfigurableApplicationContext;
import pt.isep.labdsof.transports.application.SourceImportService;

/** The import's metrics (spec US-40 AC-7, D10), read from the application's MeterRegistry. */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MetricsIT {

  private static final WireMockAdmin WIREMOCK = new WireMockAdmin(Infrastructure.wiremockUrl());
  private static ConfigurableApplicationContext transports;
  private static String unavailable;

  @BeforeAll
  static void start() throws SQLException {
    try (var connection = DriverManager.getConnection(Infrastructure.POSTGRES.getJdbcUrl(),
        Infrastructure.POSTGRES.getUsername(), Infrastructure.POSTGRES.getPassword())) {
      connection.createStatement().execute("DELETE FROM source_import");
    }
    // The read at start-up fails, so there is no valid import yet
    unavailable = WIREMOCK.failure("{\"status\":503}");
    transports = Infrastructure.startTransports(Map.of(
        "farol.transport.source.enabled", "true",
        "farol.transport.source.url", Infrastructure.wiremockUrl(),
        "farol.transport.source.interval", "1h",
        "farol.transport.source.retry.first-wait", "10ms"));
    await().atMost(Duration.ofSeconds(20)).until(() -> count("unavailable") >= 1);
  }

  @AfterAll
  static void stop() {
    if (unavailable != null) {
      WIREMOCK.remove(unavailable);
    }
    transports.close();
  }

  private static MeterRegistry meters() {
    return transports.getBean(MeterRegistry.class);
  }

  private static double count(String result) {
    var counter = meters().find("farol.transport.import").tag("result", result).counter();
    return counter == null ? 0 : counter.count();
  }

  private static double dataAge() {
    return meters().get("farol.transport.data.age").gauge().value();
  }

  @Test
  @Order(1)
  @DisplayName("US-40 AC-7 before any valid import the data age is NaN")
  void noDataAgeBeforeAValidImport() {
    assertThat(dataAge()).isNaN();
  }

  @Test
  @Order(2)
  @DisplayName("US-40 AC-7 each run counts its result")
  void eachRunCountsItsResult() {
    assertThat(count("unavailable")).isEqualTo(1);
    WIREMOCK.remove(unavailable);
    unavailable = null;
    transports.getBean(CircuitBreakerRegistry.class).circuitBreaker("transport-source").reset();
    WIREMOCK.resetScenarios();

    transports.getBean(SourceImportService.class).runOnce();

    assertThat(count("succeeded")).isEqualTo(1);
    assertThat(count("unavailable")).isEqualTo(1);
    assertThat(count("invalid")).isZero();
  }

  @Test
  @Order(3)
  @DisplayName("US-40 AC-7 after a valid import the data age is the seconds since it")
  void dataAgeAfterAValidImport() {
    assertThat(dataAge()).isBetween(0.0, 30.0);
  }

  @Test
  @Order(4)
  @DisplayName("US-40 AC-7 the circuit breaker transport-source reports its state")
  void circuitBreakerReportsItsState() {
    assertThat(meters().find("resilience4j.circuitbreaker.state").tag("name", "transport-source").gauges())
        .isNotEmpty();
    assertThat(meters().find("resilience4j.retry.calls").tag("name", "transport-source").functionCounters())
        .isNotEmpty();
  }
}
