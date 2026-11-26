package pt.isep.labdsof.authusers.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import pt.isep.labdsof.authusers.application.InMemoryPorts.MutableClock;
import pt.isep.labdsof.authusers.domain.AccountStatus;
import pt.isep.labdsof.authusers.domain.BootstrapAdmin;
import pt.isep.labdsof.authusers.domain.GlobalRole;
import pt.isep.labdsof.authusers.domain.UserAccount;

class MeServiceTest {

  private static final UUID USER = UUID.fromString("11111111-2222-3333-4444-555555555555");
  private static final Instant START = Instant.parse("2026-11-17T10:00:00.123456789Z");
  private static final Instant START_MICROS = Instant.parse("2026-11-17T10:00:00.123456Z");

  private final InMemoryPorts ports = new InMemoryPorts();
  private final MutableClock clock = new MutableClock(START);
  private final MeService service = serviceWithBootstrap("daniel@farol.test");

  private MeService serviceWithBootstrap(String setting) {
    return new MeService(ports, ports, ports, ports, clock, BootstrapAdmin.of(setting));
  }

  private static Identity identity(String email, Boolean verified, String name) {
    return new Identity(USER, email, verified, name);
  }

  private UserAccount stored(String email, AccountStatus status) {
    return stored(USER, email, true, status);
  }

  private UserAccount stored(UUID userId, String email, boolean emailVerified, AccountStatus status) {
    var account = new UserAccount(userId, email, emailVerified, "Ana", status, 1, START_MICROS, START_MICROS);
    ports.accounts.put(userId, account);
    return account;
  }

  private void assertNothingWritten(UserAccount accountBefore) {
    assertThat(ports.accounts).isEqualTo(accountBefore == null ? java.util.Map.of() : java.util.Map.of(USER, accountBefore));
    assertThat(ports.outbox).isEmpty();
    assertThat(ports.roles).isEmpty();
    assertThat(ports.emailUpdates).isZero();
    assertThat(ports.releasedEmails).isEmpty();
  }

  @Test
  @DisplayName("191 AC-5 a first call creates the account from the token and writes UserProfileChanged to the outbox")
  void firstCallCreatesAccountAndWritesEvent() {
    var result = service.me(identity("ana@farol.test", true, "Ana Silva"));

    var expected = new UserAccount(USER, "ana@farol.test", true, "Ana Silva", AccountStatus.Active, 1, START_MICROS,
        START_MICROS);
    assertThat(result.account()).isEqualTo(expected);
    assertThat(result.globalRoles()).isEmpty();
    assertThat(ports.accounts).containsExactly(java.util.Map.entry(USER, expected));
    assertThat(ports.outbox).hasSize(1);
    var event = ports.outbox.get(0);
    assertThat(event.type()).isEqualTo("UserProfileChanged");
    assertThat(event.exchange()).isEqualTo("farol.auth-users");
    assertThat(event.routingKey()).isEqualTo("user-profile.changed");
    assertThat(event.actorUserId()).isEqualTo(USER.toString());
    assertThat(event.occurredAt()).isEqualTo(START_MICROS);
    assertThat(event.payload()).isEqualTo(
        java.util.Map.of("userId", USER.toString(), "displayName", "Ana Silva", "version", 1L));
    assertThat(ports.roles).isEmpty();
  }

  @Test
  @DisplayName("191 AC-5 a second call returns the same account and writes no event")
  void secondCallWritesNoEvent() {
    var first = service.me(identity("ana@farol.test", true, "Ana Silva"));
    clock.advance(Duration.ofHours(1));

    var second = service.me(identity("ana@farol.test", true, "Ana Silva"));

    assertThat(second).isEqualTo(first);
    assertThat(ports.insertAttempts).as("only the first call tries to insert").isEqualTo(1);
    assertThat(ports.outbox).hasSize(1);
    assertThat(ports.emailUpdates).isZero();
    assertThat(ports.accounts).hasSize(1);
  }

