package pt.isep.labdsof.events.adapters.out.persistence;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import pt.isep.labdsof.events.application.GlobalRoles;
import pt.isep.labdsof.events.domain.GlobalRole;
import pt.isep.labdsof.events.domain.GlobalRoleGrant;

/**
 * The global role copy, upserted by version exactly as the membership copy is (US-06 D2). This table is the only
 * thing that makes a caller the FarolAdmin of {@code POST /events} and {@code GET /events}: the role is never
 * read from the token (ADR-006), so a caller whose {@code GlobalRoleChanged} has not arrived is simply not one.
 */
@Repository
class JdbcGlobalRoles implements GlobalRoles {

  private final JdbcClient jdbc;

  JdbcGlobalRoles(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean isFarolAdmin(UUID userId) {
    return jdbc.sql("""
        SELECT EXISTS (SELECT 1 FROM global_role_copy
                       WHERE user_id = :userId AND role = :role AND granted)
        """)
        .param("userId", userId)
        .param("role", GlobalRole.FarolAdmin.name())
        .query(Boolean.class)
        .single();
  }

  @Override
  public boolean upsertIfNewer(GlobalRoleGrant grant) {
    return jdbc.sql("""
        INSERT INTO global_role_copy (user_id, role, granted, version)
        VALUES (:userId, :role, :granted, :version)
        ON CONFLICT (user_id, role) DO UPDATE
          SET granted = excluded.granted, version = excluded.version
          WHERE global_role_copy.version < excluded.version
        """)
        .param("userId", grant.userId())
        .param("role", grant.role().name())
        .param("granted", grant.granted())
        .param("version", grant.version())
        .update() == 1;
  }
}
