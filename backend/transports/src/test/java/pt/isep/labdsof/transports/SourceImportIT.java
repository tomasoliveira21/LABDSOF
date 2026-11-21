package pt.isep.labdsof.transports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.jdbc.core.simple.JdbcClient;
import pt.isep.labdsof.transports.application.SourceImportService;

/** The scheduled read through the simulator, recorded in source_import (spec AC-6, D5). */
class SourceImportIT {

  private static final WireMockAdmin WIREMOCK = new WireMockAdmin(Infrastructure.wiremockUrl());
  private static Instant started;
  private static ConfigurableApplicationContext transports;

  @BeforeAll
  static void start() {
    WIREMOCK.resetScenarios();
    WIREMOCK.clearJournal();
    started = Instant.now();
    transports = Infrastructure.startTransports(Map.of(
        "farol.transport.source.enabled", "true",
        "farol.transport.source.url", Infrastructure.wiremockUrl(),
        "farol.transport.source.interval", "1h"));
  }

  @AfterAll
  static void stop() {
    transports.close();
  }

  private static JdbcClient jdbc() {
    return transports.getBean(JdbcClient.class);
  }

  @Test
  @DisplayName("193 AC-6 the job reads the recorded buses through WireMock at start-up and records Succeeded")
  void theJobRunsAtStartUp() {
    await().atMost(Duration.ofSeconds(20)).until(() -> jdbc().sql("""
        SELECT count(*) FROM source_import
        WHERE started_at >= :started AND outcome = 'Succeeded' AND vehicle_count BETWEEN 2 AND 13
        """).param("started", java.sql.Timestamp.from(started)).query(Long.class).single() >= 1);

    assertThat(WIREMOCK.requestedUrls()).contains(WireMockAdmin.GEO_QUERY);
  }

  @Test
  @DisplayName("193 AC-6 a failed read is recorded with its reason")
  void aFailedReadIsRecorded() {
    var stub = WIREMOCK.failure("{\"status\":503}");
    try {
      var result = transports.getBean(SourceImportService.class).runOnce();

      var row = jdbc().sql("""
          SELECT outcome, vehicle_count, error FROM source_import ORDER BY import_id DESC LIMIT 1
          """).query().singleRow();
      assertThat(row).containsEntry("outcome", "Unavailable").containsEntry("error", "HTTP 503");
      assertThat(row.get("vehicle_count")).isNull();
      assertThat(result.error()).isEqualTo("HTTP 503");
    } finally {
      WIREMOCK.remove(stub);
    }
  }

  @Test
  @DisplayName("193 AC-6 the job is off when disabled")
  void theJobIsOffWhenDisabled() {
    assertThat(transports.containsBean("sourceImportJob")).isTrue();
    try (var off = Infrastructure.startTransports(Map.of())) {
      assertThat(off.containsBean("sourceImportJob")).isFalse();
    }
  }

  @Test
  @DisplayName("193 D5 start-up fails when the job is on without a source URL")
  void startUpFailsWithoutAUrl() {
    assertThatThrownBy(() -> Infrastructure.startTransports(Map.of("farol.transport.source.enabled", "true",
        "farol.transport.source.url", "")).close())
        .satisfies(e -> assertThat(NestedExceptionUtils.getMostSpecificCause(e))
            .hasMessageContaining("farol.transport.source.url is required"));
  }
}
