package pt.isep.labdsof.transports.application;

import java.time.Instant;
import java.util.Optional;
import pt.isep.labdsof.transports.domain.SourceImport;

/** Port: the record of every read of the bus source (spec #193 D5, US-40 D8). */
public interface SourceImports {

  void record(SourceImport sourceImport);

  /** When the last {@code Succeeded} import ended: the time of the data in use (US-40 D8, D10); empty before any. */
  Optional<Instant> lastSucceededAt();
}
