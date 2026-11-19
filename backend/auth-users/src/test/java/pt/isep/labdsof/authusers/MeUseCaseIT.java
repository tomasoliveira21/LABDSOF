package pt.isep.labdsof.authusers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import pt.isep.labdsof.authusers.application.AccountDisabledException;
import pt.isep.labdsof.authusers.application.AccountUnavailableException;
import pt.isep.labdsof.authusers.application.Identity;
import pt.isep.labdsof.authusers.application.MeService;
import pt.isep.labdsof.authusers.application.MissingEmailException;
import pt.isep.labdsof.authusers.domain.GlobalRole;

/** The {@code GET /me} use case on the real schema; every test uses its own user id. */
class MeUseCaseIT extends IntegrationTest {

  @Autowired MeService service;
  @Autowired TransactionTemplate tx;

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  private static Identity identity(UUID id, String email) {
    return new Identity(id, email, true, "Ana Silva");
  }

  private long outboxRows(UUID user) {
    return jdbc.sql("SELECT count(*) FROM outbox WHERE body->'payload'->>'userId' = :id")
        .param("id", user.toString()).query(Long.class).single();
  }

  private long outboxRows(UUID user, String type) {
    return jdbc.sql("SELECT count(*) FROM outbox WHERE body->'payload'->>'userId' = :id AND type = :type")
        .param("id", user.toString()).param("type", type).query(Long.class).single();
  }

  private long accountRows(UUID user) {
    return jdbc.sql("SELECT count(*) FROM user_account WHERE user_id = :id")
        .param("id", user).query(Long.class).single();
  }

  /** email_verified false, so two fixtures may share an address: these tests are about the status, not the e-mail. */
  private void insertAccount(UUID user, String email, String status) {
    jdbc.sql("""
        INSERT INTO user_account (user_id, email, email_verified, display_name, status, version, created_at,
            updated_at)
        VALUES (:id, :email, false, 'Ana', :status, 1, '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')
        """).param("id", user).param("email", email).param("status", status).update();
  }

  private String emailOf(UUID user) {
    return jdbc.sql("SELECT email FROM user_account WHERE user_id = :id")
        .param("id", user).query(String.class).single();
  }

  @Test
  @DisplayName("191 AC-5 a first call stores the account and its UserProfileChanged in the outbox")
  void firstCallStoresAccountAndEvent() {
    var user = UUID.randomUUID();

    var result = service.me(identity(user, "ana@farol.test"));

    assertThat(result.globalRoles()).isEmpty();
    var account = jdbc.sql("SELECT email, display_name, status, version FROM user_account WHERE user_id = :id")
        .param("id", user).query().singleRow();
    assertThat(account).containsEntry("email", "ana@farol.test").containsEntry("display_name", "Ana Silva")
        .containsEntry("status", "Active").containsEntry("version", 1L);
    var row = jdbc.sql("""
        SELECT exchange, routing_key, type, body->>'producer' AS producer, body->>'actorUserId' AS actor,
               jsonb_typeof(body->'eventId') AS event_id_type, (body->>'schemaVersion')::int AS schema_version,
               body->'payload'->>'displayName' AS display_name, (body->'payload'->>'version')::bigint AS version
        FROM outbox WHERE body->'payload'->>'userId' = :id
        """).param("id", user.toString()).query().singleRow();
    assertThat(row).containsEntry("exchange", "farol.auth-users")
        .containsEntry("routing_key", "user-profile.changed")
        .containsEntry("type", "UserProfileChanged")
        .containsEntry("producer", "auth-users")
        .containsEntry("actor", user.toString())
        .containsEntry("event_id_type", "null")
        .containsEntry("schema_version", 1)
        .containsEntry("display_name", "Ana Silva")
        .containsEntry("version", 1L);
  }

  @Test
  @DisplayName("191 AC-5 the account and its outbox row commit or roll back together")
  void accountAndOutboxRowShareTheTransaction() {
    var user = UUID.randomUUID();

    tx.executeWithoutResult(status -> {
      service.me(identity(user, "ana@farol.test"));
      status.setRollbackOnly();
    });

    assertThat(accountRows(user)).isZero();
    assertThat(outboxRows(user)).isZero();

    var committed = UUID.randomUUID();
    tx.executeWithoutResult(status -> service.me(identity(committed, "ana@farol.test")));
    assertThat(accountRows(committed)).isEqualTo(1);
    assertThat(outboxRows(committed)).isEqualTo(1);
  }

