package pt.isep.labdsof.reports.adapters.out.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Preloads the model and keeps it loaded (local AI decisions: a cold call takes over 50 s, so the model must be
 * preloaded and kept loaded; spec US-34 D13). Only when the triage consumer runs and an AI is configured.
 */
@Component
class TriageWarmUp {

  private final OllamaTriageModel model;
  private final boolean enabled;

  TriageWarmUp(OllamaTriageModel model, @Value("${farol.triage.enabled:true}") boolean enabled) {
    this.model = model;
    this.enabled = enabled && model.configured();
  }

  /** At start-up, off the main thread: the warm-up can take a minute and must not delay readiness. */
  @EventListener(ApplicationReadyEvent.class)
  void warmUp() {
    if (enabled) {
      Thread.ofVirtual().name("ai-warm-up").start(model::warmUp);
    }
  }

  /** Renews the keep-alive (10 min) well before it ends, without generating. */
  @Scheduled(fixedDelayString = "${farol.ai.keep-loaded-interval:5m}",
      initialDelayString = "${farol.ai.keep-loaded-interval:5m}")
  void keepLoaded() {
    if (enabled) {
      model.keepLoaded();
    }
  }
}
