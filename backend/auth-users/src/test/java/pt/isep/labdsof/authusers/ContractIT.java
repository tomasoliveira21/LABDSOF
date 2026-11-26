package pt.isep.labdsof.authusers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.yaml.snakeyaml.Yaml;
import pt.isep.labdsof.authusers.adapters.in.web.generated.model.AccountSummary;
import pt.isep.labdsof.authusers.adapters.in.web.generated.model.EventRole;
import pt.isep.labdsof.authusers.adapters.in.web.generated.model.EventStatus;
import pt.isep.labdsof.authusers.adapters.in.web.generated.model.GlobalRole;
import pt.isep.labdsof.authusers.adapters.in.web.generated.model.Me;
import pt.isep.labdsof.authusers.adapters.in.web.generated.model.Member;
import pt.isep.labdsof.authusers.adapters.in.web.generated.model.Membership;
import pt.isep.labdsof.authusers.adapters.in.web.generated.model.NameEventAdmin;
import pt.isep.labdsof.authusers.adapters.in.web.generated.model.Problem;
import pt.isep.labdsof.authusers.adapters.in.web.generated.model.ServiceStatus;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * The generated records and enums are measured by JaCoCo (spec D2: no exclusion), so this test writes and reads
 * every one of them through the application's own mapper.
 */
class ContractIT extends IntegrationTest {

  private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
  private static final UUID OTHER = UUID.fromString("66666666-7777-8888-9999-aaaaaaaaaaaa");

  private static final Path CONTRACT = Path.of("contracts", "openapi.yaml");

  @Autowired JsonMapper json;
  @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings;

  private record EnumCase(Enum<?> constant, String value, Function<String, Object> fromValue) {}

  private record EnumType(Class<?> type, Function<String, Object> fromValue) {}

  private record ModelCase(Object instance, Set<String> properties, String valid) {}

  private static Stream<Arguments> enumCases() {
    return Stream.of(
        enumCase(GlobalRole.FarolAdmin, "FarolAdmin", GlobalRole::fromValue),
        enumCase(EventRole.EventAdmin, "EventAdmin", EventRole::fromValue),
        enumCase(EventRole.RegularUser, "RegularUser", EventRole::fromValue),
        enumCase(EventStatus.Draft, "Draft", EventStatus::fromValue),
        enumCase(EventStatus.Published, "Published", EventStatus::fromValue),
        enumCase(EventStatus.Suspended, "Suspended", EventStatus::fromValue),
        enumCase(EventStatus.Closed, "Closed", EventStatus::fromValue),
        enumCase(NameEventAdmin.RoleEnum.EventAdmin, "EventAdmin", NameEventAdmin.RoleEnum::fromValue),
        enumCase(ServiceStatus.ServiceEnum.authUsers, "auth-users", ServiceStatus.ServiceEnum::fromValue),
        enumCase(ServiceStatus.StatusEnum.ok, "ok", ServiceStatus.StatusEnum::fromValue));
  }

  private static Arguments enumCase(Enum<?> constant, String value, Function<String, Object> fromValue) {
    return Arguments.of(new EnumCase(constant, value, fromValue));
  }

  private static Stream<Arguments> enumTypes() {
    return Stream.of(
        Arguments.of(new EnumType(GlobalRole.class, GlobalRole::fromValue)),
        Arguments.of(new EnumType(EventRole.class, EventRole::fromValue)),
        Arguments.of(new EnumType(EventStatus.class, EventStatus::fromValue)),
        Arguments.of(new EnumType(NameEventAdmin.RoleEnum.class, NameEventAdmin.RoleEnum::fromValue)),
        Arguments.of(new EnumType(ServiceStatus.ServiceEnum.class, ServiceStatus.ServiceEnum::fromValue)),
        Arguments.of(new EnumType(ServiceStatus.StatusEnum.class, ServiceStatus.StatusEnum::fromValue)));
  }

