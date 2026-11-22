package pt.isep.labdsof.transports.adapters.out.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Locale;
import org.springframework.stereotype.Component;
import pt.isep.labdsof.transports.application.ImportObserver;
import pt.isep.labdsof.transports.application.ImportRun;

/** Counts every recorded run by its result: {@code farol.transport.import{result}} (spec US-40 D10, architecture 9.2). */
@Component
class MicrometerImportObserver implements ImportObserver {

  static final String IMPORTS = "farol.transport.import";

  private final MeterRegistry registry;

  MicrometerImportObserver(MeterRegistry registry) {
    this.registry = registry;
  }

  @Override
  public void observed(ImportRun run) {
    Counter.builder(IMPORTS)
        .description("Runs of the transport source import, by result")
        .tag("result", run.sourceImport().outcome().name().toLowerCase(Locale.ROOT))
        .register(registry)
        .increment();
  }
}
