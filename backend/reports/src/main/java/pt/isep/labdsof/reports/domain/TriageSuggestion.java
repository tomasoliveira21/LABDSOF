package pt.isep.labdsof.reports.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** The AI triage of one help request (API outline §4.1). */
public record TriageSuggestion(
    TriageState state,
    Category suggestedCategory,
    Priority suggestedPriority,
    String suggestedReason,
    NotTriagedReason notTriagedReason) {

  /** Scenario D1: without a suggestion 30 s after submission, the request reads as NotTriaged. */
  public static final Duration TRIAGE_DEADLINE = Duration.ofSeconds(30);

  public TriageSuggestion {
    Objects.requireNonNull(state, "state");
  }

  public static TriageSuggestion pending() {
    return new TriageSuggestion(TriageState.Pending, null, null, null, null);
  }

  /** The state a reader sees at {@code now}: NotTriaged is derived, never stored (decision 6, #177). */
  public TriageSuggestion asSeenAt(Instant submittedAt, Instant now) {
    if (state == TriageState.Pending && !now.isBefore(submittedAt.plus(TRIAGE_DEADLINE))) {
      return new TriageSuggestion(TriageState.NotTriaged, null, null, null, NotTriagedReason.Timeout);
    }
    return this;
  }
}
