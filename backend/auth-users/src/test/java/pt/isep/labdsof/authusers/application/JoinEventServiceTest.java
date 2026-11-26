package pt.isep.labdsof.authusers.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import pt.isep.labdsof.authusers.application.MyMemberships.Status;
import pt.isep.labdsof.authusers.domain.AccountStatus;
import pt.isep.labdsof.authusers.domain.EventMembership;
import pt.isep.labdsof.authusers.domain.EventRole;
import pt.isep.labdsof.authusers.domain.UserAccount;

/** US-12: joining an event by its QR code or link, with the ports faked (spec §4 F1). */
class JoinEventServiceTest {

  private static final Instant NOW = Instant.parse("2027-04-30T10:15:30.123456Z");

  private final InMemoryPorts ports = new InMemoryPorts();
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  private final JoinEventService service = new JoinEventService(ports, ports, ports, ports, ports, ports, clock);
  private final UUID rita = UUID.randomUUID();
  private final UUID event = UUID.randomUUID();

  private void account(AccountStatus status) {
    var provisioned = UserAccount.provision(rita, "rita@farol.test", true, "Rita", NOW);
    ports.accounts.put(rita, new UserAccount(rita, provisioned.email(), true, provisioned.displayName(), status,
        1, NOW, NOW));
  }

  private void published() {
    ports.eventCopy(event, "Queima das Fitas 2027", Status.Published);
  }

  @Test
  @DisplayName("US-12 AC-1 a logged-in user joins a published event and becomes a RegularUser of it")
  void aLoggedInUserJoinsAPublishedEvent() {
    account(AccountStatus.Active);
    published();

    var joined = service.join(rita, event);

    assertThat(joined.created()).isTrue();
    assertThat(joined.eventName()).isEqualTo("Queima das Fitas 2027");
    assertThat(joined.eventStatus()).isEqualTo(Status.Published);
    assertThat(joined.membership().role()).isEqualTo(EventRole.RegularUser);
    assertThat(joined.membership().teamIds()).isEmpty();
    assertThat(joined.membership().version()).isEqualTo(1);
    assertThat(ports.find(event, rita)).contains(joined.membership());
    assertThat(ports.joins).singleElement().satisfies(line -> {
      assertThat(line.actorUserId()).isEqualTo(rita);
      assertThat(line.eventId()).isEqualTo(event);
      assertThat(line.at()).isEqualTo(NOW);
    });
    assertThat(ports.outbox).singleElement().satisfies(published -> {
      assertThat(published.type()).isEqualTo("MembershipChanged");
      assertThat(published.eventId()).isEqualTo(event);
      assertThat(published.actorUserId()).isEqualTo(rita.toString()); // the joiner is the actor (D7)
      assertThat(published.payload()).containsExactlyInAnyOrderEntriesOf(Map.of(
          "userId", rita.toString(), "role", "RegularUser", "status", "Active",
          "teamIds", List.of(), "version", 1L));
    });
  }

  @Test
  @DisplayName("US-12 AC-1 joining twice changes nothing and publishes nothing")
  void joiningTwiceChangesNothing() {
    account(AccountStatus.Active);
    published();

    var first = service.join(rita, event);
    var second = service.join(rita, event);

    assertThat(first.created()).isTrue();
    assertThat(second.created()).isFalse();
    assertThat(second.membership()).isEqualTo(first.membership());
    assertThat(ports.outbox).hasSize(1);
    assertThat(ports.joins).hasSize(1);
    assertThat(ports.insertedMemberships).hasSize(1); // the second call does not even try to insert
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(value = Status.class, names = {"Draft", "Suspended", "Closed"})
  @DisplayName("US-12 AC-3 only a published event can be joined")
  void onlyAPublishedEventCanBeJoined(Status status) {
    account(AccountStatus.Active);
    ports.eventCopy(event, "Queima das Fitas 2027", status);

    assertThatThrownBy(() -> service.join(rita, event)).isInstanceOf(EventNotFoundException.class);
    assertThat(ports.find(event, rita)).isEmpty();
    assertThat(ports.outbox).isEmpty();
    assertThat(ports.refusals).containsExactly(
        new InMemoryPorts.Refused(rita, event, MemberAudit.Refusal.NOT_JOINABLE, NOW));
  }

  @Test
  @DisplayName("US-12 AC-3 an event with no copy yet and an unknown event are the same not-found")
  void anEventWithNoCopyIsTheSameNotFound() {
    account(AccountStatus.Active);

    assertThatThrownBy(() -> service.join(rita, event)).isInstanceOf(EventNotFoundException.class);
    assertThatThrownBy(() -> service.join(rita, UUID.randomUUID())).isInstanceOf(EventNotFoundException.class);
    assertThat(ports.outbox).isEmpty();
  }

  @Test
  @DisplayName("US-12 AC-1 an existing member keeps their role, EventAdmin included")
  void anExistingMemberKeepsTheirRole() {
    account(AccountStatus.Active);
    published();
    ports.member(new EventMembership(event, rita, EventRole.EventAdmin, List.of(UUID.randomUUID()), 3));

    var joined = service.join(rita, event);

    assertThat(joined.created()).isFalse();
    assertThat(joined.membership().role()).isEqualTo(EventRole.EventAdmin);
    assertThat(joined.membership().version()).isEqualTo(3);
    assertThat(joined.membership().teamIds()).hasSize(1);
    assertThat(ports.outbox).isEmpty();
    assertThat(ports.joins).isEmpty();
  }

  @Test
  @DisplayName("US-12 AC-1 the loser of two scans at once answers the winner's membership and publishes nothing")
  void theLoserOfTwoScansAnswersTheWinnersMembership() {
    account(AccountStatus.Active);
    published();
    var winner = new EventMembership(event, rita, EventRole.RegularUser, List.of(), 1);
    ports.concurrentJoinWinner = winner;

    var joined = service.join(rita, event);

    assertThat(joined.created()).isFalse();
    assertThat(joined.membership()).isEqualTo(winner);
    assertThat(ports.outbox).isEmpty(); // the winner's call published it
    assertThat(ports.joins).isEmpty();
  }

  @Test
  @DisplayName("US-12 AC-1 a conflict with no membership behind it is an error, not a silent answer")
  void aConflictWithNoMembershipIsAnError() {
    account(AccountStatus.Active);
    published();
    ports.joinConflictsWithoutAMembership = true;

    // Only the membership's own key can refuse that insert, so the row has to be there to be read
    assertThatThrownBy(() -> service.join(rita, event)).isInstanceOf(EventNotFoundException.class);
    assertThat(ports.outbox).isEmpty();
  }

  @Test
  @DisplayName("US-12 AC-1 an account that cannot act is refused, and the event is not even read")
  void anAccountThatCannotActIsRefused() {
    published();

    assertThatThrownBy(() -> service.join(rita, event)).isInstanceOf(AccountUnavailableException.class);

    account(AccountStatus.Disabled);
    assertThatThrownBy(() -> service.join(rita, event)).isInstanceOf(AccountDisabledException.class);

    account(AccountStatus.DeletionPending);
    assertThatThrownBy(() -> service.join(rita, event)).isInstanceOf(AccountUnavailableException.class);

    account(AccountStatus.Active);
    ports.tombstones.add(rita);
    assertThatThrownBy(() -> service.join(rita, event)).isInstanceOf(AccountUnavailableException.class);

    assertThat(ports.find(event, rita)).isEmpty();
    assertThat(ports.outbox).isEmpty();
    assertThat(ports.refusals).isEmpty();
  }
}
