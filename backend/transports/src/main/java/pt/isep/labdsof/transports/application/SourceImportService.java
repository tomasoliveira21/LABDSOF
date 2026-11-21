package pt.isep.labdsof.transports.application;

import java.time.Clock;
import org.springframework.stereotype.Service;
import pt.isep.labdsof.transports.domain.SourceImport;

/**
 * The first use case of Transports (spec D5): read the bus source once and record the attempt. Storing the vehicles,
 * the validation rules, freshness, retry and the circuit breaker are US-40 (#84).
 */
@Service
public class SourceImportService {

  private final TransportSource source;
  private final SourceImports imports;
  private final Clock clock;

  public SourceImportService(TransportSource source, SourceImports imports, Clock clock) {
    this.source = source;
    this.imports = imports;
    this.clock = clock;
  }

  public SourceImport runOnce() {
    var startedAt = clock.instant();
    var reading = source.read();
    var result = new SourceImport(startedAt, clock.instant(), reading.outcome(), reading.vehicleCount(),
        reading.error());
    imports.record(result);
    return result;
  }
}
