package pt.isep.labdsof.authusers.adapters.out.persistence;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import pt.isep.labdsof.authusers.application.Tombstones;

@Repository
class JdbcTombstones implements Tombstones {

  private final JdbcClient jdbc;

  JdbcTombstones(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean exists(UUID userId) {
    return jdbc.sql("SELECT EXISTS (SELECT 1 FROM user_tombstone WHERE user_id = :userId)")
        .param("userId", userId)
        .query(Boolean.class)
        .single();
  }
}
