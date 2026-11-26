package pt.isep.labdsof.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
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
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.yaml.snakeyaml.Yaml;
import pt.isep.labdsof.events.adapters.in.web.generated.model.DateRange;
import pt.isep.labdsof.events.adapters.in.web.generated.model.Event;
import pt.isep.labdsof.events.adapters.in.web.generated.model.EventStatus;
import pt.isep.labdsof.events.adapters.in.web.generated.model.HelpCategory;
import pt.isep.labdsof.events.adapters.in.web.generated.model.MapPosition;
import pt.isep.labdsof.events.adapters.in.web.generated.model.MapVersion;
import pt.isep.labdsof.events.adapters.in.web.generated.model.NewEvent;
import pt.isep.labdsof.events.adapters.in.web.generated.model.NewPointOfInterest;
import pt.isep.labdsof.events.adapters.in.web.generated.model.NewTeam;
import pt.isep.labdsof.events.adapters.in.web.generated.model.Permission;
import pt.isep.labdsof.events.adapters.in.web.generated.model.PoiType;
import pt.isep.labdsof.events.adapters.in.web.generated.model.PointOfInterest;
import pt.isep.labdsof.events.adapters.in.web.generated.model.PointOfInterestChange;
import pt.isep.labdsof.events.adapters.in.web.generated.model.Problem;
import pt.isep.labdsof.events.adapters.in.web.generated.model.ReportType;
import pt.isep.labdsof.events.adapters.in.web.generated.model.ServiceStatus;
import pt.isep.labdsof.events.adapters.in.web.generated.model.Team;
import pt.isep.labdsof.events.adapters.in.web.generated.model.TeamChange;
import pt.isep.labdsof.events.adapters.in.web.generated.model.TeamPermission;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * The generated records and enums are measured by JaCoCo (spec D1: no exclusion), so this test writes and reads
 * every one of them through the application's own mapper.
 */
class ContractIT extends IntegrationTest {

  private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
  private static final UUID OTHER = UUID.fromString("66666666-7777-8888-9999-aaaaaaaaaaaa");
  private static final OffsetDateTime UPLOADED = OffsetDateTime.of(2027, 5, 2, 10, 15, 30, 0, ZoneOffset.UTC);

