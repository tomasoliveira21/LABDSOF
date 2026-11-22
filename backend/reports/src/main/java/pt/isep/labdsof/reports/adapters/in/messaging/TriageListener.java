package pt.isep.labdsof.reports.adapters.in.messaging;

import java.util.UUID;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import pt.isep.labdsof.reports.application.TriageService;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reports' triage consumer of its own HelpRequestSubmitted (API outline §7.4, §7.5; spec US-34 D1): one message at
 * a time, as the model serves one request at a time. Started only when {@code farol.triage.enabled} is true (the
 * default). An invalid message goes to the dead-letter queue (spec 194 D8); other event types are acknowledged.
 */
@Component
class TriageListener {

  record Payload(UUID requestId) {}

  private final TriageService triage;
  private final DeadLetters deadLetters;
  private final JsonMapper json;

  TriageListener(TriageService triage, DeadLetters deadLetters, JsonMapper json) {
    this.triage = triage;
    this.deadLetters = deadLetters;
    this.json = json.rebuild().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
  }

  @RabbitListener(queues = "reports.reports-events", autoStartup = "${farol.triage.enabled:true}")
  void onReportsEvent(Message message) {
    UUID eventId;
    UUID requestId;
    try {
      var envelope = LocalCopyListener.envelope(json, message);
      if (!"HelpRequestSubmitted".equals(envelope.type())) {
        return;
      }
      var payload = envelope.payload() == null ? null : json.treeToValue(envelope.payload(), Payload.class);
      if (envelope.eventId() == null || payload == null || payload.requestId() == null) {
        throw new IllegalArgumentException("HelpRequestSubmitted needs eventId and payload.requestId");
      }
      eventId = envelope.eventId();
      requestId = payload.requestId();
    } catch (JacksonException | IllegalArgumentException e) {
      deadLetters.recover(message, e);
      return;
    }
    triage.triage(eventId, requestId);
  }
}
