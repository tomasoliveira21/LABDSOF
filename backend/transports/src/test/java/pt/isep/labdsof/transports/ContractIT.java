package pt.isep.labdsof.transports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.yaml.snakeyaml.Yaml;
import pt.isep.labdsof.transports.adapters.in.web.generated.model.BusObservation;
import pt.isep.labdsof.transports.adapters.in.web.generated.model.BusObservations;
import pt.isep.labdsof.transports.adapters.in.web.generated.model.Departure;
import pt.isep.labdsof.transports.adapters.in.web.generated.model.GeoPosition;
import pt.isep.labdsof.transports.adapters.in.web.generated.model.NewDeparture;
import pt.isep.labdsof.transports.adapters.in.web.generated.model.NewTransportOption;
import pt.isep.labdsof.transports.adapters.in.web.generated.model.Problem;
import pt.isep.labdsof.transports.adapters.in.web.generated.model.ServiceStatus;
import pt.isep.labdsof.transports.adapters.in.web.generated.model.TransportMode;
import pt.isep.labdsof.transports.adapters.in.web.generated.model.TransportOption;
import pt.isep.labdsof.transports.adapters.in.web.generated.model.TransportSourceStatus;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * The contract of spec D6. The generated records and enums are measured by JaCoCo (spec D1: no exclusion), so this
 * test writes and reads every one of them through the application's own mapper.
 */
class ContractIT extends IntegrationTest {

  private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
  private static final UUID OTHER = UUID.fromString("66666666-7777-8888-9999-aaaaaaaaaaaa");
  private static final OffsetDateTime AT = OffsetDateTime.of(2027, 5, 8, 23, 14, 5, 0, ZoneOffset.UTC);

  private static final Path CONTRACT = Path.of("contracts", "openapi.yaml");
  private static final Path GENERATED_MODEL = Path.of("target", "generated-sources", "openapi", "src", "main",
      "java", "pt", "isep", "labdsof", "transports", "adapters", "in", "web", "generated", "model");

  @Autowired JsonMapper json;
  @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings;

  private record EnumCase(Enum<?> constant, String value, Function<String, Object> fromValue) {}

  private record EnumType(Class<?> type, Function<String, Object> fromValue) {}

  private record ModelCase(Object instance, Set<String> properties) {}

  private static Arguments enumCase(Enum<?> constant, String value, Function<String, Object> fromValue) {
    return Arguments.of(new EnumCase(constant, value, fromValue));
  }

  private static Stream<Arguments> enumCases() {
    return Stream.of(
        enumCase(TransportMode.SHUTTLE, "Shuttle", TransportMode::fromValue),
        enumCase(TransportMode.EXTERNAL_BUS, "ExternalBus", TransportMode::fromValue),
        enumCase(ServiceStatus.ServiceEnum.TRANSPORTS, "transports", ServiceStatus.ServiceEnum::fromValue),
        enumCase(ServiceStatus.StatusEnum.OK, "ok", ServiceStatus.StatusEnum::fromValue));
  }

  private static Stream<Arguments> enumTypes() {
    return Stream.of(
        Arguments.of(new EnumType(TransportMode.class, TransportMode::fromValue)),
        Arguments.of(new EnumType(ServiceStatus.ServiceEnum.class, ServiceStatus.ServiceEnum::fromValue)),
        Arguments.of(new EnumType(ServiceStatus.StatusEnum.class, ServiceStatus.StatusEnum::fromValue)));
  }

  private static Arguments model(Object instance, String... properties) {
    return Arguments.of(new ModelCase(instance, Set.of(properties)));
  }

  private static final GeoPosition POSITION = new GeoPosition(-8.6841, 41.1724);
  private static final BusObservation BUS = new BusObservation("stcp:3301", "205", POSITION, AT);
  private static final Departure DEPARTURE = new Departure("Trindade", AT, AT.plusMinutes(5));