  private static final Path CONTRACT = Path.of("contracts", "openapi.yaml");
  private static final Path GENERATED_MODEL = Path.of("target", "generated-sources", "openapi", "src", "main",
      "java", "pt", "isep", "labdsof", "events", "adapters", "in", "web", "generated", "model");

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
        enumCase(EventStatus.DRAFT, "Draft", EventStatus::fromValue),
        enumCase(EventStatus.PUBLISHED, "Published", EventStatus::fromValue),
        enumCase(EventStatus.SUSPENDED, "Suspended", EventStatus::fromValue),
        enumCase(EventStatus.CLOSED, "Closed", EventStatus::fromValue),
        enumCase(HelpCategory.HEALTH, "Health", HelpCategory::fromValue),
        enumCase(HelpCategory.SAFETY, "Safety", HelpCategory::fromValue),
        enumCase(HelpCategory.LOST_PERSON, "LostPerson", HelpCategory::fromValue),
        enumCase(HelpCategory.INFRASTRUCTURE, "Infrastructure", HelpCategory::fromValue),
        enumCase(HelpCategory.OTHER, "Other", HelpCategory::fromValue),
        enumCase(Permission.HELP_REQUESTS_TRIAGE, "HelpRequests.Triage", Permission::fromValue),
        enumCase(Permission.HELP_REQUESTS_RESPOND, "HelpRequests.Respond", Permission::fromValue),
        enumCase(Permission.REPORTS_REVIEW, "Reports.Review", Permission::fromValue),
        enumCase(Permission.REPORTS_RESPOND, "Reports.Respond", Permission::fromValue),
        enumCase(Permission.ANNOUNCEMENTS_PUBLISH, "Announcements.Publish", Permission::fromValue),
        enumCase(Permission.MAP_EDIT, "Map.Edit", Permission::fromValue),
        enumCase(Permission.TRANSPORT_EDIT, "Transport.Edit", Permission::fromValue),
        enumCase(Permission.ENTRANCES_UPDATE, "Entrances.Update", Permission::fromValue),
        enumCase(ReportType.QUEUE_AT_ENTRANCE, "QueueAtEntrance", ReportType::fromValue),
        enumCase(ReportType.CROWDED_AREA, "CrowdedArea", ReportType::fromValue),
        enumCase(ReportType.WC_PROBLEM, "WcProblem", ReportType::fromValue),
        enumCase(ReportType.NO_WATER, "NoWater", ReportType::fromValue),
        enumCase(ReportType.HAZARD, "Hazard", ReportType::fromValue),
        enumCase(ReportType.TRANSPORT_PROBLEM, "TransportProblem", ReportType::fromValue),
        enumCase(ReportType.WRONG_INFORMATION, "WrongInformation", ReportType::fromValue),
        enumCase(PoiType.ENTRANCE, "Entrance", PoiType::fromValue),
        enumCase(PoiType.STAGE, "Stage", PoiType::fromValue),
        enumCase(PoiType.WC, "Wc", PoiType::fromValue),
        enumCase(PoiType.FIRST_AID, "FirstAid", PoiType::fromValue),
        enumCase(PoiType.INFORMATION, "Information", PoiType::fromValue),
        enumCase(PoiType.TRANSPORT_STOP, "TransportStop", PoiType::fromValue),
        enumCase(PoiType.OTHER, "Other", PoiType::fromValue),
        enumCase(ServiceStatus.ServiceEnum.EVENTS, "events", ServiceStatus.ServiceEnum::fromValue),
        enumCase(ServiceStatus.StatusEnum.OK, "ok", ServiceStatus.StatusEnum::fromValue));
  }

  private static Stream<Arguments> enumTypes() {
    return Stream.of(
        Arguments.of(new EnumType(EventStatus.class, EventStatus::fromValue)),
        Arguments.of(new EnumType(HelpCategory.class, HelpCategory::fromValue)),
        Arguments.of(new EnumType(Permission.class, Permission::fromValue)),
        Arguments.of(new EnumType(ReportType.class, ReportType::fromValue)),
        Arguments.of(new EnumType(PoiType.class, PoiType::fromValue)),
        Arguments.of(new EnumType(ServiceStatus.ServiceEnum.class, ServiceStatus.ServiceEnum::fromValue)),
        Arguments.of(new EnumType(ServiceStatus.StatusEnum.class, ServiceStatus.StatusEnum::fromValue)));
  }

  private static Arguments model(Object instance, String... properties) {
    return Arguments.of(new ModelCase(instance, Set.of(properties)));
  }

  private static final DateRange DATES = new DateRange(LocalDate.of(2027, 5, 2), LocalDate.of(2027, 5, 9));
  private static final MapPosition POSITION = new MapPosition(0.25, 0.75);
  private static final PointOfInterest POINT = new PointOfInterest(ID, PoiType.STAGE, "Main stage", POSITION);
  private static final TeamPermission REVIEW = new TeamPermission(Permission.REPORTS_REVIEW,
      List.of(ReportType.HAZARD, ReportType.NO_WATER));

  private static Stream<Arguments> models() {
    return Stream.of(
        model(new ServiceStatus(ServiceStatus.ServiceEnum.EVENTS, ServiceStatus.StatusEnum.OK), "service", "status"),
        model(DATES, "start", "end"),
        model(new NewEvent("Queima das Fitas", "FAP", DATES, "Semana academica"),
            "name", "organiserName", "dates", "description"),
        model(new Event(ID, "Queima das Fitas", "FAP", DATES, "Semana academica", EventStatus.PUBLISHED,
            "https://farol.test/join/ABC"),
            "eventId", "name", "organiserName", "dates", "description", "status", "joinLink"),
        model(REVIEW, "permission", "reportTypes"),
        model(new Team(OTHER, "Nursing", List.of(REVIEW), List.of(HelpCategory.HEALTH, HelpCategory.SAFETY)),
            "teamId", "name", "permissions", "handledCategories"),
        model(new NewTeam("Nursing"), "name"),
        model(new TeamChange("Nursing", List.of(HelpCategory.LOST_PERSON)), "name", "handledCategories"),
        model(POSITION, "x", "y"),
        model(POINT, "poiId", "type", "name", "position"),
        model(new NewPointOfInterest(PoiType.WC, "North WC", POSITION), "type", "name", "position"),
        model(new PointOfInterestChange("Renamed", POSITION), "name", "position"),
        model(new MapVersion(2, UPLOADED, List.of(POINT)), "version", "uploadedAt", "pointsOfInterest"),
        model(new Problem("about:blank", "Not found", 404, "No such thing", "/api/v1/x"),
            "type", "title", "status", "detail", "instance"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("enumCases")
  @DisplayName("192 D1 every generated enum writes and reads each value of the contract")
  void everyEnumWritesAndReadsEachValue(EnumCase c) {
    var quoted = "\"" + c.value() + "\"";

    assertThat(json.writeValueAsString(c.constant())).isEqualTo(quoted);
    assertThat(json.readValue(quoted, c.constant().getDeclaringClass())).isEqualTo(c.constant());
    assertThat(c.constant().toString()).isEqualTo(c.value());
    assertThat(c.fromValue().apply(c.value())).isEqualTo(c.constant());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("enumTypes")
  @DisplayName("192 D1 an unknown enum value is refused")
  void unknownEnumValueIsRefused(EnumType t) {
    assertThatThrownBy(() -> t.fromValue().apply("Nope")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> t.fromValue().apply(null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> json.readValue("\"Nope\"", t.type())).isInstanceOf(JacksonException.class);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("models")
  @DisplayName("192 D1 every generated model survives a JSON round trip with the contract's field names")
  void everyModelRoundTrips(ModelCase c) {
    var written = json.writeValueAsString(c.instance());

    assertThat(json.readValue(written, c.instance().getClass())).isEqualTo(c.instance());
    assertThat(json.readValue(written, Map.class).keySet()).isEqualTo(c.properties());
  }

  @Test
  @DisplayName("192 D1 a missing list is read as an empty list")
  void missingListIsEmpty() {
    assertThat(new TeamPermission(Permission.MAP_EDIT, null).reportTypes()).isEmpty();
    var team = new Team(ID, "T", null, null);
    assertThat(team.permissions()).isEmpty();
    assertThat(team.handledCategories()).isEmpty();
    assertThat(new TeamChange("T", null).handledCategories()).isEmpty();
    assertThat(new MapVersion(1, UPLOADED, null).pointsOfInterest()).isEmpty();

    assertThat(json.readValue("{\"permission\":\"Map.Edit\"}", TeamPermission.class).reportTypes()).isEmpty();
    var readTeam = json.readValue("{\"teamId\":\"%s\",\"name\":\"T\"}".formatted(ID), Team.class);
    assertThat(readTeam.permissions()).isEmpty();
    assertThat(readTeam.handledCategories()).isEmpty();
    assertThat(json.readValue("{\"name\":\"T\"}", TeamChange.class).handledCategories()).isEmpty();
    assertThat(json.readValue("{\"version\":1,\"uploadedAt\":\"2027-05-02T10:15:30Z\"}", MapVersion.class)
        .pointsOfInterest()).isEmpty();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("models")
  @DisplayName("192 D1 an unknown field is refused by every generated model")
  void unknownFieldIsRefused(ModelCase c) {
    var type = c.instance().getClass();
    var written = json.writeValueAsString(c.instance());
    var open = written.substring(0, written.length() - 1);

    assertThat(json.readValue(open + "}", type)).isEqualTo(c.instance());
    assertThatThrownBy(() -> json.readValue(open + ",\"isAdmin\":true}", type)).isInstanceOf(JacksonException.class);
  }

  @Test
  @DisplayName("192 D1 ContractIT covers every generated model and enum")
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

  @Test
  @DisplayName("192 D6 an event's dates are written as ISO-8601 dates")
  void datesAreIsoDates() {
    assertThat(json.writeValueAsString(DATES)).isEqualTo("{\"start\":\"2027-05-02\",\"end\":\"2027-05-09\"}");
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
  @DisplayName("192 AC-4 openapi.yaml holds the Sprint B endpoints of API outline 3")
  void contractHoldsTheSprintBEndpoints() {
    assertThat(contractOperations()).containsExactlyInAnyOrder(
        "GET /api/v1/status",
        "POST /api/v1/events",
        "GET /api/v1/events/{eventId}",
        "POST /api/v1/events/{eventId}/publish",
        "GET /api/v1/events/{eventId}/teams",
        "POST /api/v1/events/{eventId}/teams",
        "PATCH /api/v1/events/{eventId}/teams/{teamId}",
        "DELETE /api/v1/events/{eventId}/teams/{teamId}",
        "PUT /api/v1/events/{eventId}/teams/{teamId}/permissions",
        "POST /api/v1/events/{eventId}/map-versions",
        "GET /api/v1/events/{eventId}/map-versions/current",
        "GET /api/v1/events/{eventId}/map-versions/{version}",
        "GET /api/v1/events/{eventId}/map-versions/{version}/image",
        "POST /api/v1/events/{eventId}/map-versions/{version}/points-of-interest",
        "PATCH /api/v1/events/{eventId}/map-versions/{version}/points-of-interest/{poiId}",
        "DELETE /api/v1/events/{eventId}/map-versions/{version}/points-of-interest/{poiId}");
  }

  /** A valid request for one operation; {@code story} is the 501 story, null for status and getEvent. */
  record SampleRequest(String method, String path, String body, String contentType, String story) {}

  private static final String JSON_TYPE = "application/json";
  private static final String MULTIPART_TYPE = "multipart/form-data; boundary=farol-boundary";
  private static final String MULTIPART_BODY = "--farol-boundary\r\n"
      + "Content-Disposition: form-data; name=\"file\"; filename=\"map.png\"\r\n"
      + "Content-Type: image/png\r\n\r\nnot really a png\r\n--farol-boundary--\r\n";

  private static final Map<String, String> STORIES = Map.ofEntries(
      Map.entry("POST /api/v1/events", "US-06"),
      Map.entry("POST /api/v1/events/{eventId}/map-versions", "US-19"),
      Map.entry("GET /api/v1/events/{eventId}/map-versions/current", "US-21 and US-22"),
      Map.entry("GET /api/v1/events/{eventId}/map-versions/{version}", "US-27"),
      Map.entry("GET /api/v1/events/{eventId}/map-versions/{version}/image", "US-21"),
      Map.entry("POST /api/v1/events/{eventId}/map-versions/{version}/points-of-interest", "US-20"),
      Map.entry("PATCH /api/v1/events/{eventId}/map-versions/{version}/points-of-interest/{poiId}", "US-20"),
      Map.entry("DELETE /api/v1/events/{eventId}/map-versions/{version}/points-of-interest/{poiId}", "US-20"));

  private static final Map<String, String> BODIES = Map.of(
      "POST /api/v1/events", "{\"name\":\"Queima das Fitas 2027\",\"organiserName\":\"FAP\","
          + "\"dates\":{\"start\":\"2027-05-02\",\"end\":\"2027-05-09\"}}",
      "POST /api/v1/events/{eventId}/teams", "{\"name\":\"Medical\"}",
      "PATCH /api/v1/events/{eventId}/teams/{teamId}", "{\"name\":\"Medical\",\"handledCategories\":[\"Health\"]}",
      "PUT /api/v1/events/{eventId}/teams/{teamId}/permissions",
      "[{\"permission\":\"Reports.Review\",\"reportTypes\":[\"Hazard\"]}]",
      "POST /api/v1/events/{eventId}/map-versions/{version}/points-of-interest",
      "{\"type\":\"Stage\",\"name\":\"Main stage\",\"position\":{\"x\":0.25,\"y\":0.75}}",
      "PATCH /api/v1/events/{eventId}/map-versions/{version}/points-of-interest/{poiId}",
      "{\"name\":\"Main stage\"}");

  /** The sample request of an operation ("METHOD path" of the contract), with fresh UUIDs and version 1. */
  static SampleRequest sampleRequest(String operation) {
    var method = operation.substring(0, operation.indexOf(' '));
    var path = operation.substring(operation.indexOf(' ') + 1)
        .replace("{eventId}", UUID.randomUUID().toString()).replace("{teamId}", UUID.randomUUID().toString())
        .replace("{poiId}", UUID.randomUUID().toString()).replace("{version}", "1");
    if (operation.equals("POST /api/v1/events/{eventId}/map-versions")) {
      return new SampleRequest(method, path, MULTIPART_BODY, MULTIPART_TYPE, STORIES.get(operation));
    }
    var body = BODIES.get(operation);
    return new SampleRequest(method, path, body, body == null ? null : JSON_TYPE, STORIES.get(operation));
  }

  private static Stream<String> laterStoryOperations() {
    return contractOperations().stream().filter(STORIES::containsKey);
  }

  @Test
  @DisplayName("192 AC-4 the service serves exactly the operations of openapi.yaml")
  void servesExactlyTheContractOperations() {
    var served = new TreeSet<String>();
    mappings.getHandlerMethods().forEach((info, handler) -> {
      if (handler.getBeanType().getPackageName().startsWith("pt.isep.labdsof.events")) {
        info.getMethodsCondition().getMethods().forEach(method ->
            info.getPathPatternsCondition().getPatternValues().forEach(path -> served.add(method + " " + path)));
      }
    });

    assertThat(served).isEqualTo(contractOperations());
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("laterStoryOperations")
  @DisplayName("192 AC-4 every operation of a later story answers 501 Problem Details naming its story")
  void laterStoriesAnswer501(String operation) {
    var sample = sampleRequest(operation);
    var headers = sample.contentType() == null ? new String[0] : new String[] {"Content-Type", sample.contentType()};

    var response = new ApiClient(port).send(sample.method(), sample.path(),
        "Bearer " + JWKS.valid(UUID.randomUUID().toString()), sample.body(), headers);

    ApiClient.assertProblem(response, 501);
    assertThat(ApiClient.json(response).get("detail")).isEqualTo("This operation arrives with " + sample.story() + ".");
  }

  @Test
  @DisplayName("192 AC-4 eight operations of the contract belong to later stories, none of the Events or Teams "
      + "tags among them")
  void laterStoriesAreTheContractOperationsButWhatIsImplemented() {
    assertThat(laterStoryOperations().toList()).hasSize(8).doesNotContain("GET /api/v1/status",
        "POST /api/v1/events/{eventId}/publish",
        "GET /api/v1/events/{eventId}", "GET /api/v1/events/{eventId}/teams",
        "POST /api/v1/events/{eventId}/teams", "PATCH /api/v1/events/{eventId}/teams/{teamId}",
        "DELETE /api/v1/events/{eventId}/teams/{teamId}",
        "PUT /api/v1/events/{eventId}/teams/{teamId}/permissions");
    assertThat(laterStoryOperations().toList()).noneMatch(operation -> operation.contains("/teams"));
  }

  @ParameterizedTest(name = "[{index}] {0} {1}")
  @CsvSource({"GET,/api/v1/nothing-here,404", "GET,/api/v1/events,405", "PUT,/api/v1/events/{id},405",
      "DELETE,/api/v1/status,405"})
  @DisplayName("192 AC-4 a path or method outside the contract answers 404 or 405 Problem Details")
  void pathsOutsideTheContractAreRefused(String method, String path, int status) {
    var response = new ApiClient(port).send(method, path.replace("{id}", UUID.randomUUID().toString()),
        "Bearer " + JWKS.valid(UUID.randomUUID().toString()));

    ApiClient.assertProblem(response, status);
  }

  @Test
  @DisplayName("192 AC-4 the contract is served at /openapi.yaml without a token")
  void contractIsServedWithoutAToken() throws IOException {
    var response = new ApiClient(port).get("/openapi.yaml", null);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).isEqualTo(Files.readString(CONTRACT, StandardCharsets.UTF_8));
  }

  @Test
  @DisplayName("192 AC-5 an event without a join link is written without the joinLink key")
  void eventWithoutJoinLinkOmitsTheKey() {
    var without = new Event(ID, "Queima das Fitas", "FAP", DATES, "Semana academica", EventStatus.DRAFT, null);
    var with = new Event(ID, "Queima das Fitas", "FAP", DATES, "Semana academica", EventStatus.PUBLISHED,
        "/join/x?code=y");

    assertThat(json.readValue(json.writeValueAsString(without), Map.class)).doesNotContainKey("joinLink");
    assertThat(json.readValue(json.writeValueAsString(with), Map.class)).containsEntry("joinLink", "/join/x?code=y");
  }

  @Test
  @DisplayName("192 D6 openapi.yaml has no members path, which belongs to Auth & Users")
  @SuppressWarnings("unchecked")
  void contractHasNoMembersPath() {
    var paths = new HashSet<>(((Map<String, Object>) contract().get("paths")).keySet());

    assertThat(paths).isNotEmpty().noneMatch(path -> path.contains("/members"))
        .noneMatch(path -> path.startsWith("/api/v1/me")).noneMatch(path -> path.startsWith("/api/v1/accounts"));
  }

  @Test
  @DisplayName("192 D6 the contract's event status, permission, report type, point type and help category codes "
      + "are the agreed lists")
  void contractCodesAreTheAgreedLists() {
    assertThat(contractEnum("EventStatus")).containsExactly("Draft", "Published", "Suspended", "Closed");
    assertThat(contractEnum("Permission")).containsExactly("HelpRequests.Triage", "HelpRequests.Respond",
        "Reports.Review", "Reports.Respond", "Announcements.Publish", "Map.Edit", "Transport.Edit",
        "Entrances.Update");
    assertThat(contractEnum("ReportType")).containsExactly("QueueAtEntrance", "CrowdedArea", "WcProblem",
        "NoWater", "Hazard", "TransportProblem", "WrongInformation");
    assertThat(contractEnum("PoiType")).containsExactly("Entrance", "Stage", "Wc", "FirstAid", "Information",
        "TransportStop", "Other");
    assertThat(contractEnum("HelpCategory")).containsExactly("Health", "Safety", "LostPerson", "Infrastructure",
        "Other");
  }
}
