package pt.isep.labdsof.authusers.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import pt.isep.labdsof.authusers.application.MemberAudit.Refusal;
import pt.isep.labdsof.authusers.application.MyMemberships.Status;
import pt.isep.labdsof.authusers.domain.AccountStatus;
import pt.isep.labdsof.authusers.domain.EventMembership;
import pt.isep.labdsof.authusers.domain.EventRole;
import pt.isep.labdsof.authusers.domain.GlobalRole;
import pt.isep.labdsof.authusers.domain.UserAccount;

/** US-06: naming a user EventAdmin of an event, with the ports faked (spec §4 F5). */
class NameEventAdminServiceTest {

  private static final Instant NOW = Instant.parse("2027-04-30T10:15:30.123456Z");

  private final InMemoryPorts ports = new InMemoryPorts();
  private final NameEventAdminService service = new NameEventAdminService(ports, ports, ports, ports, ports,
      ports, ports, Clock.fixed(NOW, ZoneOffset.UTC));
  private final UUID admin = UUID.randomUUID();
  private final UUID sofia = UUID.randomUUID();
  private final UUID event = UUID.randomUUID();

  private void farolAdmin() {
    ports.grantBySystem(admin, GlobalRole.FarolAdmin, NOW);
  }

  private void account(UUID userId, AccountStatus status) {
    var provisioned = UserAccount.provision(userId, userId + "@farol.test", true, "Sofia Organiser", NOW);
    ports.accounts.put(userId, new UserAccount(userId, provisioned.email(), true, provisioned.displayName(),
        status, 1, NOW, NOW));
  }

  /** The event copy the create of US-06 publishes and this service reads (D5). */
  private void eventCopy() {
    ports.eventCopy(event, "Queima das Fitas 2027", Status.Draft);
  }

  @Test
  @DisplayName("US-06 AC-1 the FarolAdmin names the first EventAdmin, and MembershipChanged carries it")
  void theFarolAdminNamesTheFirstEventAdmin() {
    farolAdmin();
    account(sofia, AccountStatus.Active);
    eventCopy();

    var named = service.name(admin, event, sofia);

    assertThat(named.changed()).isTrue();
    assertThat(named.displayName()).isEqualTo("Sofia Organiser");
    assertThat(named.membership())
        .isEqualTo(new EventMembership(event, sofia, EventRole.EventAdmin, List.of(), 1));
    // The first EventAdmin goes in through the key, not the lock: there is no row to lock yet
    assertThat(ports.insertedMemberships).singleElement()
        .satisfies(write -> assertThat(write.actorUserId()).isEqualTo(admin));
    assertThat(ports.roleWrites).isEmpty();
    assertThat(ports.outbox).singleElement()
        .satisfies(sent -> assertThat(sent.type()).isEqualTo("MembershipChanged"));
    assertThat(ports.namings).singleElement()
        .satisfies(naming -> assertThat(naming.eventId()).isEqualTo(event));
  }

  @Test
  @DisplayName("US-06 AC-1 naming the same user again changes nothing, publishes nothing, and still answers")
  void namingTheSameUserAgainChangesNothing() {
    farolAdmin();
    account(sofia, AccountStatus.Active);
    eventCopy();
    service.name(admin, event, sofia);

    var again = service.name(admin, event, sofia);

    assertThat(again.changed()).isFalse();
    assertThat(again.membership().version()).isEqualTo(1); // the version does not move (D6)
    assertThat(ports.insertedMemberships).hasSize(1);
    assertThat(ports.roleWrites).isEmpty();
    assertThat(ports.outbox).hasSize(1);
    assertThat(ports.namings).hasSize(1);
  }

  @Test
  @DisplayName("US-06 AC-1 the loser of two name calls at once writes nothing and answers the winner's row")
  void theLoserOfTheRaceAnswersTheWinnersRow() {
    farolAdmin();
    account(sofia, AccountStatus.Active);
    eventCopy();
    // There is no row to lock before the first insert, so the key is what serialises the two calls: this is the
    // losing one, and the outcome is not reachable with a sequential request (the real race is in the IT)
    ports.concurrentJoinWinner = new EventMembership(event, sofia, EventRole.EventAdmin, List.of(), 1);

    var named = service.name(admin, event, sofia);

    assertThat(named.changed()).isFalse();
    assertThat(named.membership())
        .isEqualTo(new EventMembership(event, sofia, EventRole.EventAdmin, List.of(), 1));
    assertThat(named.displayName()).isEqualTo("Sofia Organiser");
    // The winner published its MembershipChanged; this call must not publish a second one with the same version
    assertThat(ports.outbox).isEmpty();
    assertThat(ports.namings).isEmpty();
  }

