package pt.isep.labdsof.transports.adapters.in.scheduling;

import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.Level;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pt.isep.labdsof.transports.Correlation;
import pt.isep.labdsof.transports.application.SourceImportService;
import pt.isep.labdsof.transports.domain.ImportOutcome;

/**
 * Reads the bus source every {@code farol.transport.source.interval} (30 s, ADR-008), with a fixed delay so two runs
 * never overlap (architecture 9.1). Never inside a user's request (API outline 5). Each run is a system action with
 * its own correlation id (spec D9); a run that throws is logged and the next one still happens.
 */
@Component
@ConditionalOnProperty(prefix = "farol.transport.source", name = "enabled", havingValue = "true",
    matchIfMissing = true)
class SourceImportJob {

  private static final Logger log = LoggerFactory.getLogger(SourceImportJob.class);

  private final SourceImportService imports;

  SourceImportJob(SourceImportService imports) {
    this.imports = imports;
  }

  @Scheduled(fixedDelayString = "${farol.transport.source.interval}")
  void run() {
    MDC.put(Correlation.MDC_KEY, UUID.randomUUID().toString());
    try {
      var result = imports.runOnce();
      log.atLevel(result.outcome() == ImportOutcome.Succeeded ? Level.INFO : Level.WARN)
          .addKeyValue("outcome", result.outcome().name())
          .addKeyValue("vehicleCount", result.vehicleCount())
          .addKeyValue("durationMs", Duration.between(result.startedAt(), result.finishedAt()).toMillis())
          .addKeyValue("error", result.error())
          .log("Transport source read");
    } catch (RuntimeException e) {
      log.error("Transport source import failed", e);
    } finally {
      MDC.remove(Correlation.MDC_KEY);
    }
  }
}
