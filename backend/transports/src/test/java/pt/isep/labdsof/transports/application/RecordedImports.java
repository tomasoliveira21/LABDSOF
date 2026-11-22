package pt.isep.labdsof.transports.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import pt.isep.labdsof.transports.domain.ImportOutcome;
import pt.isep.labdsof.transports.domain.SourceImport;

/** In-memory {@link SourceImports} with the rules of the SQL adapter; optionally failing on every record. */
public final class RecordedImports implements SourceImports {

  public final List<SourceImport> recorded = new ArrayList<>();
  private final RuntimeException failure;

  public RecordedImports() {
    this(null);
  }

  public RecordedImports(RuntimeException failure) {
    this.failure = failure;
  }

  @Override
  public void record(SourceImport sourceImport) {
    if (failure != null) {
      throw failure;
    }
    recorded.add(sourceImport);
  }

  @Override
  public Optional<Instant> lastSucceededAt() {
    return recorded.stream().filter(i -> i.outcome() == ImportOutcome.Succeeded).map(SourceImport::finishedAt)
        .max(Instant::compareTo);
  }
}