  @Test
  @DisplayName("US-06 AC-1 a lost race that leaves no row at all is the same 404, and still writes nothing")
  void aLostRaceWithNoRowIsNotFound() {
    farolAdmin();
    account(sofia, AccountStatus.Active);
    eventCopy();
    ports.joinConflictsWithoutAMembership = true;

    assertThatThrownBy(() -> service.name(admin, event, sofia))
        .isInstanceOf(EventNotFoundException.class);

    assertThat(ports.outbox).isEmpty();
    assertThat(ports.namings).isEmpty();
  }

  @Test
  @DisplayName("US-06 AC-1 a RegularUser of the event is promoted, one version later, keeping their teams")
  void aRegularUserIsPromotedKeepingTheirTeams() {
    farolAdmin();
    account(sofia, AccountStatus.Active);
    eventCopy();
    var nursing = UUID.randomUUID();
    ports.member(new EventMembership(event, sofia, EventRole.RegularUser, List.of(nursing), 3));

    var named = service.name(admin, event, sofia);

    assertThat(named.changed()).isTrue();
    assertThat(named.membership())
        .isEqualTo(new EventMembership(event, sofia, EventRole.EventAdmin, List.of(nursing), 4));
    assertThat(ports.locked).contains(sofia); // the lock of US-18 D7
    assertThat(ports.outbox).singleElement()
        .satisfies(sent -> assertThat(sent.type()).isEqualTo("MembershipChanged"));
  }

  @Test
  @DisplayName("US-06 AC-1 an event with no copy yet is the same 404 as an unknown event, and nothing is written")
  void anEventWithNoCopyIsNotFound() {
    farolAdmin();
    account(sofia, AccountStatus.Active);

    assertThatThrownBy(() -> service.name(admin, event, sofia))
        .isInstanceOf(EventNotFoundException.class);

    assertThat(ports.roleWrites).isEmpty();
    assertThat(ports.insertedMemberships).isEmpty();
    assertThat(ports.outbox).isEmpty();
    assertThat(ports.refusals).containsExactly(
        new InMemoryPorts.Refused(admin, event, Refusal.NO_SUCH_EVENT_COPY, NOW));
  }

  @Test
  @DisplayName("US-06 AC-2 a caller who is not the FarolAdmin writes nothing, and the refusal is recorded")
  void aCallerWhoIsNotTheFarolAdminWritesNothing() {
    account(sofia, AccountStatus.Active);
    eventCopy();
    var user = UUID.randomUUID();

    assertThatThrownBy(() -> service.name(user, event, sofia))
        .isInstanceOf(NotAFarolAdminException.class);

    assertThat(ports.roleWrites).isEmpty();
    assertThat(ports.insertedMemberships).isEmpty();
    assertThat(ports.outbox).isEmpty();
    assertThat(ports.refusals).containsExactly(
        new InMemoryPorts.Refused(user, event, Refusal.NOT_A_FAROL_ADMIN, NOW));
  }

  @Test
  @DisplayName("US-06 AC-2 an EventAdmin of the event is still not the FarolAdmin: this is the platform's job")
  void anEventAdminOfTheEventCannotNameOne() {
    account(sofia, AccountStatus.Active);
    eventCopy();
    var eventAdmin = UUID.randomUUID();
    ports.member(new EventMembership(event, eventAdmin, EventRole.EventAdmin, List.of(), 1));

    assertThatThrownBy(() -> service.name(eventAdmin, event, sofia))
        .isInstanceOf(NotAFarolAdminException.class);

    assertThat(ports.roleWrites).isEmpty();
  }

  @Test
  @DisplayName("US-06 AC-1 a userId with no account is the same 404, so the lookup is the only way to find one")
  void aUserWithNoAccountIsNotFound() {
    farolAdmin();
    eventCopy();

    assertThatThrownBy(() -> service.name(admin, event, UUID.randomUUID()))
        .isInstanceOf(EventNotFoundException.class);

    assertThat(ports.roleWrites).isEmpty();
    assertThat(ports.refusals).singleElement()
        .satisfies(refused -> assertThat(refused.refusal()).isEqualTo(Refusal.NO_SUCH_MEMBER));
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(value = AccountStatus.class, names = {"Disabled", "DeletionPending"})
  @DisplayName("US-06 AC-1 an account that is not active cannot be named, and nothing is written")
  void anInactiveAccountCannotBeNamed(AccountStatus status) {
    farolAdmin();
    account(sofia, status);
    eventCopy();

    assertThatThrownBy(() -> service.name(admin, event, sofia))
        .isInstanceOf(EventNotFoundException.class);

    assertThat(ports.roleWrites).isEmpty();
    assertThat(ports.outbox).isEmpty();
  }

  @Test
  @DisplayName("US-06 AC-1 a deleted account cannot be named, even while its id is still known")
  void aDeletedAccountCannotBeNamed() {
    farolAdmin();
    account(sofia, AccountStatus.Active);
    eventCopy();
    ports.tombstones.add(sofia);

    assertThatThrownBy(() -> service.name(admin, event, sofia))
        .isInstanceOf(EventNotFoundException.class);

    assertThat(ports.roleWrites).isEmpty();
  }
}
