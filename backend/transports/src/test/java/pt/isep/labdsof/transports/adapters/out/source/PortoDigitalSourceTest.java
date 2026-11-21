package pt.isep.labdsof.transports.adapters.out.source;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pt.isep.labdsof.transports.application.SourceReading;
import tools.jackson.databind.json.JsonMapper;

class PortoDigitalSourceTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Test
  @DisplayName("193 AC-6 the query is the geo-query the WireMock stubs expect")
  void queryIsTheGeoQueryOfTheStubs() {
    assertThat(PortoDigitalSource.query("41.1724,-8.6841", 2000)).isEqualTo(
        "/v2/entities?q=vehicleType==bus&georel=near;maxDistance:2000&geometry=point&coords=41.1724,-8.6841");
  }

  @Test
  @DisplayName("193 AC-6 the source URL may end with a slash")
  void urlWithATrailingSlash() {
    assertThat(PortoDigitalSource.uri("http://wiremock:8080/", "41.1724,-8.6841", 2000)).hasToString(
        "http://wiremock:8080/v2/entities?q=vehicleType==bus&georel=near;maxDistance:2000&geometry=point"
            + "&coords=41.1724,-8.6841");
    assertThat(PortoDigitalSource.uri("http://wiremock:8080", "41.1724,-8.6841", 2000).getRawQuery())
        .isEqualTo("q=vehicleType==bus&georel=near;maxDistance:2000&geometry=point&coords=41.1724,-8.6841");
  }

  @ParameterizedTest(name = "{1} vehicles")
  @CsvSource(delimiter = '|', textBlock = """
      []                                                   | 0
      [{"id":"urn:ngsi-ld:Vehicle:porto:stcp:bus:1126"}]   | 1
      [{"id":"a","type":"Vehicle"},{"id":"b","extra":[1]}] | 2
      """)
  @DisplayName("193 AC-6 a JSON array of entities with an id is a successful reading with their count")
  void entitiesAreCounted(String body, int count) {
    assertThat(PortoDigitalSource.parse(body.getBytes(UTF_8), JSON)).isEqualTo(SourceReading.succeeded(count));
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(delimiter = '|', textBlock = """
      an object                 | {}                              | not a JSON array
      HTML                      | <html>down</html>               | not JSON
      truncated JSON            | [{"id":"a"                      | not JSON
      a number                  | [1]                             | entity 0 is not an object
      an entity without id      | [{"id":"a"},{"type":"Vehicle"}] | entity 1 has no id
      an id that is not text    | [{"id":7}]                      | entity 0 has no id
      a blank id                | [{"id":"  "}]                   | entity 0 has no id
      null                      | null                            | not a JSON array
      """)
  @DisplayName("193 AC-6 a body that is not a list of NGSI entities is an invalid reading with its reason")
  void otherBodiesAreInvalid(String name, String body, String reason) {
    var reading = PortoDigitalSource.parse(body.getBytes(UTF_8), JSON);

    assertThat(reading.outcome()).hasToString("Invalid");
    assertThat(reading.error()).startsWith(reason);
  }

  @Test
  @DisplayName("193 AC-6 a reason names the root cause and its message, and leaves out a message it does not have")
  void reasonNamesTheRootCause() {
    assertThat(PortoDigitalSource.describe(new IllegalStateException("outer",
        new java.nio.channels.UnresolvedAddressException()))).isEqualTo("UnresolvedAddressException");
    assertThat(PortoDigitalSource.describe(new IllegalStateException("outer",
        new java.io.IOException("Connection refused")))).isEqualTo("IOException: Connection refused");
  }

  @Test
  @DisplayName("193 AC-6 an empty body is an invalid reading")
  void emptyBodyIsInvalid() {
    assertThat(PortoDigitalSource.parse(null, JSON)).isEqualTo(SourceReading.invalid("empty body"));
    assertThat(PortoDigitalSource.parse(new byte[0], JSON)).isEqualTo(SourceReading.invalid("empty body"));
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource({"wiremock:8080", "ftp://wiremock:8080", "http://", "not a url", "/v2/entities", "http//wiremock:8080",
      "http:/v2/entities"})
  @DisplayName("193 D5 the job cannot be switched on with a source URL that is not an absolute http or https URL")
  void malformedUrlIsRefused(String url) {
    assertThatThrownBy(() -> new TransportSourceProperties(true, url, "41.1724,-8.6841", 2000,
        Duration.ofSeconds(3), Duration.ofSeconds(5)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("farol.transport.source.url must be an absolute http or https URL");
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource({"http://wiremock:8080", "https://broker.fiware.urbanplatform.portodigital.pt", "HTTP://127.0.0.1:8090/"})
  @DisplayName("193 D5 an absolute http or https source URL is accepted")
  void httpUrlIsAccepted(String url) {
    assertThat(new TransportSourceProperties(true, url, "41.1724,-8.6841", 2000, Duration.ofSeconds(3), Duration.ofSeconds(5)).url()).isEqualTo(url);
  }

  @Test
  @DisplayName("193 D5 the job cannot be switched on without a source URL")
  void urlIsRequiredWhenEnabled() {
    assertThatThrownBy(() -> new TransportSourceProperties(true, " ", "41.1724,-8.6841", 2000,
        Duration.ofSeconds(3), Duration.ofSeconds(5)))
        .isInstanceOf(IllegalArgumentException.class).hasMessage("farol.transport.source.url is required");
    assertThatThrownBy(() -> new TransportSourceProperties(true, null, "41.1724,-8.6841", 2000,
        Duration.ofSeconds(3), Duration.ofSeconds(5)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(new TransportSourceProperties(false, null, "41.1724,-8.6841", 2000, Duration.ofSeconds(3), Duration.ofSeconds(5)).enabled()).isFalse();
  }
}
