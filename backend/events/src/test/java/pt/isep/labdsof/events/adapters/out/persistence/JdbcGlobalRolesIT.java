package pt.isep.labdsof.events.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pt.isep.labdsof.events.IntegrationTest;
import pt.isep.labdsof.events.application.GlobalRoles;
import pt.isep.labdsof.events.domain.GlobalRole;
import pt.isep.labdsof.events.domain.GlobalRoleGrant;

/** The global role copy through its port, on the real database (US-06 D1, D2). */
class JdbcGlobalRolesIT extends IntegrationTest {

  @Autowired GlobalRoles roles;

  private final UUID user = UUID.randomUUID();

  private GlobalRoleGrant grant(boolean granted, long version) {
    return new GlobalRoleGrant(user, GlobalRole.FarolAdmin, granted, version);
  }

  @Test
  @DisplayName("US-06 AC-2 a grant makes the user a FarolAdmin and a revoke takes it away, both by version")
  void aGrantAndARevokeAreAppliedByVersion() {
    assertThat(roles.isFarolAdmin(user)).isFalse();

    assertThat(roles.upsertIfNewer(grant(true, 1))).isTrue();
    assertThat(roles.isFarolAdmin(user)).isTrue();

    assertThat(roles.upsertIfNewer(grant(false, 2))).isTrue();
    assertThat(roles.isFarolAdmin(user)).isFalse();
  }

  @Test
  @DisplayName("US-06 AC-2 an older or equal version is rejected, so a late grant cannot undo a revoke")
  void anOlderOrEqualVersionIsRejected() {
    roles.upsertIfNewer(grant(true, 5));
    roles.upsertIfNewer(grant(false, 6));

    // The revoke keeps the row (D2), which is the only reason these two can be compared at all
    assertThat(roles.upsertIfNewer(grant(true, 5))).isFalse();
    assertThat(roles.upsertIfNewer(grant(true, 6))).isFalse();
    assertThat(roles.isFarolAdmin(user)).isFalse();

    assertThat(roles.upsertIfNewer(grant(true, 7))).isTrue();
    assertThat(roles.isFarolAdmin(user)).isTrue();
  }

  @Test
  @DisplayName("US-06 AC-2 one user's role says nothing about another's")
  void oneUsersRoleSaysNothingAboutAnother() {
    roles.upsertIfNewer(grant(true, 1));

    assertThat(roles.isFarolAdmin(UUID.randomUUID())).isFalse();
    // One row for this user only: the schema is shared by every IT of the module
    assertThat(jdbc.sql("SELECT count(*) FROM global_role_copy WHERE user_id = :u").param("u", user)
        .query(Long.class).single()).isEqualTo(1);
  }

  @Test
  @DisplayName("US-06 AC-2 the database refuses a global role the product does not have, and version 0")
  void theDatabaseRefusesAnUnknownRoleAndVersionZero() {
    assertThat(jdbc.sql("SELECT count(*) FROM global_role_copy WHERE role <> 'FarolAdmin'")
        .query(Long.class).single()).isZero();
    assertThatThrownBy(() -> jdbc.sql(
            "INSERT INTO global_role_copy (user_id, role, granted, version) VALUES (:u, 'Nope', true, 1)")
        .param("u", UUID.randomUUID()).update()).hasMessageContaining("global_role_copy_role");
    assertThatThrownBy(() -> jdbc.sql(
            "INSERT INTO global_role_copy (user_id, role, granted, version) VALUES (:u, 'FarolAdmin', true, 0)")
        .param("u", UUID.randomUUID()).update()).hasMessageContaining("global_role_copy_version");
  }
}
