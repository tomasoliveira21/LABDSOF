package pt.isep.labdsof.transports.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pt.isep.labdsof.transports.domain.ImportOutcome;
import pt.isep.labdsof.transports.domain.SourceImport;

class SourceImportServiceTest {

  private static final Instant T0 = Instant.parse("2027-05-08T23:14:05Z");

  /** A clock that moves 250 ms each time it is read. */
  private static final class SteppingClock extends Clock {
    private Instant now = T0;

    @Override
    public ZoneId getZone() {
      return ZoneId.of("UTC");
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      var current = now;
      now = now.plus(Duration.ofMillis(250));
      return current;
    }
  }

  private final List<SourceImport> recorded = new ArrayList<>();

  private SourceImport run(SourceReading reading) {
    return new SourceImportService(() -> reading, recorded::add, new SteppingClock()).runOnce();
  }

  @Test
  @DisplayName("193 AC-6 a run records a successful reading with its start and end times")
  void aSuccessfulRunIsRecorded() {
    var result = run(SourceReading.succeeded(7));

    assertThat(result).isEqualTo(new SourceImport(T0, T0.plusMillis(250), ImportOutcome.Succeeded, 7, null));
    assertThat(recorded).containsExactly(result);
  }

  @Test
  @DisplayName("193 AC-6 a run records an unavailable source with its reason")
  void anUnavailableRunIsRecorded() {
    var result = run(SourceReading.unavailable("HTTP 503"));

    assertThat(result).isEqualTo(new SourceImport(T0, T0.plusMillis(250), ImportOutcome.Unavailable, null,
        "HTTP 503"));
    assertThat(recorded).containsExactly(result);
  }

  @Test
  @DisplayName("193 AC-6 a run records an invalid answer with its reason")
  void anInvalidRunIsRecorded() {
    var result = run(SourceReading.invalid("not a JSON array"));

    assertThat(result).isEqualTo(new SourceImport(T0, T0.plusMillis(250), ImportOutcome.Invalid, null,
        "not a JSON array"));
    assertThat(recorded).containsExactly(result);
  }

  @Test
  @DisplayName("193 AC-6 the readings carry their outcome, count and reason")
  void readingsCarryTheirFields() {
    assertThat(SourceReading.succeeded(3)).isEqualTo(new SourceReading(ImportOutcome.Succeeded, 3, null));
    assertThat(SourceReading.unavailable("x")).isEqualTo(new SourceReading(ImportOutcome.Unavailable, null, "x"));
    assertThat(SourceReading.invalid("y")).isEqualTo(new SourceReading(ImportOutcome.Invalid, null, "y"));
  }
}
