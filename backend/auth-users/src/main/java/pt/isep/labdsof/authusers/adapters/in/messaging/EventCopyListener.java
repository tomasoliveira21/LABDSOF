package pt.isep.labdsof.authusers.adapters.in.messaging;

import java.util.UUID;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import pt.isep.labdsof.authusers.adapters.out.persistence.JdbcEventCopies;
import pt.isep.labdsof.authusers.application.MyMemberships.Status;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
class EventCopyListener {

  private final JdbcEventCopies copies;
  private final JsonMapper json;

  EventCopyListener(JdbcEventCopies copies, JsonMapper json) {
    this.copies = copies;
    this.json = json;
  }

  @RabbitListener(queues = "auth-users.events-events")
  void onEvent(Message message) {
    try {
      var envelope = json.readTree(message.getBody());
      if (envelope == null || !envelope.isObject()) {
        throw new IllegalArgumentException("Expected Events envelope");
      }
      var eventId = UUID.fromString(text(envelope, "eventId"));
      var payload = envelope.path("payload");
      var version = payload.path("version");
      var schemaVersion = envelope.path("schemaVersion");
      if (!schemaVersion.isIntegralNumber() || schemaVersion.asLong() != 1 || !version.isIntegralNumber()
          || !version.canConvertToLong() || version.asLong() < 1) {
        throw new IllegalArgumentException("Invalid event version");
      }
      switch (text(envelope, "type")) {
        case "EventChanged" -> copies.event(eventId, text(payload, "name"),
            Status.valueOf(text(payload, "status")), version.asLong());
        case "TeamChanged" -> copies.team(eventId, UUID.fromString(text(payload, "teamId")),
            json.writeValueAsString(payload), version.asLong());
        default -> throw new IllegalArgumentException("Unsupported Events message");
      }
    } catch (JacksonException | IllegalArgumentException error) {
      throw new AmqpRejectAndDontRequeueException("Invalid Events message", error);
    }
  }

  private static String text(JsonNode node, String field) {
    var value = node.path(field);
    if (!value.isString() || value.asString().isBlank()) {
      throw new IllegalArgumentException("Missing required Events field " + field);
    }
    return value.asString();
  }
}
