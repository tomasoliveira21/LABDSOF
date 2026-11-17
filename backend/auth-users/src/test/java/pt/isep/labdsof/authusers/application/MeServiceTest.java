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
    var account = new UserAccount(USER, email, "Ana", status, 1, START_MICROS, START_MICROS);
    ports.accounts.put(USER, account);
    return account;
  }

  private void assertNothingWritten(UserAccount accountBefore) {
    assertThat(ports.accounts).isEqualTo(accountBefore == null ? java.util.Map.of() : java.util.Map.of(USER, accountBefore));
    assertThat(ports.outbox).isEmpty();
    assertThat(ports.roles).isEmpty();
    assertThat(ports.emailUpdates).isZero();
  }

  @Test
  @DisplayName("191 AC-5 a first call creates the account from the token and writes UserProfileChanged to the outbox")
  void firstCallCreatesAccountAndWritesEvent() {
    var result = service.me(identity("ana@farol.test", true, "Ana Silva"));

    var expected = new UserAccount(USER, "ana@farol.test", "Ana Silva", AccountStatus.Active, 1, START_MICROS,
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
    var winner = new UserAccount(USER, "ana@farol.test", "Winner", AccountStatus.Active, 1,
        START_MICROS.minusSeconds(1), START_MICROS.minusSeconds(1));
    ports.concurrentWinner = winner;

    var result = service.me(identity("ana@farol.test", true, "Loser"));

    assertThat(result.account()).isEqualTo(winner);
    assertThat(ports.accounts).isEqualTo(java.util.Map.of(USER, winner));
    assertThat(ports.outbox).isEmpty();
    assertThat(ports.roles).isEmpty();
    assertThat(ports.emailUpdates).isZero();
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
  @DisplayName("191 AC-6 an existing account whose e-mail later matches the bootstrap e-mail gets no role")
  void existingAccountLaterMatchingBootstrapGetsNoRole() {
    stored("old@farol.test", AccountStatus.Active);

    var result = service.me(identity("daniel@farol.test", true, "Daniel"));

    assertThat(result.account().email()).isEqualTo("daniel@farol.test");
    assertThat(result.globalRoles()).isEmpty();
    assertThat(ports.roles).isEmpty();
    assertThat(ports.outbox).isEmpty();
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
