package pt.isep.labdsof.reports.application;

import java.time.Duration;

/** Port: the AI model that suggests a triage (ADR-005; local Ollama, guide K1). */
public interface TriageModel {

  /** False when no model is configured in this stack (FAROL_AI_URL empty): the AI is not called. */
  boolean configured();

  /**
   * One call with the participant's description only (features.md §5.2 AI-1), never longer than {@code budget};
   * returns the model's parsed JSON output, unvalidated (the domain validates it).
   */
  Object suggest(String description, Duration budget) throws TriageModelException;
}