  @Test
  @DisplayName("191 AC-5 later calls return the same account, refresh the e-mail, keep the name and write no event")
  void laterCallsRefreshEmailAndWriteNoEvent() {
    var user = UUID.randomUUID();
    var first = service.me(identity(user, "ana@farol.test"));

    var second = service.me(new Identity(user, "ana@farol.test", true, "Another Name"));
    var third = service.me(new Identity(user, "ana.new@farol.test", true, "Yet Another"));

    assertThat(second.account()).isEqualTo(first.account());
    assertThat(third.account().userId()).isEqualTo(user);
    assertThat(third.account().email()).isEqualTo("ana.new@farol.test");
    assertThat(third.account().displayName()).isEqualTo("Ana Silva");
    assertThat(third.account().version()).isEqualTo(1);
    assertThat(third.account().createdAt()).isEqualTo(first.account().createdAt());
    assertThat(emailOf(user)).isEqualTo("ana.new@farol.test");
    assertThat(outboxRows(user)).isEqualTo(1);
  }

  private boolean verifiedOf(UUID user) {
    return jdbc.sql("SELECT email_verified FROM user_account WHERE user_id = :id")
        .param("id", user).query(Boolean.class).single();
  }

  @Test
  @DisplayName("191 review an unverified token never replaces the stored e-mail on the real schema")
  void anUnverifiedTokenDoesNotRefreshTheStoredEmail() {
    var user = UUID.randomUUID();
    var email = "rita-" + UUID.randomUUID() + "@farol.test";
    service.me(identity(user, email));

    var result = service.me(new Identity(user, "taken-over-" + UUID.randomUUID() + "@farol.test", false, "Rita"));

    assertThat(result.account().email()).isEqualTo(email);
    assertThat(emailOf(user)).isEqualTo(email);
    assertThat(verifiedOf(user)).isTrue();
  }

  @Test
  @DisplayName("191 review a verified caller takes the address from the stale account, so the unique index of D3 "
      + "still holds")
  void aVerifiedCallerTakesTheAddressOver() {
    var shared = "shared-" + UUID.randomUUID() + "@farol.test";
    var stale = UUID.randomUUID();
    var current = UUID.randomUUID();
    service.me(identity(stale, shared));

    service.me(identity(current, shared));

    assertThat(verifiedOf(stale)).isFalse();
    assertThat(emailOf(stale)).isEqualTo(shared);
    assertThat(verifiedOf(current)).isTrue();
    assertThat(emailOf(current)).isEqualTo(shared);
  }

  @Test
  @DisplayName("191 review an unverified first call stores the e-mail as unverified and claims the address for "
      + "nobody")
  void anUnverifiedFirstCallClaimsNothing() {
    var shared = "unclaimed-" + UUID.randomUUID() + "@farol.test";
    var first = UUID.randomUUID();
    var second = UUID.randomUUID();

    service.me(new Identity(first, shared, false, "Rita"));
    service.me(new Identity(second, shared, false, "Rita"));

    assertThat(verifiedOf(first)).isFalse();
    assertThat(verifiedOf(second)).isFalse();
  }

  @Test
  @DisplayName("191 review a later verified token marks the stored e-mail verified without a new event")
  void aLaterVerifiedTokenMarksTheEmailVerified() {
    var user = UUID.randomUUID();
    var email = "later-" + UUID.randomUUID() + "@farol.test";
    service.me(new Identity(user, email, false, "Rita"));

    service.me(new Identity(user, email, true, "Rita"));

    assertThat(verifiedOf(user)).isTrue();
    assertThat(emailOf(user)).isEqualTo(email);
    assertThat(outboxRows(user)).isEqualTo(1);
  }

