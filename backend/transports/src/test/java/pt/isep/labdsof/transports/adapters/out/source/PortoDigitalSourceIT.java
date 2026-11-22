package pt.isep.labdsof.transports.adapters.out.source;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pt.isep.labdsof.transports.Infrastructure;
import pt.isep.labdsof.transports.WireMockAdmin;
import pt.isep.labdsof.transports.application.SourceReading;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/** The adapter against the real simulator with the recorded stubs (spec D5, ADR-008, J1 timeouts). */
class PortoDigitalSourceIT {

  private static final String VENUE = "41.1724,-8.6841";
  private static final WireMockAdmin WIREMOCK = new WireMockAdmin(Infrastructure.wiremockUrl());

  private final List<String> stubs = new ArrayList<>();

  private static PortoDigitalSource source(String url, String coords, Duration readTimeout) {
    var properties = new TransportSourceProperties(true, url, coords, 2000, Duration.ofSeconds(3), readTimeout);
    return new PortoDigitalSource(properties, JsonMapper.builder().build(), RestClient.builder());
  }

  private static PortoDigitalSource source() {
    return source(Infrastructure.wiremockUrl(), VENUE, Duration.ofSeconds(5));
  }

  private void failure(String response) {
    stubs.add(WIREMOCK.failure(response));
  }

  @BeforeEach
  void reset() {
    WIREMOCK.resetScenarios();
    WIREMOCK.clearJournal();
  }

  @AfterEach
  void removeStubs() {
    stubs.forEach(WIREMOCK::remove);
  }

  @Test
  @DisplayName("193 AC-6 the recorded buses near the venue are read with the exact geo-query")
  void recordedBusesAreRead() {
    var reading = source().read();

    assertThat(reading).isInstanceOfSatisfying(SourceReading.Answered.class, answered -> {
      assertThat(answered.buses()).hasSizeBetween(2, 13);
      assertThat(answered.buses()).allSatisfy(bus -> {
        assertThat(bus.externalEntityId()).startsWith("urn:ngsi-ld:Vehicle:porto:stcp:bus:");
        assertThat(bus.dataProvider()).isEqualTo("STCP");
        assertThat(bus.routeId()).isNotBlank();
        assertThat(bus.longitude()).isBetween(-8.72, -8.64);
        assertThat(bus.latitude()).isBetween(41.15, 41.19);
        assertThat(bus.observedAt()).isBefore(Instant.now().plusSeconds(1));
      });
    });
    assertThat(WIREMOCK.requestedUrls()).containsExactly(WireMockAdmin.GEO_QUERY);
  }

  @Test
  @DisplayName("193 AC-6 no bus near the venue is a successful reading of 0 vehicles")
  void noBusIsZero() {
    failure("{\"status\":200,\"body\":\"[]\",\"headers\":{\"Content-Type\":\"application/json\"}}");

    assertThat(source().read()).isEqualTo(SourceReading.answered(List.of(), 1));
  }

  @Test
  @DisplayName("193 AC-6 a 503 is an unavailable source")
  void serviceUnavailable() {
    failure("{\"status\":503}");

    assertThat(source().read()).isEqualTo(SourceReading.unavailable("HTTP 503", 1));
  }

  @Test
  @DisplayName("193 AC-6 the coordinates written differently get a 404, read as an unavailable source")
  void otherCoordinatesGet404() {
    var reading = source(Infrastructure.wiremockUrl(), "41.17240,-8.68410", Duration.ofSeconds(5)).read();

    assertThat(reading).isEqualTo(SourceReading.unavailable("HTTP 404", 1));
  }

  @Test
  @DisplayName("193 AC-6 a source slower than the read timeout is unavailable, and the call ends at the timeout")
  void slowSourceTimesOut() {
    failure("{\"status\":200,\"body\":\"[]\",\"fixedDelayMilliseconds\":3000}");

    var started = System.nanoTime();
    var reading = source(Infrastructure.wiremockUrl(), VENUE, Duration.ofMillis(500)).read();
    var elapsed = Duration.ofNanos(System.nanoTime() - started);

    assertThat(reading).isInstanceOfSatisfying(SourceReading.Unavailable.class,
        unavailable -> assertThat(unavailable.reason()).startsWith("HttpTimeoutException"));
    assertThat(elapsed).isLessThan(Duration.ofMillis(2500));
  }

  @Test
  @DisplayName("193 AC-6 a dropped connection is an unavailable source")
  void droppedConnection() {
    failure("{\"fault\":\"CONNECTION_RESET_BY_PEER\"}");

    assertThat(source().read()).isInstanceOf(SourceReading.Unavailable.class);
  }

  @Test
  @DisplayName("193 AC-6 nothing listening at the source URL is an unavailable source")
  void nothingListening() {
    var reading = source("http://127.0.0.1:1", VENUE, Duration.ofSeconds(5)).read();

    assertThat(reading).isInstanceOfSatisfying(SourceReading.Unavailable.class,
        unavailable -> assertThat(unavailable.reason()).isNotBlank());
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(delimiter = '|', textBlock = """
      an object                | {}
      HTML                     | <html>maintenance</html>
      a number in the list     | [1]
      truncated JSON           | [{"id":"a"
      """)
  @DisplayName("193 AC-6 a body that is not a list of NGSI entities is an invalid reading")
  void invalidBodies(String name, String body) {
    failure("{\"status\":200,\"body\":\"" + body.replace("\"", "\\\"") + "\"}");

    assertThat(source().read()).isInstanceOf(SourceReading.Invalid.class);
  }
}
