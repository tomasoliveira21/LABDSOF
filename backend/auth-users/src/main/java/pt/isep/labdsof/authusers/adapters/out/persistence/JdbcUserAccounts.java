package pt.isep.labdsof.authusers.adapters.out.persistence;

import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import pt.isep.labdsof.authusers.application.UserAccounts;
import pt.isep.labdsof.authusers.domain.AccountStatus;
import pt.isep.labdsof.authusers.domain.UserAccount;

@Repository
class JdbcUserAccounts implements UserAccounts {

  private final JdbcClient jdbc;

  JdbcUserAccounts(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<UserAccount> findById(UUID userId) {
    return jdbc.sql("""
        SELECT user_id, email, display_name, status, version, created_at, updated_at
        FROM user_account WHERE user_id = :userId
        """)
        .param("userId", userId)
        .query((rs, row) -> new UserAccount(rs.getObject("user_id", UUID.class), rs.getString("email"),
            rs.getString("display_name"), AccountStatus.valueOf(rs.getString("status")), rs.getLong("version"),
            rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()))
        .optional();
  }

  @Override
  public boolean insertIfAbsent(UserAccount account) {
    // ON CONFLICT waits for a concurrent insert of the same sub to commit, then does nothing (D5)
    return jdbc.sql("""
        INSERT INTO user_account (user_id, email, display_name, status, version, created_at, updated_at)
        VALUES (:userId, :email, :displayName, :status, :version, :createdAt, :updatedAt)
        ON CONFLICT (user_id) DO NOTHING
        """)
        .param("userId", account.userId())
        .param("email", account.email())
        .param("displayName", account.displayName())
        .param("status", account.status().name())
        .param("version", account.version())
        .param("createdAt", Timestamp.from(account.createdAt()))
        .param("updatedAt", Timestamp.from(account.updatedAt()))
        .update() == 1;
  }

  @Override
  public void updateEmail(UserAccount account) {
    jdbc.sql("UPDATE user_account SET email = :email, updated_at = :updatedAt WHERE user_id = :userId")
        .param("email", account.email())
        .param("updatedAt", Timestamp.from(account.updatedAt()))
        .param("userId", account.userId())
        .update();
  }
}
