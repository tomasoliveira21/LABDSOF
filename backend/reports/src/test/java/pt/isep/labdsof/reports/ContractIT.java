package pt.isep.labdsof.reports;

import static org.assertj.core.api.Assertions.assertThat;

import com.networknt.schema.InputFormat;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.yaml.snakeyaml.Yaml;
import pt.isep.labdsof.reports.application.HelpRequestService;
import pt.isep.labdsof.reports.domain.Location;
import pt.isep.labdsof.reports.domain.Membership;

/** AC-6: the hand-written contracts match what Reports serves and sends (SDD rule R3, spec D14, D15). */
class ContractIT extends IntegrationTest {

  static final Path EVENTS = Path.of("contracts/events");

  @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings;
  @Autowired HelpRequestService service;

  @SuppressWarnings("unchecked")
  static Map<String, Map<String, Object>> paths() throws Exception {
    Map<String, Object> contract = new Yaml().load(Files.readString(Path.of("contracts/openapi.yaml")));
    return (Map<String, Map<String, Object>>) contract.get("paths");
  }

  /** "METHOD path" of the operations in the contract with the given x-farol-status. */
  @SuppressWarnings("unchecked")
  static Set<String> operations(String status) throws Exception {
    var operations = new HashSet<String>();
    paths().forEach((path, item) -> item.forEach((method, operation) -> {
      if (operation instanceof Map<?, ?> op && status.equals(((Map<String, Object>) op).get("x-farol-status"))) {
        operations.add(method.toUpperCase() + " " + path);
      }
    }));
    return operations;
  }

  @Test
  @DisplayName("194 AC-6 every endpoint Reports serves is in openapi.yaml as implemented, and the reverse")
  void servedMatchesContract() throws Exception {
    var served = new HashSet<String>();
    mappings.getHandlerMethods().forEach((info, handler) -> {
      if (((HandlerMethod) handler).getBeanType().getPackageName().startsWith("pt.isep.labdsof.reports")) {
        for (var method : info.getMethodsCondition().getMethods()) {
          for (var pattern : info.getPathPatternsCondition().getPatternValues()) {
            served.add(method.name() + " " + pattern);
          }
        }
      }
    });
    assertThat(served).containsExactlyInAnyOrderElementsOf(operations("implemented"));
  }

  @Test
  @DisplayName("194 AC-6 openapi.yaml has the Sprint B endpoints of API outline §4 and no demo route")
  void sprintBEndpoints() throws Exception {
    var all = new HashSet<>(operations("implemented"));
    all.addAll(operations("planned"));
    assertThat(all).containsExactlyInAnyOrder(
        "GET /api/v1/status",
        "POST /api/v1/events/{eventId}/help-requests",
        "GET /api/v1/events/{eventId}/help-requests",
        "GET /api/v1/events/{eventId}/help-requests/{requestId}",
        "PATCH /api/v1/events/{eventId}/help-requests/{requestId}",
        "POST /api/v1/events/{eventId}/help-requests/{requestId}/acknowledge",
        "POST /api/v1/events/{eventId}/help-requests/{requestId}/route",
        "POST /api/v1/events/{eventId}/help-requests/{requestId}/accept-suggestion",
        "POST /api/v1/events/{eventId}/help-requests/{requestId}/close",
        "GET /api/v1/events/{eventId}/help-requests/{requestId}/history",
        "GET /api/v1/events/{eventId}/triage-status",
        "GET /api/v1/events/{eventId}/field-tasks",
        "POST /api/v1/events/{eventId}/field-tasks/{taskId}/accept",
        "POST /api/v1/events/{eventId}/field-tasks/{taskId}/resolve");
    assertThat(paths().keySet()).noneMatch(path -> path.contains("/demo/"));
  }

  static JsonSchema schema(String file) {
    var config = SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build();
    return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
        .getSchema(SchemaLocation.of(EVENTS.resolve(file).toAbsolutePath().toUri().toString()), config);
  }

  @Test
  @DisplayName("194 AC-6 the HelpRequestSubmitted the outbox sends matches its JSON Schema")
  void helpRequestSubmitted() {
    var event = UUID.randomUUID();
    member(event, "ana", Membership.Role.RegularUser);
    var request = service.submit(event, "ana", UUID.randomUUID(), "Friend fainted",
        new Location(3, 0.4, 0.7)).request();
    var body = jdbc.sql("SELECT body::text FROM outbox WHERE body->'payload'->>'requestId' = :id")
        .param("id", request.id().toString()).query(String.class).single();

    var schema = schema("help-request.submitted.schema.json");
    assertThat(schema.validate(body, InputFormat.JSON)).isEmpty();
    // The schema keeps help-request text out of the broker (M-5)
    var leaking = body.replace("\"payload\": {", "\"payload\": {\"description\": \"Friend fainted\", ");
    assertThat(leaking).isNotEqualTo(body);
    assertThat(schema.validate(leaking, InputFormat.JSON)).isNotEmpty();
  }

  @Test
  @DisplayName("194 AC-6 the consumed MembershipChanged and TeamChanged match their JSON Schemas")
  void consumedEvents() {
    var envelope = """
        {"messageId":"%s","type":"%s","schemaVersion":1,"occurredAt":"2027-05-08T23:14:05Z",
         "producer":"%s","eventId":"%s","actorUserId":"","payload":%s}""";
    var membership = envelope.formatted(UUID.randomUUID(), "MembershipChanged", "auth-users", UUID.randomUUID(),
        "{\"userId\":\"u\",\"role\":\"RegularUser\",\"status\":\"Active\",\"teamIds\":[\"%s\"],\"version\":1}"
            .formatted(UUID.randomUUID()));
    var team = envelope.formatted(UUID.randomUUID(), "TeamChanged", "events", UUID.randomUUID(),
        "{\"teamId\":\"%s\",\"name\":\"Nursing\",\"permissions\":[{\"permission\":\"HelpRequests.Respond\"}],"
            .formatted(UUID.randomUUID())
            + "\"handledCategories\":[\"Health\"],\"deleted\":false,\"version\":2}");
    assertThat(schema("consumed/membership.changed.schema.json").validate(membership, InputFormat.JSON))
        .isEmpty();
    assertThat(schema("consumed/team.changed.schema.json").validate(team, InputFormat.JSON)).isEmpty();

    for (var invalid : List.of(membership.replace("RegularUser", "Owner"),
        membership.replace("\"version\":1", "\"version\":0"))) {
      assertThat(schema("consumed/membership.changed.schema.json").validate(invalid, InputFormat.JSON))
          .isNotEmpty();
    }
    assertThat(schema("consumed/team.changed.schema.json")
        .validate(team.replace("\"Health\"", "\"Theft\""), InputFormat.JSON)).isNotEmpty();
  }
}
