package pt.isep.labdsof.authusers.adapters.in.messaging;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import pt.isep.labdsof.authusers.Correlation;
import pt.isep.labdsof.authusers.application.EventCopies;
import pt.isep.labdsof.authusers.application.MyMemberships.Status;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
class EventCopyListener {

  private static final Logger log = LoggerFactory.getLogger(EventCopyListener.class);

  private final EventCopies copies;
  private final DeadLetters deadLetters;
  private final JsonMapper json;

  EventCopyListener(EventCopies copies, DeadLetters deadLetters, JsonMapper json) {
    this.copies = copies;
    this.deadLetters = deadLetters;
    this.json = json;
  }

  @RabbitListener(queues = "auth-users.events-events")
  void onEvent(Message message) {
    var properties = message.getMessageProperties();
    try {
      if (properties.getCorrelationId() != null) {
        MDC.put(Correlation.MDC_KEY, properties.getCorrelationId());
      }
      handle(message);
    } finally {
      MDC.remove(Correlation.MDC_KEY);
    }
  }

  private void handle(Message message) {
    String messageId = message.getMessageProperties().getMessageId();
    try {
      // Read known fields while allowing producers to add fields to the envelope and payload.
      var envelope = json.readTree(message.getBody());
      if (envelope == null || !envelope.isObject()) {
        throw new IllegalArgumentException("Expected Events envelope");
      }
      messageId = UUID.fromString(text(envelope, "messageId")).toString();
      validateEnvelope(envelope);
      var eventId = UUID.fromString(text(envelope, "eventId"));
      var payload = envelope.path("payload");
      var type = text(envelope, "type");
      switch (type) {
        case "EventChanged" -> copies.event(eventId, text(payload, "name"),
            Status.valueOf(text(payload, "status")), version(payload));
        case "TeamChanged" -> copies.team(eventId, UUID.fromString(text(payload, "teamId")),
            json.writeValueAsString(payload), version(payload));
        default -> log.atInfo().addKeyValue("messageId", messageId).addKeyValue("type", type)
            .log("Event type not handled by Auth & Users yet; acknowledged");
      }
    } catch (JacksonException | IllegalArgumentException | DateTimeParseException error) {
      log.atWarn()
          .addKeyValue("messageId", String.valueOf(messageId))
          .addKeyValue("reason", DeadLetters.describe(error))
          .log("Invalid message sent to the dead-letter queue");
      deadLetters.recover(message, error);
    }
  }

  private static void validateEnvelope(JsonNode envelope) {
    var schema = envelope.path("schemaVersion");
    if (!schema.isIntegralNumber() || !schema.canConvertToLong() || schema.asLong() != 1) {
      throw new IllegalArgumentException("Unsupported Events envelope version");
    }
    Instant.parse(text(envelope, "occurredAt"));
    if (!"events".equals(text(envelope, "producer"))) {
      throw new IllegalArgumentException("Expected Events producer");
    }
    if (!envelope.path("actorUserId").isString()) {
      throw new IllegalArgumentException("Missing actorUserId");
    }
  }

  private static long version(JsonNode payload) {
    var version = payload.path("version");
    if (!version.isIntegralNumber() || !version.canConvertToLong() || version.asLong() < 1) {
      throw new IllegalArgumentException("Invalid event version");
    }
    return version.asLong();
  }

  private static String text(JsonNode node, String field) {
    var value = node.path(field);
    if (!value.isString() || value.asString().isBlank()) {
      throw new IllegalArgumentException("Missing required Events field " + field);
    }
    return value.asString();
  }
}