  @Test
  @DisplayName("191 AC-5 a second call refreshes the e-mail from the token and keeps the display name")
  void secondCallRefreshesEmailAndKeepsName() {
    service.me(identity("ana@farol.test", true, "Ana Silva"));
    clock.advance(Duration.ofHours(1));

    var second = service.me(identity("ana.new@farol.test", true, "Another Name"));

    assertThat(second.account().email()).isEqualTo("ana.new@farol.test");
    assertThat(second.account().displayName()).isEqualTo("Ana Silva");
    assertThat(second.account().version()).isEqualTo(1);
    assertThat(second.account().createdAt()).isEqualTo(START_MICROS);
    assertThat(second.account().updatedAt()).isEqualTo(START_MICROS.plus(Duration.ofHours(1)));
    assertThat(ports.accounts.get(USER)).isEqualTo(second.account());
    assertThat(ports.emailUpdates).isEqualTo(1);
    assertThat(ports.outbox).hasSize(1);
  }

  @Test
  @DisplayName("191 AC-5 a second call with the same e-mail changes nothing")
  void secondCallWithSameEmailChangesNothing() {
    var account = stored("ana@farol.test", AccountStatus.Active);

    var result = service.me(identity("ana@farol.test", true, "A New Name"));

    assertThat(result.account()).isEqualTo(account);
    assertNothingWritten(account);
  }

  @Test
  @DisplayName("191 AC-5 a call that loses the race to create the account returns the winner's account and "
      + "writes no event")
  void losingTheRaceReturnsTheWinnersAccount() {
    var winner = new UserAccount(USER, "ana@farol.test", true, "Winner", AccountStatus.Active, 1,
        START_MICROS.minusSeconds(1), START_MICROS.minusSeconds(1));
    ports.concurrentWinner = winner;

    var result = service.me(identity("ana@farol.test", true, "Loser"));

    assertThat(result.account()).isEqualTo(winner);
    assertThat(ports.accounts).isEqualTo(java.util.Map.of(USER, winner));
    assertThat(ports.outbox).isEmpty();
    assertThat(ports.roles).isEmpty();
    assertThat(ports.emailUpdates).isZero();
  }

