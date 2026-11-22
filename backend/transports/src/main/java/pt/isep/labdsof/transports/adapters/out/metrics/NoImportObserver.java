package pt.isep.labdsof.transports.adapters.out.metrics;

import org.springframework.stereotype.Component;
import pt.isep.labdsof.transports.application.ImportObserver;
import pt.isep.labdsof.transports.application.ImportRun;

/** Until the metrics of US-40 D10 exist: observes nothing. */
@Component
class NoImportObserver implements ImportObserver {

  @Override
  public void observed(ImportRun run) {
    // nothing to measure yet
  }
}