  /** Each case carries its JSON without the closing brace, so the unknown field can be appended. */
  private static Stream<Arguments> models() {
    return Stream.of(
        model(new Me(ID, "ana@farol.test", "Ana", List.of(GlobalRole.FarolAdmin)),
            Set.of("userId", "email", "displayName", "globalRoles"),
            "{\"userId\":\"" + ID + "\",\"email\":\"ana@farol.test\",\"displayName\":\"Ana\","
                + "\"globalRoles\":[\"FarolAdmin\"]"),
        model(new Membership(ID, "Festival", EventStatus.Published, EventRole.EventAdmin, List.of(OTHER)),
            Set.of("eventId", "eventName", "eventStatus", "role", "teamIds"),
            "{\"eventId\":\"" + ID + "\",\"eventName\":\"Festival\",\"eventStatus\":\"Published\","
                + "\"role\":\"EventAdmin\",\"teamIds\":[\"" + OTHER + "\"]"),
        model(new Member(ID, "Ana", EventRole.RegularUser, List.of(OTHER)),
            Set.of("userId", "displayName", "role", "teamIds"),
            "{\"userId\":\"" + ID + "\",\"displayName\":\"Ana\",\"role\":\"RegularUser\","
                + "\"teamIds\":[\"" + OTHER + "\"]"),
        model(new AccountSummary(ID, "Ana"), Set.of("userId", "displayName"),
            "{\"userId\":\"" + ID + "\",\"displayName\":\"Ana\""),
        model(new NameEventAdmin(ID, NameEventAdmin.RoleEnum.EventAdmin), Set.of("userId", "role"),
            "{\"userId\":\"" + ID + "\",\"role\":\"EventAdmin\""),
        model(new Problem("about:blank", "Not found", 404, "No such thing", "/api/v1/x"),
            Set.of("type", "title", "status", "detail", "instance"),
            "{\"type\":\"about:blank\",\"title\":\"Not found\",\"status\":404,"
                + "\"detail\":\"No such thing\",\"instance\":\"/api/v1/x\""),
        model(new ServiceStatus(ServiceStatus.ServiceEnum.authUsers, ServiceStatus.StatusEnum.ok),
            Set.of("service", "status"), "{\"service\":\"auth-users\",\"status\":\"ok\""));
  }

