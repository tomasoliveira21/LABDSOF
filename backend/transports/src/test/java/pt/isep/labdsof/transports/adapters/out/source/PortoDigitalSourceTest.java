package pt.isep.labdsof.transports.adapters.out.source;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pt.isep.labdsof.transports.application.SourceReading;
import pt.isep.labdsof.transports.domain.BusReading;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class PortoDigitalSourceTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  /** One entity of the recordings (snapshot 1, bus 1126), its observation time fixed. */
  private static final String RECORDED = """
      {"id":"urn:ngsi-ld:Vehicle:porto:stcp:bus:1126","type":"Vehicle",\
      "annotations":{"type":"Array","value":["stcp:route:205","stcp:nr_turno:na",\
      "stcp:nr_viagem:205_0_1|325|D3|T2|N10","stcp:sentido:0"],"metadata":{}},\
      "bearing":{"type":"Number","value":220,"metadata":{}},\
      "dataProvider":{"type":"Text","value":"STCP","metadata":{}},\
      "fleetVehicleId":{"type":"Text","value":"1126","metadata":{}},\
      "location":{"type":"geo:json","value":{"type":"Point","coordinates":[-8.688138962,41.168491364]},\
      "metadata":{}},\
      "name":{"type":"Text","value":"STCP 205 1126","metadata":{}},\
      "observationDateTime":{"type":"DateTime","value":"2026-10-26T20:02:11Z","metadata":{}},\
      "vehicleType":{"type":"Text","value":"bus","metadata":{}}}""";

  /** The one reading of an answer holding only {@code entity}. */
  private static BusReading reading(String entity) {
    var parsed = PortoDigitalSource.parse(("[" + entity + "]").getBytes(UTF_8), JSON);
    assertThat(parsed).isInstanceOf(SourceReading.Answered.class);
    return ((SourceReading.Answered) parsed).buses().getFirst();
  }

  /** The recorded entity with one attribute replaced, or removed with {@code null}. */
  private static String recordedWith(String attribute, String json) {
    var tree = (ObjectNode) JSON.readTree(RECORDED);
    if (json == null) {
      tree.remove(attribute);
    } else {
      tree.set(attribute, JSON.readTree(json));
    }
    return tree.toString();
  }

  @Test
  @DisplayName("US-40 D6 the query asks the stubs' geo-query for up to 1000 buses")
  void queryIsTheGeoQueryOfTheStubs() {
    assertThat(PortoDigitalSource.query("41.1724,-8.6841", 2000)).isEqualTo(
        "/v2/entities?q=vehicleType==bus&georel=near;maxDistance:2000&geometry=point&coords=41.1724,-8.6841"
            + "&limit=1000");
  }

  @Test
  @DisplayName("193 AC-6 the source URL may end with a slash")
  void urlWithATrailingSlash() {
    assertThat(PortoDigitalSource.uri("http://wiremock:8080/", "41.1724,-8.6841", 2000)).hasToString(
        "http://wiremock:8080/v2/entities?q=vehicleType==bus&georel=near;maxDistance:2000&geometry=point"
            + "&coords=41.1724,-8.6841&limit=1000");
    assertThat(PortoDigitalSource.uri("http://wiremock:8080", "41.1724,-8.6841", 2000).getRawQuery()).isEqualTo(
        "q=vehicleType==bus&georel=near;maxDistance:2000&geometry=point&coords=41.1724,-8.6841&limit=1000");
  }

  @Test
  @DisplayName("US-40 D7 an entity of the recordings becomes a reading with every field")
  void recordedEntityIsRead() {
    assertThat(reading(RECORDED)).isEqualTo(new BusReading(0, "urn:ngsi-ld:Vehicle:porto:stcp:bus:1126", "STCP",
        "1126", "205", -8.688138962, 41.168491364, Instant.parse("2026-10-26T20:02:11Z")));
  }

  @Test
  @DisplayName("US-40 D7 every entity of a list becomes a reading, numbered from 0, and no entity is no reading")
  void everyEntityIsRead() {
    var parsed = (SourceReading.Answered) PortoDigitalSource.parse(("[" + RECORDED + ","
        + RECORDED.replace("bus:1126", "bus:2001") + "]").getBytes(UTF_8), JSON);
    assertThat(parsed.buses()).extracting(BusReading::position, BusReading::externalEntityId).containsExactly(
        tuple(0, "urn:ngsi-ld:Vehicle:porto:stcp:bus:1126"), tuple(1, "urn:ngsi-ld:Vehicle:porto:stcp:bus:2001"));
    assertThat(PortoDigitalSource.parse("[]".getBytes(UTF_8), JSON)).isEqualTo(SourceReading.answered(List.of(), 1));
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(delimiter = '|', textBlock = """
      the first route annotation | {"type":"Array","value":["stcp:sentido:0","stcp:route:500","stcp:route:205"]} | 500
      a route with nothing after it | {"type":"Array","value":["stcp:route:"]}                                    |
      no route annotation           | {"type":"Array","value":["stcp:sentido:0"]}                                 |
      an empty list                 | {"type":"Array","value":[]}                                                 |
      not a list                    | {"type":"Text","value":"stcp:route:205"}                                     |
      non-text values               | {"type":"Array","value":[205,{"route":"205"},null]}                         |
      no value                      | {"type":"Array"}                                                            |
      """)
  @DisplayName("US-40 D7 the first stcp:route annotation is the route, and anything else leaves the route missing")
  void routeFromAnnotations(String name, String annotations, String route) {
    assertThat(reading(recordedWith("annotations", annotations)).routeId()).isEqualTo(route);
  }

  @Test
  @DisplayName("US-40 AC-2 annotations missing leave the route missing")
  void noAnnotations() {
    assertThat(reading(recordedWith("annotations", null)).routeId()).isNull();
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(delimiter = '|', textBlock = """
      coordinates as strings | {"type":"geo:json","value":{"type":"Point","coordinates":["-8.6","41.1"]}}
      one number             | {"type":"geo:json","value":{"type":"Point","coordinates":[-8.6]}}
      a missing coordinate   | {"type":"geo:json","value":{"type":"Point","coordinates":[-8.6,null]}}
      not a Point            | {"type":"geo:json","value":{"type":"LineString","coordinates":[-8.6,41.1]}}
      no coordinates         | {"type":"geo:json","value":{"type":"Point"}}
      no value               | {"type":"geo:json"}
      """)
  @DisplayName("US-40 AC-2 coordinates as strings, one number, a missing coordinate or a location that is not a Point "
      + "leave the position missing")
  void positionMissing(String name, String location) {
    var read = reading(recordedWith("location", location));
    assertThat(read.longitude()).isNull();
    assertThat(read.latitude()).isNull();
  }

  @Test
  @DisplayName("US-40 D7 a GeoJSON point with an altitude keeps its longitude and latitude")
  void pointWithAltitude() {
    var read = reading(recordedWith("location",
        "{\"type\":\"geo:json\",\"value\":{\"type\":\"Point\",\"coordinates\":[-8.6,41.1,95.0]}}"));
    assertThat(read.longitude()).isEqualTo(-8.6);
    assertThat(read.latitude()).isEqualTo(41.1);
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource({"2026-10-26T20:02:11Z", "2026-10-26T20:02:11.00Z", "2026-10-26T21:02:11+01:00"})
  @DisplayName("US-40 D7 observation times with .00Z, without a fraction and with an offset are the same instant")
  void observationTimes(String value) {
    var read = reading(recordedWith("observationDateTime", "{\"type\":\"DateTime\",\"value\":\"" + value + "\"}"));
    assertThat(read.observedAt()).isEqualTo(Instant.parse("2026-10-26T20:02:11Z"));
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(delimiter = '|', textBlock = """
      unreadable | {"type":"DateTime","value":"yesterday"}
      not text   | {"type":"DateTime","value":1761508931}
      """)
  @DisplayName("US-40 D7 an unreadable or missing observation time is missing")
  void unreadableObservationTime(String name, String attribute) {
    assertThat(reading(recordedWith("observationDateTime", attribute)).observedAt()).isNull();
    assertThat(reading(recordedWith("observationDateTime", null)).observedAt()).isNull();
  }

  @Test
  @DisplayName("US-40 AC-6 text holding a NUL character, which PostgreSQL cannot store, is read as missing, for the "
      + "rules to reject or leave out")
  void textWithANulIsMissing() {
    var id = reading(RECORDED.replace("bus:1126\",\"type\"", "bus:1126\\u0000\",\"type\""));
    var provider = reading(recordedWith("dataProvider", "{\"value\":\"\\u0000STCP\"}"));
    var fleet = reading(recordedWith("fleetVehicleId", "{\"value\":\"11\\u000026\"}"));
    var route = reading(recordedWith("annotations", "{\"value\":[\"stcp:route:205\\u0000\"]}"));

    assertThat(id.externalEntityId()).isNull();
    assertThat(provider.dataProvider()).isNull();
    assertThat(fleet.fleetVehicleId()).isNull();
    assertThat(route.routeId()).isNull();
    assertThat(List.of(id.dataProvider(), provider.externalEntityId(), fleet.routeId(), route.fleetVehicleId()))
        .containsExactly("STCP", "urn:ngsi-ld:Vehicle:porto:stcp:bus:1126", "205", "1126");
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(delimiter = '|', textBlock = """
      no id      | {"type":"Vehicle"}
      an id of 7 | {"id":7}
      """)
  @DisplayName("US-40 D7 an id that is missing or not text is read as missing, for the rules to reject")
  void idMissing(String name, String entity) {
    assertThat(reading(entity).externalEntityId()).isNull();
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(delimiter = '|', textBlock = """
      an object      | {}                | not a JSON array
      HTML           | <html>down</html> | not JSON
      truncated JSON | [{"id":"a"        | not JSON
      a number       | [1]               | entity 0 is not an object
      null           | null              | not a JSON array
      """)
  @DisplayName("193 AC-6 a body that is not a list of NGSI entities is an invalid reading with its reason")
  void otherBodiesAreInvalid(String name, String body, String reason) {
    var reading = PortoDigitalSource.parse(body.getBytes(UTF_8), JSON);

    assertThat(reading).isInstanceOfSatisfying(SourceReading.Invalid.class,
        invalid -> assertThat(invalid.reason()).startsWith(reason));
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
    assertThat(PortoDigitalSource.parse(null, JSON)).isEqualTo(SourceReading.invalid("empty body", 1));
    assertThat(PortoDigitalSource.parse(new byte[0], JSON)).isEqualTo(SourceReading.invalid("empty body", 1));
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
    assertThat(new TransportSourceProperties(true, url, "41.1724,-8.6841", 2000, Duration.ofSeconds(3),
        Duration.ofSeconds(5)).url()).isEqualTo(url);
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
    assertThat(new TransportSourceProperties(false, null, "41.1724,-8.6841", 2000, Duration.ofSeconds(3),
        Duration.ofSeconds(5)).enabled()).isFalse();
  }
}
