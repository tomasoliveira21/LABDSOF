package pt.isep.labdsof.reports.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TriageViewTest {

  static final Instant SUBMITTED = Instant.parse("2027-05-08T23:14:05Z");

  @Test
  @DisplayName("194 AC-1 a request still Pending before 30 s reads as Pending")
  void pendingBeforeDeadline() {
    var seen = TriageSuggestion.pending().asSeenAt(SUBMITTED, SUBMITTED.plusMillis(29_999));
    assertThat(seen).isEqualTo(TriageSuggestion.pending());
  }

  @Test
  @DisplayName("194 AC-1 a request still Pending at 30 s reads as NotTriaged with reason Timeout")
  void notTriagedAtDeadline() {
    var seen = TriageSuggestion.pending().asSeenAt(SUBMITTED, SUBMITTED.plusSeconds(30));
    assertThat(seen.state()).isEqualTo(TriageState.NotTriaged);
    assertThat(seen.notTriagedReason()).isEqualTo(NotTriagedReason.Timeout);
    assertThat(seen.suggestedCategory()).isNull();
    assertThat(seen.suggestedPriority()).isNull();
  }

  @Test
  @DisplayName("194 AC-1 a suggestion is never turned into NotTriaged")
  void suggestionKept() {
    var suggested = new TriageSuggestion(TriageState.Suggested, Category.Health, Priority.High,
        "Fainting reported", null);
    assertThat(suggested.asSeenAt(SUBMITTED, SUBMITTED.plusSeconds(3600))).isSameAs(suggested);
  }

  @Test
  @DisplayName("194 AC-1 a triage always has a state")
  void stateRequired() {
    assertThatThrownBy(() -> new TriageSuggestion(null, null, null, null, null))
        .isInstanceOf(NullPointerException.class);
  }
}
