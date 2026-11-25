package pt.isep.labdsof.authusers.adapters.out.persistence;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import pt.isep.labdsof.authusers.application.MyMemberships;

@Repository
class JdbcMyMemberships implements MyMemberships {

  private final JdbcClient jdbc;

  JdbcMyMemberships(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<Entry> findForUser(UUID userId) {
    return jdbc.sql("""
        SELECT m.event_id, e.name, e.status, m.role,
               ARRAY(SELECT t.team_id FROM team_assignment t
                     WHERE t.event_id = m.event_id AND t.user_id = m.user_id ORDER BY t.team_id) AS team_ids
        FROM event_membership m
        LEFT JOIN event_copy e ON e.event_id = m.event_id
        WHERE m.user_id = :userId
        ORDER BY lower(e.name), m.event_id
        """)
        .param("userId", userId)
        .query((row, number) -> {
          var teamIds = row.getArray("team_ids");
          try {
            var status = row.getString("status");
            return new Entry(row.getObject("event_id", UUID.class), row.getString("name"),
                status == null ? null : Status.valueOf(status), row.getString("role"),
                Arrays.asList((UUID[]) teamIds.getArray()));
          } finally {
            teamIds.free();
          }
        })
        .list();
  }
}