  @Test
  @DisplayName("191 AC-5 an insert refused without any account for the subject is an error, not a silent answer")
  void anInsertRefusedWithoutAnAccountIsAnError() {
    // Only an e-mail conflict can do this, and an e-mail alone must never select an account (spec D5)
    ports.insertConflictsWithoutAnAccount = true;

    assertThatThrownBy(() -> service.me(identity("ana@farol.test", true, "Ana")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("conflicted without a matching subject");
    assertThat(ports.accounts).isEmpty();
    assertThat(ports.outbox).isEmpty();
  }

  @ParameterizedTest(name = "e-mail [{0}]")
  @NullAndEmptySource
  @ValueSource(strings = "  ")
  @DisplayName("191 AC-5 a token without e-mail is refused and creates nothing")
  void tokenWithoutEmailIsRefused(String email) {
    assertThatThrownBy(() -> service.me(identity(email, true, "Ana")))
        .isInstanceOf(MissingEmailException.class);

    assertNothingWritten(null);
  }

  @Test
  @DisplayName("191 AC-6 the first call with the verified bootstrap e-mail grants FarolAdmin and writes "
      + "GlobalRoleChanged")
  void bootstrapEmailGetsFarolAdmin() {
    var result = service.me(identity("daniel@farol.test", true, "Daniel"));

    assertThat(result.globalRoles()).containsExactly(GlobalRole.FarolAdmin);
    assertThat(ports.roles).containsOnlyKeys(USER);
    assertThat(ports.roles.get(USER)).containsExactly(GlobalRole.FarolAdmin);
    assertThat(ports.outbox).hasSize(2);
    assertThat(ports.outbox.get(0).type()).isEqualTo("UserProfileChanged");
    var granted = ports.outbox.get(1);
    assertThat(granted.type()).isEqualTo("GlobalRoleChanged");
    assertThat(granted.exchange()).isEqualTo("farol.auth-users");
    assertThat(granted.routingKey()).isEqualTo("global-role.changed");
    assertThat(granted.actorUserId()).isEmpty();
    assertThat(granted.occurredAt()).isEqualTo(START_MICROS);
    assertThat(granted.payload()).isEqualTo(java.util.Map.of("userId", USER.toString(), "role", "FarolAdmin",
        "granted", true, "version", 1L));
    assertThat(ports.outbox.get(0).messageId()).isNotEqualTo(granted.messageId());
  }

  @Test
  @DisplayName("191 AC-6 the bootstrap e-mail in another case or with spaces, verified, grants FarolAdmin")
  void bootstrapEmailMatchesIgnoringCaseAndSpaces() {
    var result = serviceWithBootstrap("  Daniel@Farol.TEST ").me(identity(" DANIEL@farol.test ", true, "Daniel"));

    assertThat(result.globalRoles()).containsExactly(GlobalRole.FarolAdmin);
    assertThat(ports.outbox).extracting(OutgoingEvent::type)
        .containsExactly("UserProfileChanged", "GlobalRoleChanged");
  }

  @ParameterizedTest(name = "email_verified {0}")
  @NullSource
  @ValueSource(booleans = false)
  @DisplayName("191 AC-6 an unverified bootstrap e-mail, or one without email_verified, gets no role and no "
      + "GlobalRoleChanged")
  void unverifiedBootstrapEmailGetsNoRole(Boolean verified) {
    var result = service.me(identity("daniel@farol.test", verified, "Daniel"));

    assertThat(result.globalRoles()).isEmpty();
    assertThat(ports.roles).isEmpty();
    assertThat(ports.outbox).extracting(OutgoingEvent::type).containsExactly("UserProfileChanged");
  }

  @Test
  @DisplayName("191 AC-6 an empty bootstrap setting grants nobody")
  void emptyBootstrapSettingGrantsNobody() {
    var result = serviceWithBootstrap("").me(identity("daniel@farol.test", true, "Daniel"));

    assertThat(result.globalRoles()).isEmpty();
    assertThat(ports.roles).isEmpty();
    assertThat(ports.outbox).extracting(OutgoingEvent::type).containsExactly("UserProfileChanged");
  }

  @Test
  @DisplayName("191 review the bootstrap account that verified its e-mail after its first call gets FarolAdmin on "
      + "the next one, or the deployment would have no admin")
  void bootstrapVerifiedAfterTheFirstCallGetsTheRoleLater() {
    var first = service.me(identity("daniel@farol.test", false, "Daniel"));
    assertThat(first.globalRoles()).isEmpty();
    assertThat(ports.outbox).extracting(OutgoingEvent::type).containsExactly("UserProfileChanged");
    clock.advance(Duration.ofHours(2));

    var second = service.me(identity("daniel@farol.test", true, "Daniel"));

    assertThat(second.globalRoles()).containsExactly(GlobalRole.FarolAdmin);
    assertThat(ports.roles.get(USER)).containsExactly(GlobalRole.FarolAdmin);
    assertThat(ports.outbox).extracting(OutgoingEvent::type)
        .containsExactly("UserProfileChanged", "GlobalRoleChanged");
    var granted = ports.outbox.get(1);
    assertThat(granted.actorUserId()).isEmpty();
    assertThat(granted.occurredAt()).isEqualTo(START_MICROS.plus(Duration.ofHours(2)));
    assertThat(granted.payload()).isEqualTo(java.util.Map.of("userId", USER.toString(), "role", "FarolAdmin",
        "granted", true, "version", 1L));
  }

  @Test
  @DisplayName("191 review an account whose unverified e-mail matches the bootstrap e-mail gets no role")
  void unverifiedBootstrapEmailOnALaterCallGetsNoRole() {
    stored(USER, "daniel@farol.test", false, AccountStatus.Active);

    var result = service.me(identity("daniel@farol.test", false, "Daniel"));

    assertThat(result.globalRoles()).isEmpty();
    assertThat(ports.roles).isEmpty();
    assertThat(ports.outbox).isEmpty();
  }

  @Test
  @DisplayName("191 review a verified e-mail that another account already holds as verified takes it over, so the "
      + "US-06 lookup can never find two accounts")
  void aVerifiedEmailIsTakenFromTheStaleAccount() {
    var other = UUID.fromString("99999999-8888-7777-6666-555555555555");
    stored(other, "ana@farol.test", true, AccountStatus.Active);

    service.me(identity("ana@farol.test", true, "Ana"));

    assertThat(ports.releasedEmails).containsExactly("ana@farol.test");
    assertThat(ports.accounts.get(other).emailVerified()).as("the stale copy gives the address up").isFalse();
    assertThat(ports.accounts.get(other).email()).isEqualTo("ana@farol.test");
    assertThat(ports.accounts.get(USER).emailVerified()).isTrue();
  }

  @Test
  @DisplayName("191 review an unverified token never replaces the stored e-mail: it could take over another account")
  void anUnverifiedTokenDoesNotRefreshTheEmail() {
    var account = stored("ana@farol.test", AccountStatus.Active);

    var result = service.me(identity("victim@farol.test", false, "Ana"));

    assertThat(result.account()).isEqualTo(account);
    assertThat(result.account().email()).isEqualTo("ana@farol.test");
    assertThat(ports.accounts.get(USER)).isEqualTo(account);
    assertThat(ports.emailUpdates).isZero();
    assertThat(ports.releasedEmails).isEmpty();
  }

  @ParameterizedTest(name = "email_verified {0}")
  @NullSource
  @ValueSource(booleans = false)
  @DisplayName("191 review an unverified e-mail is stored as unverified on the first call, and claims nothing")
  void anUnverifiedFirstCallStoresAnUnverifiedEmail(Boolean verified) {
    var result = service.me(identity("ana@farol.test", verified, "Ana"));

    assertThat(result.account().emailVerified()).isFalse();
    assertThat(ports.accounts.get(USER).emailVerified()).isFalse();
    assertThat(ports.releasedEmails).as("an unverified e-mail never takes an address from another account").isEmpty();
  }

  @Test
  @DisplayName("191 review a token whose e-mail is verified later sets the stored flag without a new event")
  void aVerifiedTokenLaterMarksTheStoredEmailVerified() {
    service.me(identity("ana@farol.test", false, "Ana"));
    clock.advance(Duration.ofHours(1));

    var second = service.me(identity("ana@farol.test", true, "Ana"));

    assertThat(second.account().emailVerified()).isTrue();
    assertThat(second.account().email()).isEqualTo("ana@farol.test");
    assertThat(ports.emailUpdates).isEqualTo(1);
    assertThat(ports.outbox).extracting(OutgoingEvent::type).containsExactly("UserProfileChanged");
  }

  @ParameterizedTest(name = "e-mail [{0}]")
  @ValueSource(strings = {"\0", "an\0a@farol.test", "ana@farol.test\0"})
  @DisplayName("191 review an e-mail with a NUL is refused with 401: no PostgreSQL text column accepts it")
  void anEmailWithNulIsRefused(String email) {
    assertThatThrownBy(() -> service.me(identity(email, true, "Ana")))
        .isInstanceOf(MissingEmailException.class);

    assertNothingWritten(null);
  }

  @Test
  @DisplayName("191 review the e-mail is stored stripped, so the same address is one value for the lookup")
  void theEmailIsStoredStripped() {
    var result = service.me(identity("  ana@farol.test\t", true, "Ana"));

    assertThat(result.account().email()).isEqualTo("ana@farol.test");
    assertThat(ports.releasedEmails).containsExactly("ana@farol.test");
  }

  @Test
  @DisplayName("191 AC-6 a FarolAdmin keeps the role on later calls without a new event")
  void farolAdminKeepsTheRoleOnLaterCalls() {
    service.me(identity("daniel@farol.test", true, "Daniel"));
    clock.advance(Duration.ofMinutes(5));

    var second = service.me(identity("daniel@farol.test", true, "Daniel"));

    assertThat(second.globalRoles()).isEqualTo(Set.of(GlobalRole.FarolAdmin));
    assertThat(ports.outbox).hasSize(2);
    assertThat(ports.roles.get(USER)).containsExactly(GlobalRole.FarolAdmin);
  }

  @Test
  @DisplayName("191 AC-7 a deleted account (tombstone) is refused as unauthenticated and nothing is created")
  void tombstoneIsRefusedAsUnauthenticated() {
    ports.tombstones.add(USER);

    assertThatThrownBy(() -> service.me(identity("ana@farol.test", true, "Ana")))
        .isInstanceOf(AccountUnavailableException.class);

    assertNothingWritten(null);
  }

  @Test
  @DisplayName("191 AC-7 an account pending deletion is refused as unauthenticated and nothing changes")
  void deletionPendingIsRefusedAsUnauthenticated() {
    var account = stored("ana@farol.test", AccountStatus.DeletionPending);

    assertThatThrownBy(() -> service.me(identity("changed@farol.test", true, "Ana")))
        .isInstanceOf(AccountUnavailableException.class);

    assertNothingWritten(account);
  }

  @Test
  @DisplayName("191 AC-7 a disabled account is refused as forbidden and nothing changes")
  void disabledIsRefusedAsForbidden() {
    var account = stored("ana@farol.test", AccountStatus.Disabled);

    assertThatThrownBy(() -> service.me(identity("changed@farol.test", true, "Ana")))
        .isInstanceOf(AccountDisabledException.class);

    assertNothingWritten(account);
  }
}