  @Test
  @DisplayName("191 AC-5 concurrent first calls create one account and one event")
  void concurrentFirstCallsCreateOneAccountAndOneEvent() throws Exception {
    var user = UUID.randomUUID();
    var inserted = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(2);
    try {
      // A holds its uncommitted insert; B must wait on it, then find the account A committed
      var a = pool.submit(() -> tx.execute(status -> {
        var result = service.me(identity(user, "ana@farol.test"));
        inserted.countDown();
        await(release);
        return result;
      }));
      assertThat(inserted.await(10, TimeUnit.SECONDS)).isTrue();
      var b = pool.submit(() -> service.me(identity(user, "ana@farol.test")));

      awaitBlockedInsert();
      release.countDown();

      var fromA = a.get(10, TimeUnit.SECONDS);
      var fromB = b.get(10, TimeUnit.SECONDS);
      assertThat(fromB.account()).isEqualTo(fromA.account());
    } finally {
      release.countDown();
      pool.shutdownNow();
    }
    assertThat(accountRows(user)).isEqualTo(1);
    assertThat(outboxRows(user, "UserProfileChanged")).isEqualTo(1);
  }

  private static void await(CountDownLatch latch) {
    try {
      assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  /** Polls PostgreSQL until a lock request waits: the second call is blocked on the first's uncommitted row. */
  private void awaitBlockedInsert() throws InterruptedException {
    var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (jdbc.sql("SELECT count(*) FROM pg_locks WHERE NOT granted").query(Long.class).single() == 0) {
      assertThat(System.nanoTime()).as("the second call never waited on the first").isLessThan(deadline);
      Thread.sleep(20); // poll interval of a bounded wait for an observable condition, not a wait for a race
    }
  }

  @ParameterizedTest(name = "e-mail [{0}]")
  @NullSource
  @ValueSource(strings = {"", "  "})
  @DisplayName("191 AC-5 a token without e-mail is refused and nothing is stored")
  void tokenWithoutEmailIsRefused(String email) {
    var user = UUID.randomUUID();

    assertThatThrownBy(() -> service.me(identity(user, email))).isInstanceOf(MissingEmailException.class);

    assertThat(accountRows(user)).isZero();
    assertThat(outboxRows(user)).isZero();
  }

  @Test
  @DisplayName("191 AC-5 the outbox row keeps the correlation id of the request, or none without one")
  void outboxRowKeepsTheCorrelationId() {
    var withId = UUID.randomUUID();
    var without = UUID.randomUUID();

    MDC.put("correlationId", "corr-191-f01-abcdef");
    service.me(identity(withId, "ana@farol.test"));
    MDC.clear();
    service.me(identity(without, "ana@farol.test"));

    assertThat(correlationIds(withId)).containsExactly("corr-191-f01-abcdef");
    assertThat(correlationIds(without)).containsOnlyNulls().hasSize(1);
  }

  private List<String> correlationIds(UUID user) {
    return jdbc.sql("SELECT correlation_id FROM outbox WHERE body->'payload'->>'userId' = :id")
        .param("id", user.toString()).query(String.class).list();
  }

  @Test
  @DisplayName("191 AC-6 the first call with the verified bootstrap e-mail stores FarolAdmin and GlobalRoleChanged")
  void bootstrapEmailStoresFarolAdminAndEvent() {
    var user = UUID.randomUUID();

    var result = service.me(new Identity(user, Infrastructure.BOOTSTRAP_EMAIL.toUpperCase(), true, "Daniel"));

    assertThat(result.globalRoles()).containsExactly(GlobalRole.FarolAdmin);
    var grant = jdbc.sql("SELECT role, version, granted_by FROM global_role_grant WHERE user_id = :id")
        .param("id", user).query().singleRow();
    assertThat(grant).containsEntry("role", "FarolAdmin").containsEntry("version", 1L)
        .containsEntry("granted_by", null);
    var row = jdbc.sql("""
        SELECT exchange, routing_key, body->>'actorUserId' AS actor, body->'payload'->>'role' AS role,
               (body->'payload'->>'granted')::boolean AS granted, (body->'payload'->>'version')::bigint AS version
        FROM outbox WHERE body->'payload'->>'userId' = :id AND type = 'GlobalRoleChanged'
        """).param("id", user.toString()).query().singleRow();
    assertThat(row).containsEntry("exchange", "farol.auth-users")
        .containsEntry("routing_key", "global-role.changed")
        .containsEntry("actor", "")
        .containsEntry("role", "FarolAdmin")
        .containsEntry("granted", true)
        .containsEntry("version", 1L);
    assertThat(outboxRows(user)).isEqualTo(2);

    // Read back on a later call, with no new event
    assertThat(service.me(new Identity(user, Infrastructure.BOOTSTRAP_EMAIL, true, "Daniel")).globalRoles())
        .containsExactly(GlobalRole.FarolAdmin);
    assertThat(outboxRows(user)).isEqualTo(2);
  }

  @ParameterizedTest(name = "email_verified {0}")
  @NullSource
  @ValueSource(booleans = false)
  @DisplayName("191 AC-6 an unverified bootstrap e-mail, or one without email_verified, stores no role and no "
      + "GlobalRoleChanged")
  void unverifiedBootstrapEmailStoresNoRole(Boolean verified) {
    var user = UUID.randomUUID();

    var result = service.me(new Identity(user, Infrastructure.BOOTSTRAP_EMAIL, verified, "Daniel"));

    assertThat(result.globalRoles()).isEmpty();
    assertThat(jdbc.sql("SELECT count(*) FROM global_role_grant WHERE user_id = :id")
        .param("id", user).query(Long.class).single()).isZero();
    assertThat(outboxRows(user, "GlobalRoleChanged")).isZero();
    assertThat(outboxRows(user, "UserProfileChanged")).isEqualTo(1);
  }

  @Test
  @DisplayName("191 review the bootstrap account that verifies its e-mail after its first call gets FarolAdmin and "
      + "GlobalRoleChanged on the next one, once")
  void bootstrapVerifiedAfterTheFirstCallIsGrantedLater() {
    var user = UUID.randomUUID();
    assertThat(service.me(new Identity(user, Infrastructure.BOOTSTRAP_EMAIL, false, "Daniel")).globalRoles())
        .isEmpty();

    var result = service.me(new Identity(user, Infrastructure.BOOTSTRAP_EMAIL, true, "Daniel"));

    assertThat(result.globalRoles()).containsExactly(GlobalRole.FarolAdmin);
    assertThat(jdbc.sql("SELECT role FROM global_role_grant WHERE user_id = :id")
        .param("id", user).query(String.class).list()).containsExactly("FarolAdmin");
    assertThat(outboxRows(user, "GlobalRoleChanged")).isEqualTo(1);

    // A third call grants nothing more
    assertThat(service.me(new Identity(user, Infrastructure.BOOTSTRAP_EMAIL, true, "Daniel")).globalRoles())
        .containsExactly(GlobalRole.FarolAdmin);
    assertThat(outboxRows(user, "GlobalRoleChanged")).isEqualTo(1);
  }

  @Test
  @DisplayName("191 AC-7 a tombstoned user is refused and nothing is stored")
  void tombstonedUserIsRefused() {
    var user = UUID.randomUUID();
    jdbc.sql("INSERT INTO user_tombstone (user_id, deleted_at) VALUES (:id, now())").param("id", user).update();

    assertThatThrownBy(() -> service.me(identity(user, "ana@farol.test")))
        .isInstanceOf(AccountUnavailableException.class);

    assertThat(accountRows(user)).isZero();
    assertThat(outboxRows(user)).isZero();
  }

  @Test
  @DisplayName("191 AC-7 an account pending deletion is refused and nothing changes")
  void deletionPendingIsRefused() {
    var user = UUID.randomUUID();
    insertAccount(user, "old@farol.test", "DeletionPending");

    assertThatThrownBy(() -> service.me(identity(user, "new@farol.test")))
        .isInstanceOf(AccountUnavailableException.class);

    assertUnchanged(user);
  }

  @Test
  @DisplayName("191 AC-7 a disabled account is refused as forbidden and nothing changes")
  void disabledIsRefused() {
    var user = UUID.randomUUID();
    insertAccount(user, "old@farol.test", "Disabled");

    assertThatThrownBy(() -> service.me(identity(user, "new@farol.test")))
        .isInstanceOf(AccountDisabledException.class);

    assertUnchanged(user);
  }

  private void assertUnchanged(UUID user) {
    var row = jdbc.sql("SELECT email, updated_at = '2026-01-01T00:00:00Z' AS untouched FROM user_account "
        + "WHERE user_id = :id").param("id", user).query().singleRow();
    assertThat(row).containsEntry("email", "old@farol.test").containsEntry("untouched", true);
    assertThat(outboxRows(user)).isZero();
  }
}
