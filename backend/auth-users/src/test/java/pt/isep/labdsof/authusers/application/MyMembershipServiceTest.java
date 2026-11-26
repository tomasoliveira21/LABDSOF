package pt.isep.labdsof.authusers.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pt.isep.labdsof.authusers.domain.AccountStatus;
import pt.isep.labdsof.authusers.domain.UserAccount;

/**
 * {@code GET /me/memberships} (US-01, #241), which had only integration coverage: the four outcomes of the
 * account's state are checked here, so the mutations of this use case are killed by a unit test too.
 */
class MyMembershipServiceTest {

  private static final Instant NOW = Instant.parse("2027-04-30T10:15:30Z");

  private final InMemoryPorts ports = new InMemoryPorts();
  private final MyMembershipService service = new MyMembershipService(ports, ports, ports);
  private final UUID rita = UUID.randomUUID();

  private UserAccount account(AccountStatus status) {
    var provisioned = UserAccount.provision(rita, "rita@farol.test", true, "Rita", NOW);
    var stored = new UserAccount(provisioned.userId(), provisioned.email(), true, provisioned.displayName(),
        status, provisioned.version(), NOW, NOW);
    ports.accounts.put(rita, stored);
    return stored;
  }

  @Test
  @DisplayName("US-01 an active account gets its memberships")
  void anActiveAccountGetsItsMemberships() {
    account(AccountStatus.Active);
    var event = UUID.randomUUID();
    var team = UUID.randomUUID();
    ports.myMemberships.add(new MyMemberships.Entry(event, "Queima das Fitas", MyMemberships.Status.Published,
        "RegularUser", List.of(team)));

    var mine = service.list(rita);

    assertThat(mine).singleElement().satisfies(entry -> {
      assertThat(entry.eventId()).isEqualTo(event);
      assertThat(entry.teamIds()).containsExactly(team);
    });
  }

  @Test
  @DisplayName("US-01 an account that does not exist yet has no membership, and nothing is read for it")
  void anUnknownAccountHasNoMembership() {
    ports.myMemberships.add(new MyMemberships.Entry(UUID.randomUUID(), "Not mine", null, "RegularUser", List.of()));

    assertThat(service.list(rita)).isEmpty();
  }

  @Test
  @DisplayName("US-01 a disabled account is refused, and one pending deletion or deleted is unavailable")
  void anUnavailableAccountIsRefused() {
    account(AccountStatus.Disabled);
    assertThatThrownBy(() -> service.list(rita)).isInstanceOf(AccountDisabledException.class);

    account(AccountStatus.DeletionPending);
    assertThatThrownBy(() -> service.list(rita)).isInstanceOf(AccountUnavailableException.class);

    account(AccountStatus.Active);
    ports.tombstones.add(rita);
    assertThatThrownBy(() -> service.list(rita)).isInstanceOf(AccountUnavailableException.class);
  }
}
