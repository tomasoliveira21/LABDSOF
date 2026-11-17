package pt.isep.labdsof.authusers.adapters.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.networknt.schema.InputFormat;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import pt.isep.labdsof.authusers.ApiClient;
import pt.isep.labdsof.authusers.Infrastructure;
import pt.isep.labdsof.authusers.IntegrationTest;
import pt.isep.labdsof.authusers.adapters.out.persistence.JdbcOutbox;
import pt.isep.labdsof.authusers.application.OutgoingEvent;
import pt.isep.labdsof.authusers.domain.GlobalRole;
import pt.isep.labdsof.authusers.domain.UserAccount;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Spec D10 (SDD rule R3): what the outbox stores and sends matches contracts/events. */
class EventContractIT extends IntegrationTest {

  static final Path EVENTS = Path.of("contracts", "events");
  static final String EVENTS_QUEUE = "events.auth-users-events";
  static final String REPORTS_QUEUE = "reports.auth-users-events";
  static final RabbitTemplate ADMIN = Infrastructure.adminTemplate();
  static final String PROFILE = "user-profile.changed.schema.json";
  static final String ROLE = "global-role.changed.schema.json";
  static final String ENVELOPE = "envelope.schema.json";

  @Autowired JdbcOutbox outbox;
  @Autowired RabbitTemplate rabbit;
  @Autowired TransactionTemplate tx;
  @Autowired JsonMapper json;

  @BeforeEach
  void cleanSlate() {
    // Rows left by other test classes are not this test's concern; the broker queues start empty too.
    jdbc.sql("UPDATE outbox SET published_at = now() WHERE published_at IS NULL").update();
    var admin = new RabbitAdmin(ADMIN.getConnectionFactory());
    admin.purgeQueue(EVENTS_QUEUE, false);
    admin.purgeQueue(REPORTS_QUEUE, false);
  }

  static JsonSchema schema(String file) {
    var config = SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build();
    return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
        .getSchema(SchemaLocation.of(EVENTS.resolve(file).toAbsolutePath().toUri().toString()), config);
  }

  static String schemaOf(String type) {
    return "UserProfileChanged".equals(type) ? PROFILE : ROLE;
  }

  /** The first GET /me of the bootstrap account, through the real HTTP path: it stores both events. */
  UUID firstBootstrapCall() {
    var user = UUID.randomUUID();
    var token = JWKS.sign(JWKS.claims(user.toString()).claim("email", Infrastructure.BOOTSTRAP_EMAIL)
        .claim("email_verified", true).claim("name", "Daniel Admin").build());
    assertThat(new ApiClient(port).get("/api/v1/me", token).statusCode()).isEqualTo(200);
    return user;
  }

  String storedBody(UUID user, String type) {
    return jdbc.sql("SELECT body::text FROM outbox WHERE type = :type AND body->'payload'->>'userId' = :id")
        .param("type", type).param("id", user.toString()).query(String.class).single();
  }

  List<Message> drain(String queue) {
    var messages = new ArrayList<Message>();
    Message message;
    while ((message = ADMIN.receive(queue, 500)) != null) {
      messages.add(message);
    }
    return messages;
  }

  static String text(Message message) {
    return new String(message.getBody(), StandardCharsets.UTF_8);
  }

  @Test
  @DisplayName("191 D10 the UserProfileChanged and GlobalRoleChanged the outbox stores for a first GET /me match "
      + "their JSON Schemas")
  void storedBodiesMatchTheSchemas() {
    var user = firstBootstrapCall();

    for (var type : List.of("UserProfileChanged", "GlobalRoleChanged")) {
      var body = storedBody(user, type);
      assertThat(schema(schemaOf(type)).validate(body, InputFormat.JSON)).as(type).isEmpty();
      assertThat(schema(ENVELOPE).validate(body, InputFormat.JSON)).as(type + " envelope").isEmpty();
    }
  }

