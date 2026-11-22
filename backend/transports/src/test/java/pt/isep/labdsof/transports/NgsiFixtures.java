package pt.isep.labdsof.transports;

import java.time.Instant;
import java.util.Arrays;
import java.util.stream.Collectors;
import tools.jackson.databind.json.JsonMapper;

/** NGSI v2 Vehicle entities as the recorded Porto Digital answers have them (infra/wiremock), for the stubs. */
public final class NgsiFixtures {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private NgsiFixtures() {}

  /** A vehicle with every attribute the import reads; {@code route} null leaves out the route annotation. */
  public static String vehicle(String id, String route, double longitude, double latitude, Instant observedAt) {
    var routeAnnotation = route == null ? "" : "\"stcp:route:" + route + "\",";
    return """
        {"id":"%s","type":"Vehicle",\
        "annotations":{"type":"Array","value":[%s"stcp:nr_turno:na","stcp:sentido:0"],"metadata":{}},\
        "dataProvider":{"type":"Text","value":"STCP","metadata":{}},\
        "fleetVehicleId":{"type":"Text","value":"%s","metadata":{}},\
        "location":{"type":"geo:json","value":{"type":"Point","coordinates":[%s,%s]},"metadata":{}},\
        "observationDateTime":{"type":"DateTime","value":"%s","metadata":{}},\
        "vehicleType":{"type":"Text","value":"bus","metadata":{}}}"""
        .formatted(id, routeAnnotation, id.substring(id.lastIndexOf(':') + 1), longitude, latitude, observedAt);
  }

  public static String answer(String... vehicles) {
    return Arrays.stream(vehicles).collect(Collectors.joining(",", "[", "]"));
  }

  /** A WireMock response definition answering {@code body} as JSON. */
  public static String ok(String body) {
    return "{\"status\":200,\"headers\":{\"Content-Type\":\"application/json\"},\"body\":"
        + JSON.writeValueAsString(body) + "}";
  }
}