  private static Stream<Arguments> models() {
    return Stream.of(
        model(new ServiceStatus(ServiceStatus.ServiceEnum.TRANSPORTS, ServiceStatus.StatusEnum.OK),
            "service", "status"),
        model(POSITION, "longitude", "latitude"),
        model(BUS, "vehicleId", "routeId", "position", "observedAt"),
        model(new BusObservations(AT, false, List.of(BUS)), "dataAsOf", "stale", "buses"),
        model(new TransportSourceStatus(true, AT), "available", "lastValidImportAt"),
        model(new NewDeparture("Trindade", AT), "destination", "scheduledAt"),
        model(DEPARTURE, "destination", "scheduledAt", "expectedAt"),
        model(new NewTransportOption(TransportMode.SHUTTLE, "Shuttle A", "205", List.of(ID),
            List.of(new NewDeparture("Trindade", AT))),
            "mode", "name", "externalRoute", "stopPoiIds", "departures"),
        model(new TransportOption(OTHER, TransportMode.EXTERNAL_BUS, "Line 205", "205", List.of(ID),
            List.of(DEPARTURE)),
            "optionId", "mode", "name", "externalRoute", "stopPoiIds", "departures"),
        model(new Problem("about:blank", "Not found", 404, "No such thing", "/api/v1/x"),
            "type", "title", "status", "detail", "instance"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("enumCases")
  @DisplayName("193 D1 every generated enum writes and reads each value of the contract")
  void everyEnumWritesAndReadsEachValue(EnumCase c) {
    var quoted = "\"" + c.value() + "\"";

    assertThat(json.writeValueAsString(c.constant())).isEqualTo(quoted);
    assertThat(json.readValue(quoted, c.constant().getDeclaringClass())).isEqualTo(c.constant());
    assertThat(c.constant().toString()).isEqualTo(c.value());
    assertThat(c.fromValue().apply(c.value())).isEqualTo(c.constant());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("enumTypes")
  @DisplayName("193 D1 an unknown enum value is refused")
  void unknownEnumValueIsRefused(EnumType t) {
    assertThatThrownBy(() -> t.fromValue().apply("Nope")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> t.fromValue().apply(null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> json.readValue("\"Nope\"", t.type())).isInstanceOf(JacksonException.class);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("models")
  @DisplayName("193 D1 every generated model survives a JSON round trip with the contract's field names")
  void everyModelRoundTrips(ModelCase c) {
    var written = json.writeValueAsString(c.instance());

    assertThat(json.readValue(written, c.instance().getClass())).isEqualTo(c.instance());
    assertThat(json.readValue(written, Map.class).keySet()).isEqualTo(c.properties());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("models")
  @DisplayName("193 D1 an unknown field is refused by every generated model")
  void unknownFieldIsRefused(ModelCase c) {
    var type = c.instance().getClass();
    var written = json.writeValueAsString(c.instance());
    var open = written.substring(0, written.length() - 1);

    assertThat(json.readValue(open + "}", type)).isEqualTo(c.instance());
    assertThatThrownBy(() -> json.readValue(open + ",\"isAdmin\":true}", type)).isInstanceOf(JacksonException.class);
  }

  @Test
  @DisplayName("193 D1 a missing list is read as an empty list")
  void missingListIsEmpty() {
    assertThat(new BusObservations(null, true, null).buses()).isEmpty();
    assertThat(new NewTransportOption(TransportMode.EXTERNAL_BUS, null, "205", null, null).stopPoiIds()).isEmpty();
    assertThat(new NewTransportOption(TransportMode.EXTERNAL_BUS, null, "205", null, null).departures()).isEmpty();
    assertThat(new TransportOption(ID, TransportMode.SHUTTLE, "A", null, null, null).stopPoiIds()).isEmpty();
    assertThat(new TransportOption(ID, TransportMode.SHUTTLE, "A", null, null, null).departures()).isEmpty();
  }

  @Test
  @DisplayName("193 D1 ContractIT covers every generated model and enum")
  void everyGeneratedClassIsCovered() throws IOException {
    var covered = new TreeSet<String>();
    enumCases().map(a -> ((EnumCase) a.get()[0]).constant().getDeclaringClass()).forEach(type ->
        covered.add(type.getEnclosingClass() == null ? type.getSimpleName() : type.getEnclosingClass().getSimpleName()));
    models().map(a -> ((ModelCase) a.get()[0]).instance().getClass().getSimpleName()).forEach(covered::add);

    try (var files = Files.list(GENERATED_MODEL)) {
      var generated = new TreeSet<String>();
      files.map(file -> file.getFileName().toString()).filter(name -> name.endsWith(".java"))
          .forEach(name -> generated.add(name.substring(0, name.length() - ".java".length())));

      assertThat(generated).isNotEmpty().isEqualTo(covered);
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> contract() {
    try (var in = Files.newBufferedReader(CONTRACT, StandardCharsets.UTF_8)) {
      return (Map<String, Object>) new Yaml().load(in);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** "METHOD path" of every operation of contracts/openapi.yaml (path items also hold a {@code parameters} list). */
  @SuppressWarnings("unchecked")
  static Set<String> contractOperations() {
    var operations = new TreeSet<String>();
    ((Map<String, Object>) contract().get("paths")).forEach((path, item) ->
        ((Map<String, Object>) item).forEach((key, value) -> {
          if (value instanceof Map) {
            operations.add(key.toUpperCase() + " " + path);
          }
        }));
    return operations;
  }

  @SuppressWarnings("unchecked")
  private static List<String> contractEnum(String schema) {
    var schemas = (Map<String, Map<String, Object>>) ((Map<String, Object>) contract().get("components"))
        .get("schemas");
    return (List<String>) schemas.get(schema).get("enum");
  }

  @Test
  @DisplayName("193 AC-4 openapi.yaml holds the Sprint B endpoints of API outline 5")
  void contractHoldsTheSprintBEndpoints() {
    assertThat(contractOperations()).containsExactlyInAnyOrder(
        "GET /api/v1/status",
        "GET /api/v1/events/{eventId}/bus-observations",
        "GET /api/v1/events/{eventId}/transport-source-status",
        "POST /api/v1/events/{eventId}/transport-options");
  }

  /** A valid request for one operation; {@code story} is the 501 story, null for the status. */
  record SampleRequest(String method, String path, String body, String contentType, String story) {}

  private static final String JSON_TYPE = "application/json";

  private static final Map<String, String> STORIES = Map.of(
      "GET /api/v1/events/{eventId}/bus-observations", "US-41",
      "GET /api/v1/events/{eventId}/transport-source-status", "US-43",
      "POST /api/v1/events/{eventId}/transport-options", "US-41");

  private static final Map<String, String> BODIES = Map.of(
      "POST /api/v1/events/{eventId}/transport-options",
      "{\"mode\":\"ExternalBus\",\"externalRoute\":\"205\",\"stopPoiIds\":[\"" + ID + "\"]}");

  /** The sample request of an operation ("METHOD path" of the contract), with a fresh event id. */
  static SampleRequest sampleRequest(String operation) {
    var method = operation.substring(0, operation.indexOf(' '));
    var path = operation.substring(operation.indexOf(' ') + 1).replace("{eventId}", UUID.randomUUID().toString());
    var body = BODIES.get(operation);
    return new SampleRequest(method, path, body, body == null ? null : JSON_TYPE, STORIES.get(operation));
  }

  private static Stream<String> laterStoryOperations() {
    return contractOperations().stream().filter(STORIES::containsKey);
  }

  @Test
  @DisplayName("193 AC-4 the service serves exactly the operations of openapi.yaml")
  void servesExactlyTheContractOperations() {
    var served = new TreeSet<String>();
    mappings.getHandlerMethods().forEach((info, handler) -> {
      if (handler.getBeanType().getPackageName().startsWith("pt.isep.labdsof.transports")) {
        info.getMethodsCondition().getMethods().forEach(method ->
            info.getPathPatternsCondition().getPatternValues().forEach(path -> served.add(method + " " + path)));
      }
    });

    assertThat(served).isEqualTo(contractOperations());
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("laterStoryOperations")
  @DisplayName("193 AC-4 every operation of a later story answers 501 Problem Details naming its story")
  void laterStoriesAnswer501(String operation) {
    var sample = sampleRequest(operation);
    var headers = sample.contentType() == null ? new String[0] : new String[] {"Content-Type", sample.contentType()};

    var response = new ApiClient(port).send(sample.method(), sample.path(),
        "Bearer " + JWKS.valid(UUID.randomUUID().toString()), sample.body(), headers);

    ApiClient.assertProblem(response, 501);
    assertThat(ApiClient.json(response).get("detail")).isEqualTo("This operation arrives with " + sample.story() + ".");
  }

  @Test
  @DisplayName("193 AC-4 three operations of the contract belong to later stories")
  void laterStoriesAreTheContractOperationsButStatus() {
    assertThat(laterStoryOperations().toList()).hasSize(3).doesNotContain("GET /api/v1/status");
  }

  @ParameterizedTest(name = "[{index}] {0} {1}")
  @CsvSource({"GET,/api/v1/nothing-here,404", "GET,/api/v1/events/{id}/transport-options,405",
      "PATCH,/api/v1/events/{id}/transport-options/{id},404", "POST,/api/v1/events/{id}/bus-observations,405",
      "DELETE,/api/v1/status,405"})
  @DisplayName("193 AC-4 a path or method outside the contract answers 404 or 405 Problem Details")
  void pathsOutsideTheContractAreRefused(String method, String path, int status) {
    var response = new ApiClient(port).send(method, path.replace("{id}", UUID.randomUUID().toString()),
        "Bearer " + JWKS.valid(UUID.randomUUID().toString()));

    ApiClient.assertProblem(response, status);
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @CsvSource({"GET,/bus-observations", "GET,/transport-source-status", "POST,/transport-options"})
  @DisplayName("193 AC-2 a malformed event id is refused with 400 before the 501")
  void malformedEventIdIsRefused(String method, String suffix) {
    var body = method.equals("POST") ? BODIES.get("POST /api/v1/events/{eventId}/transport-options") : null;
    var headers = body == null ? new String[0] : new String[] {"Content-Type", JSON_TYPE};

    var response = new ApiClient(port).send(method, "/api/v1/events/not-a-uuid" + suffix,
        "Bearer " + JWKS.valid(UUID.randomUUID().toString()), body, headers);

    ApiClient.assertProblem(response, 400);
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @CsvSource(delimiter = '|', textBlock = """
      unknown field (mass assignment) | {"mode":"Shuttle","name":"A","optionId":"11111111-2222-3333-4444-555555555555"}
      unknown mode                    | {"mode":"Train","name":"A"}
      no mode                         | {"name":"A"}
      name over 100 characters        | {"mode":"Shuttle","name":"%s"}
      route over 20 characters        | {"mode":"ExternalBus","externalRoute":"123456789012345678901"}
      a stop id that is not a UUID    | {"mode":"ExternalBus","externalRoute":"205","stopPoiIds":["x"]}
      a departure without destination | {"mode":"Shuttle","name":"A","departures":[{"destination":"","scheduledAt":"2027-05-08T23:14:05Z"}]}
      a departure without its time    | {"mode":"Shuttle","name":"A","departures":[{"destination":"Trindade"}]}
      not JSON                        | not json
      """)
  @DisplayName("193 AC-4 an invalid transport option body is refused with 400 before the 501")
  void invalidTransportOptionIsRefused(String name, String body) {
    var response = new ApiClient(port).send("POST", "/api/v1/events/" + UUID.randomUUID() + "/transport-options",
        "Bearer " + JWKS.valid(UUID.randomUUID().toString()), body.formatted("n".repeat(101)),
        "Content-Type", JSON_TYPE);

    ApiClient.assertProblem(response, 400);
  }

  @Test
  @DisplayName("193 AC-4 the contract is served at /openapi.yaml without a token")
  void contractIsServedWithoutAToken() throws IOException {
    var response = new ApiClient(port).get("/openapi.yaml", null);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).isEqualTo(Files.readString(CONTRACT, StandardCharsets.UTF_8));
  }

  @Test
  @DisplayName("193 D6 the contract's transport modes are the agreed list")
  void contractCodesAreTheAgreedLists() {
    assertThat(contractEnum("TransportMode")).containsExactly("Shuttle", "ExternalBus");
  }
}