  @Test
  @DisplayName("191 D10 the UserProfileChanged and GlobalRoleChanged the outbox sends to RabbitMQ match their "
      + "JSON Schemas")
  void sentMessagesMatchTheSchemas() {
    firstBootstrapCall();

    var published = new OutboxPublisher(outbox, rabbit, tx, Clock.systemUTC(), 50, Duration.ofSeconds(2))
        .publishDue();
    assertThat(published).isEqualTo(2);

    var events = drain(EVENTS_QUEUE);
    assertThat(events).extracting(m -> m.getMessageProperties().getType())
        .containsExactly("UserProfileChanged", "GlobalRoleChanged");
    for (var message : events) {
      var type = message.getMessageProperties().getType();
      assertThat(schema(schemaOf(type)).validate(text(message), InputFormat.JSON)).as(type).isEmpty();
    }

    var reports = drain(REPORTS_QUEUE);
    assertThat(reports).hasSize(1);
    assertThat(reports.get(0).getMessageProperties().getType()).isEqualTo("UserProfileChanged");
    assertThat(schema(PROFILE).validate(text(reports.get(0)), InputFormat.JSON)).isEmpty();
  }

  static Arguments broken(String label, String type, String schemaFile, Consumer<ObjectNode> breakIt) {
    return Arguments.of(label, type, schemaFile, breakIt);
  }

  static ObjectNode payload(ObjectNode tree) {
    return (ObjectNode) tree.get("payload");
  }

  static Stream<Arguments> invalidMessages() {
    return Stream.of(
        broken("UserProfileChanged without payload.displayName", "UserProfileChanged", PROFILE,
            tree -> payload(tree).remove("displayName")),
        broken("UserProfileChanged with payload.version as a string", "UserProfileChanged", PROFILE,
            tree -> payload(tree).put("version", "1")),
        broken("UserProfileChanged with a payload.userId that is not a uuid", "UserProfileChanged", PROFILE,
            tree -> payload(tree).put("userId", "rita")),
        broken("UserProfileChanged with an e-mail in the payload (M-10)", "UserProfileChanged", PROFILE,
            tree -> payload(tree).put("email", "ana@farol.test")),
        broken("UserProfileChanged with a displayName of 101 characters", "UserProfileChanged", PROFILE,
            tree -> payload(tree).put("displayName", "a".repeat(101))),
        broken("UserProfileChanged with an eventId", "UserProfileChanged", PROFILE,
            tree -> tree.put("eventId", UUID.randomUUID().toString())),
        broken("UserProfileChanged without messageId (the envelope through allOf)", "UserProfileChanged", PROFILE,
            tree -> tree.remove("messageId")),
        broken("UserProfileChanged from another producer", "UserProfileChanged", PROFILE,
            tree -> tree.put("producer", "reports")),
        broken("GlobalRoleChanged with role EventAdmin", "GlobalRoleChanged", ROLE,
            tree -> payload(tree).put("role", "EventAdmin")),
        broken("GlobalRoleChanged with granted as a string", "GlobalRoleChanged", ROLE,
            tree -> payload(tree).put("granted", "true")),
        broken("GlobalRoleChanged without payload.version", "GlobalRoleChanged", ROLE,
            tree -> payload(tree).remove("version")),
        broken("envelope with schemaVersion 0", "UserProfileChanged", ENVELOPE,
            tree -> tree.put("schemaVersion", 0)));
  }

  private String validBody(String type) {
    var id = UUID.randomUUID();
    var event = "UserProfileChanged".equals(type)
        ? OutgoingEvent.userProfileChanged(UUID.randomUUID(),
            UserAccount.provision(id, id + "@farol.test", "Ana Silva", Instant.now()), id.toString())
        : OutgoingEvent.globalRoleChanged(UUID.randomUUID(), id, GlobalRole.FarolAdmin, true, 1, Instant.now(), "");
    return json.writeValueAsString(event.envelope());
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("invalidMessages")
  @DisplayName("191 D10 a message with a missing field, a wrong type or an unexpected value is refused by its schema")
  void brokenMessagesAreRefused(String label, String type, String schemaFile, Consumer<ObjectNode> breakIt) {
    var valid = validBody(type);
    assertThat(schema(schemaFile).validate(valid, InputFormat.JSON)).as("positive control").isEmpty();

    var tree = (ObjectNode) json.readTree(valid);
    breakIt.accept(tree);

    assertThat(schema(schemaFile).validate(json.writeValueAsString(tree), InputFormat.JSON)).isNotEmpty();
  }
}
