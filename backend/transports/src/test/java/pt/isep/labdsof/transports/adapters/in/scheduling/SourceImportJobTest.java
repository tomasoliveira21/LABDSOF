package pt.isep.labdsof.transports.adapters.in.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import pt.isep.labdsof.transports.Correlation;
import pt.isep.labdsof.transports.application.RecordedImports;
import pt.isep.labdsof.transports.application.SourceImportService;
import pt.isep.labdsof.transports.application.SourceReading;
import pt.isep.labdsof.transports.application.TransportSource;

@ExtendWith(OutputCaptureExtension.class)
class SourceImportJobTest {

  private final List<String> seen = new ArrayList<>();

  private SourceImportJob job(TransportSource source) {
    return new SourceImportJob(new SourceImportService(source, new RecordedImports(), Clock.systemUTC()));
  }

  @Test
  @DisplayName("193 AC-7 each run carries its own correlation id, removed after the run")
  void eachRunCarriesItsOwnCorrelationId() {
    var job = job(() -> {
      seen.add(MDC.get(Correlation.MDC_KEY));
      return SourceReading.succeeded(2);
    });

    job.run();
    job.run();

    assertThat(seen).hasSize(2).doesNotContainNull();
    assertThat(seen.get(0)).isNotEqualTo(seen.get(1));
    assertThat(MDC.get(Correlation.MDC_KEY)).isNull();
  }

  @Test
  @DisplayName("193 AC-6 a failed run is logged and does not stop the next one")
  void aFailedRunDoesNotStopTheNextOne(CapturedOutput output) {
    var job = new SourceImportJob(new SourceImportService(() -> SourceReading.succeeded(1),
        new RecordedImports(new IllegalStateException("database down")), Clock.systemUTC()));

    assertThatCode(job::run).doesNotThrowAnyException();
    assertThat(MDC.get(Correlation.MDC_KEY)).isNull();
    assertThat(output.getAll()).contains("Transport source import failed").contains("database down");
  }

  @Test
  @DisplayName("193 AC-6 a successful read is logged at INFO, a failed one at WARN")
  void theLevelFollowsTheOutcome(CapturedOutput output) {
    job(() -> SourceReading.succeeded(4)).run();
    job(() -> SourceReading.invalid("not a JSON array")).run();

    var lines = output.getAll().lines().filter(line -> line.contains("Transport source read")).toList();
    assertThat(lines).anySatisfy(line -> assertThat(line).contains("INFO").contains("Succeeded"));
    assertThat(lines).anySatisfy(line -> assertThat(line).contains("WARN").contains("Invalid"));
    assertThat(lines).noneSatisfy(line -> assertThat(line).contains("WARN").contains("Succeeded"));
  }

  @Test
  @DisplayName("193 AC-6 each run is logged with its outcome")
  void eachRunIsLogged(CapturedOutput output) {
    job(() -> SourceReading.succeeded(4)).run();
    job(() -> SourceReading.unavailable("HTTP 503")).run();

    assertThat(output.getAll()).contains("Transport source read").contains("Succeeded").contains("Unavailable")
        .contains("HTTP 503");
  }
}
