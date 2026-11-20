package pt.isep.labdsof.reports.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** The model's output is validated here, never repaired (spec US-34 D4, D5). */
class TriageSuggestionTest {

  static final Instant SUBMITTED = Instant.parse("2027-05-08T23:14:05Z");

  static Map<String, Object> output(Object category, Object priority, Object reason, Object abstain) {
    var map = new HashMap<String, Object>();
    map.put("category", category);
    map.put("priority", priority);
    map.put("reason", reason);
    map.put("abstain", abstain);
    return map;
  }

  @Test
  @DisplayName("229 AC-1 a valid model output is a suggestion")
  void validSuggestion() {
    var triage = TriageSuggestion.fromModel(output("Health", "High", "Pessoa caída; requer a equipa.", false));
    assertThat(triage).isEqualTo(new TriageSuggestion(TriageState.Suggested, Category.Health, Priority.High,
        "Pessoa caída; requer a equipa.", null));
    assertThat(TriageSuggestion.fromModel(output("Other", "Low", "x".repeat(240), false)).suggestedReason())
        .hasSize(240);
    for (var c : Category.values()) {
      for (var p : Priority.values()) {
        assertThat(TriageSuggestion.fromModel(output(c.name(), p.name(), "r", false)))
            .isEqualTo(TriageSuggestion.suggested(c, p, "r"));
      }
    }
  }

  @Test
  @DisplayName("229 AC-3 an abstention is NotTriaged Abstained")
  void abstention() {
    var triage = TriageSuggestion.fromModel(output(null, null, "Falta descrição.", true));
    assertThat(triage).isEqualTo(TriageSuggestion.notTriaged(NotTriagedReason.Abstained));
    assertThat(triage.state()).isEqualTo(TriageState.Pending);
    assertThat(triage.asSeenAt(SUBMITTED, SUBMITTED).state()).isEqualTo(TriageState.NotTriaged);
    assertThat(triage.asSeenAt(SUBMITTED, SUBMITTED).notTriagedReason()).isEqualTo(NotTriagedReason.Abstained);
  }

  static Stream<Arguments> invalid() {
    var extra = output("Health", "High", "r", false);
    extra.put("team", "Medical");
    var missing = output("Health", "High", "r", false);
    missing.remove("abstain");
    return Stream.of(
        Arguments.of("extra key", extra),
        Arguments.of("missing key", missing),
        Arguments.of("unknown category", output("Theft", "High", "r", false)),
        Arguments.of("unknown priority", output("Health", "Urgent", "r", false)),
        Arguments.of("category not a string", output(1, "High", "r", false)),
        Arguments.of("priority not a string", output("Health", List.of("High"), "r", false)),
        Arguments.of("empty reason", output("Health", "High", "", false)),
        Arguments.of("reason over 240", output("Health", "High", "x".repeat(241), false)),
        Arguments.of("reason not a string", output("Health", "High", 7, false)),
        Arguments.of("null reason", output("Health", "High", null, false)),
        Arguments.of("control character", output("Health", "High", "a\u0007b", false)),
        Arguments.of("newline", output("Health", "High", "a\nb", false)),
        Arguments.of("delete character", output("Health", "High", "a\u007fb", false)),
        Arguments.of("abstain as a string", output("Health", "High", "r", "false")),
        Arguments.of("abstain null", output("Health", "High", "r", null)),
        Arguments.of("abstention with a category", output("Health", null, "r", true)),
        Arguments.of("abstention with a priority", output(null, "High", "r", true)),
        Arguments.of("suggestion without a priority", output("Health", null, "r", false)),
        Arguments.of("suggestion without a category", output(null, "High", "r", false)),
        Arguments.of("not an object", List.of("Health")),
        Arguments.of("null", null),
        Arguments.of("map with a non-string key", Map.of(1, "x", "category", "Health", "priority", "High",
            "reason", "r")));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalid")
  @DisplayName("229 AC-3 an invalid model output is NotTriaged InvalidOutput")
  void invalidOutput(String name, Object output) {
    assertThat(TriageSuggestion.fromModel(output)).isEqualTo(TriageSuggestion.notTriaged(NotTriagedReason.InvalidOutput));
  }

  @Test
  @DisplayName("229 AC-3 a stored reason reads NotTriaged at once")
  void storedReason() {
    for (var reason : Arrays.stream(NotTriagedReason.values()).toList()) {
      var stored = TriageSuggestion.notTriaged(reason);
      assertThat(stored.awaitsModel()).isFalse();
      assertThat(stored.asSeenAt(SUBMITTED, SUBMITTED)).isEqualTo(
          new TriageSuggestion(TriageState.NotTriaged, null, null, null, reason));
      assertThat(stored.asSeenAt(SUBMITTED, SUBMITTED.plusSeconds(60)).notTriagedReason()).isEqualTo(reason);
    }
    assertThat(TriageSuggestion.pending().awaitsModel()).isTrue();
    assertThat(TriageSuggestion.suggested(Category.Health, Priority.Low, "r").awaitsModel()).isFalse();
    assertThat(TriageSuggestion.pending().asSeenAt(SUBMITTED, SUBMITTED)).isEqualTo(TriageSuggestion.pending());
  }
}
