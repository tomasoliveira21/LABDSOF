package pt.isep.labdsof.transports.adapters.out.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import pt.isep.labdsof.transports.application.ImportObserver;
import pt.isep.labdsof.transports.application.ImportRun;
import pt.isep.labdsof.transports.application.SourceImports;
import pt.isep.labdsof.transports.domain.ImportOutcome;

/**
 * The D2 panel's metrics of architecture 9.2 (spec US-40 D10), from every recorded run. {@code farol.transport.import}
 * counts the runs by {@code result}, each registered at 0 so the first run shows as an increase.
 * {@code farol.transport.data.age} is the seconds since the last valid import ended, {@code NaN} before any: that time
 * is read from source_import once Transports is ready and then moved by each {@code Succeeded} run, so a scrape never
 * waits for the database, and the age keeps growing while the database is down. Resilience4j's starter binds the
 * {@code transport-source} circuit breaker and retry.
 */
@Component
class MicrometerImportObserver implements ImportObserver {

  static final String IMPORTS = "farol.transport.import";
  static final String DATA_AGE = "farol.transport.data.age";

  private final Map<ImportOutcome, Counter> counters = new EnumMap<>(ImportOutcome.class);
  private final AtomicReference<Instant> lastValidImport = new AtomicReference<>();
  private final SourceImports imports;
  private final Clock clock;

  MicrometerImportObserver(MeterRegistry registry, SourceImports imports, Clock clock) {
    this.imports = imports;
    this.clock = clock;
    for (var outcome : ImportOutcome.values()) {
      counters.put(outcome, Counter.builder(IMPORTS)
          .description("Runs of the transport source import, by result")
          .tag("result", outcome.name().toLowerCase(Locale.ROOT))
          .register(registry));
    }
    Gauge.builder(DATA_AGE, this, MicrometerImportObserver::dataAge)
        .description("Seconds since the last valid import of the transport source")
        .baseUnit("seconds")
        .register(registry);
  }

  /** The last valid import before this start, once the database is migrated. */
  @EventListener(ApplicationReadyEvent.class)
  void readLastValidImport() {
    imports.lastSucceededAt().ifPresent(this::validImportAt);
  }

  @Override
  public void observed(ImportRun run) {
    var recorded = run.sourceImport();
    counters.get(recorded.outcome()).increment();
    if (recorded.outcome() == ImportOutcome.Succeeded) {
      validImportAt(recorded.finishedAt());
    }
  }

  /** The later of the two: a run observed before the start-up read is not moved back. */
  private void validImportAt(Instant at) {
    lastValidImport.accumulateAndGet(at, (known, next) -> known == null || next.isAfter(known) ? next : known);
  }

  private double dataAge() {
    var at = lastValidImport.get();
    return at == null ? Double.NaN : Duration.between(at, clock.instant()).toMillis() / 1000.0;
  }
}
