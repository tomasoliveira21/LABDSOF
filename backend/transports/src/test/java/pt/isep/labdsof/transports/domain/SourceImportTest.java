package pt.isep.labdsof.transports.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class SourceImportTest {

  private static final Instant START = Instant.parse("2027-05-08T23:14:05Z");
  private static final Instant END = START.plusMillis(420);

  @Test
  @DisplayName("193 AC-6 a successful import has a vehicle count and no error")
  void aSuccessfulImportHasACount() {
    var none = new SourceImport(START, END, ImportOutcome.Succeeded, 0, null);
    assertThat(none.vehicleCount()).isZero();
    assertThat(none.error()).isNull();
    assertThat(new SourceImport(START, START, ImportOutcome.Succeeded, 13, null).vehicleCount()).isEqualTo(13);

    assertThatThrownBy(() -> new SourceImport(START, END, ImportOutcome.Succeeded, null, null))
        .isInstanceOf(IllegalArgumentException.class).hasMessage("a successful import has a vehicle count");
    assertThatThrownBy(() -> new SourceImport(START, END, ImportOutcome.Succeeded, -1, null))
        .isInstanceOf(IllegalArgumentException.class).hasMessage("a vehicle count cannot be negative");
    assertThatThrownBy(() -> new SourceImport(START, END, ImportOutcome.Succeeded, 3, "HTTP 503"))
        .isInstanceOf(IllegalArgumentException.class).hasMessage("a successful import has no error");
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(value = ImportOutcome.class, names = {"Unavailable", "Invalid"})
  @DisplayName("193 AC-6 a failed import has an error and no vehicle count")
  void aFailedImportHasAnError(ImportOutcome outcome) {
    var failed = new SourceImport(START, END, outcome, null, "  HTTP 503  ");
    assertThat(failed.outcome()).isEqualTo(outcome);
    assertThat(failed.error()).isEqualTo("HTTP 503");
    assertThat(failed.vehicleCount()).isNull();

    assertThatThrownBy(() -> new SourceImport(START, END, outcome, 3, "HTTP 503"))
        .isInstanceOf(IllegalArgumentException.class).hasMessage("a failed import has no vehicle count");
    for (var blank : new String[] {null, "", "  ", "\u0000"}) {
      assertThatThrownBy(() -> new SourceImport(START, END, outcome, null, blank))
          .isInstanceOf(IllegalArgumentException.class).hasMessage("a failed import has an error");
    }
  }

  @Test
  @DisplayName("193 AC-6 an import cannot finish before it starts, and needs its times and outcome")
  void anImportCannotFinishBeforeItStarts() {
    assertThatThrownBy(() -> new SourceImport(END, START, ImportOutcome.Succeeded, 0, null))
        .isInstanceOf(IllegalArgumentException.class).hasMessage("an import cannot finish before it starts");
    assertThatThrownBy(() -> new SourceImport(null, END, ImportOutcome.Succeeded, 0, null))
        .isInstanceOf(NullPointerException.class).hasMessage("startedAt");
    assertThatThrownBy(() -> new SourceImport(START, null, ImportOutcome.Succeeded, 0, null))
        .isInstanceOf(NullPointerException.class).hasMessage("finishedAt");
    assertThatThrownBy(() -> new SourceImport(START, END, null, 0, null))
        .isInstanceOf(NullPointerException.class).hasMessage("outcome");
    assertThat(new SourceImport(START, END, ImportOutcome.Succeeded, 0, null).startedAt()).isEqualTo(START);
    assertThat(new SourceImport(START, END, ImportOutcome.Succeeded, 0, null).finishedAt()).isEqualTo(END);
  }

  @Test
  @DisplayName("193 AC-6 an error is stored without NUL and cut to 500 characters")
  void anErrorIsCleaned() {
    assertThat(new SourceImport(START, END, ImportOutcome.Invalid, null, "a\u0000b").error()).isEqualTo("ab");

    var emoji = "🚌".repeat(501); // 501 buses, 1002 UTF-16 units
    var cut = new SourceImport(START, END, ImportOutcome.Invalid, null, emoji).error();
    assertThat(cut.codePointCount(0, cut.length())).isEqualTo(500);
    assertThat(cut).isEqualTo("🚌".repeat(500));

    var exactly = "e".repeat(500);
    assertThat(new SourceImport(START, END, ImportOutcome.Invalid, null, exactly).error()).isEqualTo(exactly);
  }
}
