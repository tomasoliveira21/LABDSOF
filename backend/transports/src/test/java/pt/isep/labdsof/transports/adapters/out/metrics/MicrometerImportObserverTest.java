package pt.isep.labdsof.transports.adapters.out.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pt.isep.labdsof.transports.application.ImportRun;
import pt.isep.labdsof.transports.application.SourceImports;
import pt.isep.labdsof.transports.domain.ImportOutcome;
import pt.isep.labdsof.transports.domain.SourceImport;

class MicrometerImportObserverTest {

  private static final Instant T0 = Instant.parse("2027-05-08T23:14:05Z");

  /** A clock the test moves. */
  private static final class MovingClock extends Clock {
    private Instant now = T0;

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  /** source_import as the observer sees it: the last valid import, and how often it was asked. */
  private static final class LastValid implements SourceImports {
    private Optional<Instant> last = Optional.empty();
    private int reads;
    private boolean down;

    @Override
    public void record(SourceImport sourceImport) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<Instant> lastSucceededAt() {
      reads++;
      if (down) {
        throw new IllegalStateException("database unavailable");
      }
      return last;
    }
  }

  private final MeterRegistry registry = new SimpleMeterRegistry();
  private final MovingClock clock = new MovingClock();
  private final LastValid imports = new LastValid();
  private final MicrometerImportObserver observer = new MicrometerImportObserver(registry, imports, clock);

  private double count(String result) {
    return registry.get("farol.transport.import").tag("result", result).counter().count();
  }

  private double dataAge() {
    return registry.get("farol.transport.data.age").gauge().value();
  }

  private static ImportRun run(ImportOutcome outcome, Instant finishedAt) {
    var succeeded = outcome == ImportOutcome.Succeeded;
    return new ImportRun(new SourceImport(finishedAt.minusSeconds(1), finishedAt, outcome, succeeded ? 4 : null,
        succeeded ? null : "HTTP 503"), 0, 1);
  }

  @Test
  @DisplayName("US-40 D10 the three results are counted from 0 before any run, so the first one shows as an increase")
  void countersStartAtZero() {
    assertThat(count("succeeded")).isZero();
    assertThat(count("invalid")).isZero();
    assertThat(count("unavailable")).isZero();
  }

  @Test
  @DisplayName("US-40 AC-7 each run counts its result")
  void eachRunCountsItsResult() {
    observer.observed(run(ImportOutcome.Invalid, T0));
    observer.observed(run(ImportOutcome.Succeeded, T0));
    observer.observed(run(ImportOutcome.Invalid, T0));

    assertThat(count("invalid")).isEqualTo(2);
    assertThat(count("succeeded")).isEqualTo(1);
    assertThat(count("unavailable")).isZero();
  }

  @Test
  @DisplayName("US-40 D10 the data age is NaN before any valid import, then the seconds since the last one ended; a "
      + "failed run does not move it")
  void dataAgeFollowsTheValidImports() {
    assertThat(dataAge()).isNaN();

    observer.observed(run(ImportOutcome.Succeeded, T0));
    clock.now = T0.plusMillis(42_500);
    observer.observed(run(ImportOutcome.Unavailable, clock.now));

    assertThat(dataAge()).isEqualTo(42.5);
  }

  @Test
  @DisplayName("US-40 D10 the data age starts from source_import once Transports is ready, and a scrape never reads "
      + "the database: with it down, the age keeps growing")
  void dataAgeIsReadFromTheDatabaseOnce() {
    imports.last = Optional.of(T0.minusSeconds(60));
    observer.readLastValidImport();
    imports.down = true;

    var first = dataAge();
    clock.now = T0.plusSeconds(30);

    assertThat(first).isEqualTo(60.0);
    assertThat(dataAge()).isEqualTo(90.0);
    assertThat(imports.reads).isEqualTo(1);
  }

  @Test
  @DisplayName("US-40 D10 an older valid import read at start-up does not move the data age back")
  void olderTimeDoesNotMoveTheAgeBack() {
    observer.observed(run(ImportOutcome.Succeeded, T0));
    imports.last = Optional.of(T0.minusSeconds(60));

    observer.readLastValidImport();

    assertThat(dataAge()).isZero();
  }
}
