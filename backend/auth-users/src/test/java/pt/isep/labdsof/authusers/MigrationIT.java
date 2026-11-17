package pt.isep.labdsof.authusers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class MigrationIT extends IntegrationTest {

  private static final String ACCOUNT_SQL = """
      INSERT INTO user_account (user_id, email, display_name, status, version, created_at, updated_at)
      VALUES (:id, 'a@farol.test', :name, :status, 1, now(), now())
      """;

  @Test
  @DisplayName("191 AC-1 Flyway creates the schema of D3 in the auth_users database")
  void flywayCreatesTheSchema() {
    assertThat(jdbc.sql("SELECT current_database()").query(String.class).single()).isEqualTo("auth_users");
    assertThat(jdbc.sql("SELECT success FROM flyway_schema_history WHERE version = '1'")
        .query(Boolean.class).single()).isTrue();
    assertThat(jdbc.sql("""
        SELECT table_name FROM information_schema.tables
        WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
        """).query(String.class).list())
        .containsExactlyInAnyOrder("user_account", "user_tombstone", "global_role_grant", "event_membership",
            "team_assignment", "outbox", "flyway_schema_history");
    assertThat(columnType("user_account", "user_id")).isEqualTo("uuid");
  }

  @Test
  @DisplayName("191 AC-1 the database refuses an account status outside Active, Disabled and DeletionPending")
  void refusesAccountStatusOutsideTheThree() {
    for (var status : List.of("Active", "Disabled", "DeletionPending")) {
      insertAccount(UUID.randomUUID(), "Ana", status);
    }
    for (var status : List.of("Deleted", "active", "")) {
      assertThatThrownBy(() -> insertAccount(UUID.randomUUID(), "Ana", status))
          .isInstanceOf(DataIntegrityViolationException.class);
    }
  }

  @Test
  @DisplayName("191 AC-1 the database refuses a global role other than FarolAdmin and an event role other than "
      + "EventAdmin and RegularUser")
  void refusesRolesOutsideTheContract() {
    var user = UUID.randomUUID();
    insertAccount(user, "Ana", "Active");
    assertThatThrownBy(() -> grant(user, "EventAdmin")).isInstanceOf(DataIntegrityViolationException.class);
    grant(user, "FarolAdmin");

    var event = UUID.randomUUID();
    assertThatThrownBy(() -> membership(event, user, "FarolAdmin"))
        .isInstanceOf(DataIntegrityViolationException.class);
    membership(event, user, "EventAdmin");
    membership(UUID.randomUUID(), user, "RegularUser");
    assertThat(columns("event_membership")).doesNotContain("status");
  }

  @Test
  @DisplayName("191 AC-1 the database refuses an empty display name or one longer than 100 characters")
  void refusesDisplayNameOutsideOneToHundred() {
    insertAccount(UUID.randomUUID(), "a", "Active");
    insertAccount(UUID.randomUUID(), "a".repeat(100), "Active");
    assertThatThrownBy(() -> insertAccount(UUID.randomUUID(), "", "Active"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> insertAccount(UUID.randomUUID(), "a".repeat(101), "Active"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("191 AC-1 a team assignment needs the membership it belongs to")
  void teamAssignmentNeedsItsMembership() {
    var user = UUID.randomUUID();
    var event = UUID.randomUUID();
    insertAccount(user, "Ana", "Active");
    assertThatThrownBy(() -> assign(event, user)).isInstanceOf(DataIntegrityViolationException.class);
    membership(event, user, "RegularUser");
    assign(event, user);
  }

  @Test
  @DisplayName("191 AC-1 the outbox has the columns of #214 D4 plus correlation_id and a partial index on "
      + "unpublished rows")
  void outboxHasItsColumnsAndPartialIndex() {
    assertThat(columns("outbox")).containsExactlyInAnyOrder("id", "message_id", "exchange", "routing_key",
        "type", "body", "correlation_id", "created_at", "attempts", "next_attempt_at", "published_at",
        "last_error");
    assertThat(jdbc.sql("SELECT indexdef FROM pg_indexes WHERE indexname = 'outbox_due'")
        .query(String.class).single()).contains("WHERE (published_at IS NULL)");
  }

  private void insertAccount(UUID id, String name, String status) {
    jdbc.sql(ACCOUNT_SQL).param("id", id).param("name", name).param("status", status).update();
  }

  private void grant(UUID user, String role) {
    jdbc.sql("INSERT INTO global_role_grant (user_id, role, version, granted_at) VALUES (:u, :r, 1, now())")
        .param("u", user).param("r", role).update();
  }

  private void membership(UUID event, UUID user, String role) {
    jdbc.sql("""
        INSERT INTO event_membership (event_id, user_id, role, version, created_at, updated_at)
        VALUES (:e, :u, :r, 1, now(), now())
        """).param("e", event).param("u", user).param("r", role).update();
  }

  private void assign(UUID event, UUID user) {
    jdbc.sql("INSERT INTO team_assignment (event_id, user_id, team_id, assigned_at) VALUES (:e, :u, :t, now())")
        .param("e", event).param("u", user).param("t", UUID.randomUUID()).update();
  }

  private List<String> columns(String table) {
    return jdbc.sql("SELECT column_name FROM information_schema.columns WHERE table_name = :t")
        .param("t", table).query(String.class).list();
  }

  private String columnType(String table, String column) {
    return jdbc.sql("SELECT data_type FROM information_schema.columns WHERE table_name = :t AND column_name = :c")
        .param("t", table).param("c", column).query(String.class).single();
  }
}
