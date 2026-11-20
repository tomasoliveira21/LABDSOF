package pt.isep.labdsof.reports.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** The AI triage of one help request (API outline §4.1). */
public record TriageSuggestion(
    TriageState state,
    Category suggestedCategory,
    Priority suggestedPriority,
    String suggestedReason,
    NotTriagedReason notTriagedReason) {

  /** Scenario D1: without a suggestion 30 s after submission, the request reads as NotTriaged. */
  public static final Duration TRIAGE_DEADLINE = Duration.ofSeconds(30);

  /** Output schema of the evaluation package and production contract item 2 (spec US-34 D9). */
  public static final int MAX_REASON = 240;

  private static final Set<String> KEYS = Set.of("category", "priority", "reason", "abstain");

  public TriageSuggestion {
    Objects.requireNonNull(state, "state");
  }

  public static TriageSuggestion pending() {
    return new TriageSuggestion(TriageState.Pending, null, null, null, null);
  }

  public static TriageSuggestion suggested(Category category, Priority priority, String reason) {
    return new TriageSuggestion(TriageState.Suggested, category, priority, reason, null);
  }

  /**
   * No suggestion, for this reason. NotTriaged is never stored (decision 6, #177), so it is kept as Pending with
   * its reason and read as NotTriaged at once (spec US-34 D5).
   */
  public static TriageSuggestion notTriaged(NotTriagedReason reason) {
    return new TriageSuggestion(TriageState.Pending, null, null, null, Objects.requireNonNull(reason, "reason"));
  }

  /** Still waiting for the model: Pending and no reason stored. */
  public boolean awaitsModel() {
    return state == TriageState.Pending && notTriagedReason == null;
  }

  /**
   * The model's output, validated and never repaired (ADR-005 decision 3; spec US-34 D4): exactly the four keys of
   * the output schema, known values, a reason of 1 to 240 characters without control characters, and an abstention
   * with neither category nor priority. An abstention is Abstained; anything else invalid is InvalidOutput.
   */
  public static TriageSuggestion fromModel(Object output) {
    if (!(output instanceof Map<?, ?> map) || !KEYS.equals(map.keySet())
        || !(map.get("abstain") instanceof Boolean abstain) || !validReason(map.get("reason"))) {
      return notTriaged(NotTriagedReason.InvalidOutput);
    }
    var category = value(Category.class, map.get("category"));
    var priority = value(Priority.class, map.get("priority"));
    if (abstain) {
      return map.get("category") == null && map.get("priority") == null
          ? notTriaged(NotTriagedReason.Abstained) : notTriaged(NotTriagedReason.InvalidOutput);
    }
    return category == null || priority == null
        ? notTriaged(NotTriagedReason.InvalidOutput) : suggested(category, priority, (String) map.get("reason"));
  }

  private static boolean validReason(Object reason) {
    return reason instanceof String text && !text.isEmpty() && text.length() <= MAX_REASON
        && text.chars().noneMatch(c -> c < 0x20 || c == 0x7f);
  }

  private static <E extends Enum<E>> E value(Class<E> type, Object name) {
    for (var constant : type.getEnumConstants()) {
      if (constant.name().equals(name)) {
        return constant;
      }
    }
    return null;
  }

  /**
   * The state a reader sees at {@code now}: NotTriaged is derived, never stored (decision 6, #177). A stored reason
   * reads NotTriaged at once; a Pending without one reads NotTriaged/Timeout from the deadline on.
   */
  public TriageSuggestion asSeenAt(Instant submittedAt, Instant now) {
    if (state == TriageState.Pending && notTriagedReason != null) {
      return new TriageSuggestion(TriageState.NotTriaged, null, null, null, notTriagedReason);
    }
    if (state == TriageState.Pending && !now.isBefore(submittedAt.plus(TRIAGE_DEADLINE))) {
      return new TriageSuggestion(TriageState.NotTriaged, null, null, null, NotTriagedReason.Timeout);
    }
    return this;
  }
}
