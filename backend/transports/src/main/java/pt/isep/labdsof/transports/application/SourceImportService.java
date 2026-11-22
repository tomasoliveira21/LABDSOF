package pt.isep.labdsof.transports.application;

import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
import pt.isep.labdsof.transports.domain.BusImport;
import pt.isep.labdsof.transports.domain.ImportOutcome;
import pt.isep.labdsof.transports.domain.SourceImport;

/**
 * One run of the import (spec US-40): read the source, check the buses, and either replace the buses in use and
 * record {@code Succeeded} in one transaction, or record why not and leave the buses as they are. The time the answer
 * arrived is the time of the import (D7).
 */
@Service
public class SourceImportService {

  private final TransportSource source;
  private final SourceImports imports;
  private final BusData buses;
  private final ImportObserver observer;
  private final TransactionOperations transactions;
  private final Clock clock;
  private final Duration freshness;

  public SourceImportService(TransportSource source, SourceImports imports, BusData buses, ImportObserver observer,
      TransactionOperations transactions, Clock clock,
      @Value("${farol.transport.source.freshness}") Duration freshness) {
    this.source = source;
    this.imports = imports;
    this.buses = buses;
    this.observer = observer;
    this.transactions = transactions;
    this.clock = clock;
    this.freshness = freshness;
  }

  public ImportRun runOnce() {
    var startedAt = clock.instant();
    var reading = source.read();
    var arrivedAt = clock.instant();
    var run = switch (reading) {
      case SourceReading.Unavailable unavailable -> record(
          new SourceImport(startedAt, arrivedAt, ImportOutcome.Unavailable, null, unavailable.reason()), reading);
      case SourceReading.Invalid invalid -> record(
          new SourceImport(startedAt, arrivedAt, ImportOutcome.Invalid, null, invalid.reason()), reading);
      case SourceReading.Answered answered -> switch (BusImport.of(answered.buses(), arrivedAt, freshness)) {
        case BusImport.Rejected rejected -> record(
            new SourceImport(startedAt, arrivedAt, ImportOutcome.Invalid, null, rejected.reason()), reading);
        case BusImport.Accepted accepted -> {
          var succeeded = new SourceImport(startedAt, arrivedAt, ImportOutcome.Succeeded, accepted.buses().size(),
              null);
          transactions.executeWithoutResult(status -> {
            buses.replace(accepted.buses(), arrivedAt);
            imports.record(succeeded);
          });
          yield new ImportRun(succeeded, accepted.late(), reading.attempts());
        }
      };
    };
    observer.observed(run);
    return run;
  }

  private ImportRun record(SourceImport sourceImport, SourceReading reading) {
    imports.record(sourceImport);
    return new ImportRun(sourceImport, 0, reading.attempts());
  }
}
