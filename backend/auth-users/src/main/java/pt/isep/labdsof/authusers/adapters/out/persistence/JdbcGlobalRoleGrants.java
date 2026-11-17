package pt.isep.labdsof.authusers.adapters.out.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import pt.isep.labdsof.authusers.application.GlobalRoleGrants;
import pt.isep.labdsof.authusers.domain.GlobalRole;

@Repository
class JdbcGlobalRoleGrants implements GlobalRoleGrants {

  private final JdbcClient jdbc;

  JdbcGlobalRoleGrants(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void grantBySystem(UUID userId, GlobalRole role, Instant at) {
    jdbc.sql("""
        INSERT INTO global_role_grant (user_id, role, version, granted_at, granted_by)
        VALUES (:userId, :role, 1, :grantedAt, NULL)
        """)
        .param("userId", userId)
        .param("role", role.name())
        .param("grantedAt", Timestamp.from(at))
        .update();
  }

  @Override
  public Set<GlobalRole> rolesOf(UUID userId) {
    return jdbc.sql("SELECT role FROM global_role_grant WHERE user_id = :userId")
        .param("userId", userId)
        .query((rs, row) -> GlobalRole.valueOf(rs.getString("role")))
        .list()
        .stream()
        .collect(Collectors.toCollection(() -> EnumSet.noneOf(GlobalRole.class)));
  }
}