  private static Arguments model(Object instance, Set<String> properties, String openJson) {
    return Arguments.of(new ModelCase(instance, properties, openJson));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("enumCases")
  @DisplayName("191 D2 every generated enum writes and reads each value of the contract")
  void everyEnumWritesAndReadsEachValue(EnumCase c) {
    var quoted = "\"" + c.value() + "\"";

    assertThat(json.writeValueAsString(c.constant())).isEqualTo(quoted);
    assertThat(json.readValue(quoted, c.constant().getDeclaringClass())).isEqualTo(c.constant());
    assertThat(c.constant().toString()).isEqualTo(c.value());
    assertThat(c.fromValue().apply(c.value())).isEqualTo(c.constant());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("enumTypes")
  @DisplayName("191 D2 an unknown enum value is refused")
  void unknownEnumValueIsRefused(EnumType t) {
    assertThatThrownBy(() -> t.fromValue().apply("Nope")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> t.fromValue().apply(null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> json.readValue("\"Nope\"", t.type())).isInstanceOf(JacksonException.class);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("models")
  @DisplayName("191 D2 every generated model survives a JSON round trip with the contract's field names")
  void everyModelRoundTrips(ModelCase c) {
    var written = json.writeValueAsString(c.instance());

    assertThat(json.readValue(written, c.instance().getClass())).isEqualTo(c.instance());
    assertThat(json.readValue(written, Map.class).keySet()).isEqualTo(c.properties());
  }

  @Test
  @DisplayName("191 D2 a missing list is read as an empty list")
  void missingListIsEmpty() {
    assertThat(new Me(ID, "a@b.test", "A", null).globalRoles()).isEmpty();
    assertThat(new Membership(ID, "E", EventStatus.Draft, EventRole.RegularUser, null).teamIds()).isEmpty();
    assertThat(new Member(ID, "A", EventRole.RegularUser, null).teamIds()).isEmpty();

    var me = json.readValue("{\"userId\":\"%s\",\"email\":\"a@b.test\",\"displayName\":\"A\"}".formatted(ID),
        Me.class);
    var membership = json.readValue(("{\"eventId\":\"%s\",\"eventName\":\"E\",\"eventStatus\":\"Draft\","
        + "\"role\":\"RegularUser\"}").formatted(ID), Membership.class);
    var member = json.readValue("{\"userId\":\"%s\",\"displayName\":\"A\",\"role\":\"RegularUser\"}".formatted(ID),
        Member.class);
    assertThat(me.globalRoles()).isEmpty();
    assertThat(membership.teamIds()).isEmpty();
    assertThat(member.teamIds()).isEmpty();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("models")
  @DisplayName("191 D2 an unknown field is refused by every generated model")
  void unknownFieldIsRefused(ModelCase c) {
    var type = c.instance().getClass();

    assertThat(json.readValue(c.valid() + "}", type)).isEqualTo(c.instance());
    assertThatThrownBy(() -> json.readValue(c.valid() + ",\"isAdmin\":true}", type))
        .isInstanceOf(JacksonException.class);
  }

  /** "METHOD path" of every operation of contracts/openapi.yaml (path items also hold a {@code parameters} list). */
  @SuppressWarnings("unchecked")
  static Set<String> contractOperations() {
    try (var in = Files.newBufferedReader(CONTRACT, StandardCharsets.UTF_8)) {
      var contract = (Map<String, Object>) new Yaml().load(in);
      var operations = new TreeSet<String>();
      ((Map<String, Object>) contract.get("paths")).forEach((path, item) ->
          ((Map<String, Object>) item).forEach((key, value) -> {
            if (value instanceof Map) {
              operations.add(key.toUpperCase() + " " + path);
            }
          }));
      return operations;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String eventPath(String suffix) {
    return "/api/v1/events/" + UUID.randomUUID() + suffix;
  }

  private static final String NAME_ADMIN_BODY = "{\"userId\":\"" + ID + "\",\"role\":\"EventAdmin\"}";

  private record LaterStory(String method, String path, String body, String story) {}

  private static Stream<Arguments> laterStories() {
    // US-18 (#67) and US-12 (#63) are served now: they answer 404 on an event the caller cannot reach
    return Stream.of(
        new LaterStory("GET", "/api/v1/accounts?email=ana@farol.test", null, "US-06"),
        new LaterStory("POST", eventPath("/members"), NAME_ADMIN_BODY, "US-06")).map(Arguments::of);
  }

  @Test
  @DisplayName("191 AC-4 the service serves exactly the operations of openapi.yaml")
  void servesExactlyTheContractOperations() {
    var served = new TreeSet<String>();
    mappings.getHandlerMethods().forEach((info, handler) -> {
      if (handler.getBeanType().getPackageName().startsWith("pt.isep.labdsof.authusers")) {
        info.getMethodsCondition().getMethods().forEach(method ->
            info.getPathPatternsCondition().getPatternValues().forEach(path -> served.add(method + " " + path)));
      }
    });

    assertThat(served).isEqualTo(contractOperations());
  }

  @Test
  @DisplayName("191 AC-4 openapi.yaml holds the Sprint B endpoints of API outline §2")
  void contractHoldsTheSprintBEndpoints() {
    assertThat(contractOperations()).containsExactlyInAnyOrder(
        "GET /api/v1/status",
        "GET /api/v1/me",
        "GET /api/v1/me/memberships",
        "GET /api/v1/accounts",
        "GET /api/v1/events/{eventId}/members",
        "POST /api/v1/events/{eventId}/members",
        "POST /api/v1/events/{eventId}/members/me",
        "PUT /api/v1/events/{eventId}/members/{userId}/teams/{teamId}",
        "DELETE /api/v1/events/{eventId}/members/{userId}/teams/{teamId}");
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("laterStories")
  @DisplayName("191 AC-4 every operation of a later story answers 501 Problem Details naming its story")
  void laterStoriesAnswer501(LaterStory operation) {
    var response = new ApiClient(port).send(operation.method(), operation.path(),
        "Bearer " + JWKS.valid(UUID.randomUUID().toString()), operation.body());

    ApiClient.assertProblem(response, 501);
    assertThat(ApiClient.json(response).get("detail")).asString().contains(operation.story());
  }

  @ParameterizedTest(name = "[{index}] role={0}")
  @ValueSource(strings = {"EventAdmin", "RegularUser"})
  @DisplayName("191 AC-4 the member list takes the contract's role values and a team id")
  void memberListTakesTheContractRoles(String role) {
    var path = eventPath("/members") + "?role=" + role + "&teamId=" + UUID.randomUUID();

    var response = new ApiClient(port).get(path, JWKS.valid(UUID.randomUUID().toString()));

    // Accepted and answered as US-18 D2 asks — not 400: the filters are the contract's (#67)
    ApiClient.assertProblem(response, 404);
  }

  @ParameterizedTest(name = "[{index}] {0} {1}")
  @CsvSource({"GET,/api/v1/nothing-here,404", "PATCH,/api/v1/me,405", "DELETE,/api/v1/status,405"})
  @DisplayName("191 AC-4 a path or method outside the contract answers 404 or 405 Problem Details")
  void pathsOutsideTheContractAreRefused(String method, String path, int status) {
    var response = new ApiClient(port).send(method, path, "Bearer " + JWKS.valid(UUID.randomUUID().toString()));

    ApiClient.assertProblem(response, status);
  }

  @Test
  @DisplayName("191 AC-4 the contract is served at /openapi.yaml without a token")
  void contractIsServedWithoutAToken() throws IOException {
    var response = new ApiClient(port).get("/openapi.yaml", null);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).isEqualTo(Files.readString(CONTRACT, StandardCharsets.UTF_8));
  }
}
