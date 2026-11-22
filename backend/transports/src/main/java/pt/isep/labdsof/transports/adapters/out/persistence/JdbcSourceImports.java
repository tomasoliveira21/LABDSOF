package pt.isep.labdsof.transports.adapters.out.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import pt.isep.labdsof.transports.application.SourceImports;
import pt.isep.labdsof.transports.domain.SourceImport;

/** One row of source_import per read of the bus source (spec D4, D5). */
@Repository
class JdbcSourceImports implements SourceImports {

  private final JdbcClient jdbc;

  JdbcSourceImports(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void record(SourceImport i) {
    jdbc.sql("""
        INSERT INTO source_import (started_at, finished_at, outcome, vehicle_count, error)
        VALUES (:startedAt, :finishedAt, :outcome, :vehicleCount, :error)
        """)
        .param("startedAt", Timestamp.from(i.startedAt()))
        .param("finishedAt", Timestamp.from(i.finishedAt()))
        .param("outcome", i.outcome().name())
        .param("vehicleCount", i.vehicleCount())
        .param("error", i.error())
        .update();
  }

  @Override
  public Optional<Instant> lastSucceededAt() {
    return jdbc.sql("""
        SELECT finished_at FROM source_import WHERE outcome = 'Succeeded' ORDER BY finished_at DESC LIMIT 1
        """).query(Timestamp.class).optional().map(Timestamp::toInstant);
  }
}
