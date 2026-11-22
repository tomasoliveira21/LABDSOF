package pt.isep.labdsof.events.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pt.isep.labdsof.events.application.LocalCopyService.Outcome;
import pt.isep.labdsof.events.domain.Membership;
import pt.isep.labdsof.events.domain.Membership.Role;
import pt.isep.labdsof.events.domain.Membership.Status;

class LocalCopyServiceTest {

  private static final UUID EVENT = UUID.randomUUID();
  private static final UUID USER = UUID.randomUUID();

  private final InMemoryPorts ports = new InMemoryPorts();
  private final LocalCopyService service = new LocalCopyService(ports, ports);

  private static Membership membership(Role role, Status status, Set<UUID> teams, long version) {
    return new Membership(EVENT, USER, role, status, teams, version);
  }

  private Membership stored() {
    return ports.membership(EVENT, USER).orElseThrow();
  }

  @Test
  @DisplayName("192 AC-6 a MembershipChanged is applied, a duplicate messageId changes nothing")
  void appliedAndDuplicate() {
    var message = UUID.randomUUID();

    assertThat(service.apply(message, membership(Role.RegularUser, Status.Active, Set.of(), 1)))
        .isEqualTo(Outcome.APPLIED);
    assertThat(service.apply(message, membership(Role.EventAdmin, Status.Active, Set.of(), 2)))
        .isEqualTo(Outcome.DUPLICATE);

    assertThat(stored().role()).isEqualTo(Role.RegularUser);
    assertThat(stored().version()).isEqualTo(1);
    assertThat(ports.processed).containsExactly(message);
  }

  @Test
  @DisplayName("192 AC-6 an older or equal MembershipChanged version changes nothing")
  void olderOrEqualVersionChangesNothing() {
    service.apply(UUID.randomUUID(), membership(Role.RegularUser, Status.Active, Set.of(), 5));

    assertThat(service.apply(UUID.randomUUID(), membership(Role.EventAdmin, Status.Removed, Set.of(), 4)))
        .isEqualTo(Outcome.STALE);
    assertThat(service.apply(UUID.randomUUID(), membership(Role.EventAdmin, Status.Removed, Set.of(), 5)))
        .isEqualTo(Outcome.STALE);

    assertThat(stored()).isEqualTo(membership(Role.RegularUser, Status.Active, Set.of(), 5));
  }

  @Test
  @DisplayName("192 AC-6 a removed membership stays removed when an older Active version arrives late")
  void removedStaysRemoved() {
    service.apply(UUID.randomUUID(), membership(Role.RegularUser, Status.Removed, Set.of(), 3));

    var late = service.apply(UUID.randomUUID(), membership(Role.RegularUser, Status.Active, Set.of(), 2));

    assertThat(late).isEqualTo(Outcome.STALE);
    assertThat(stored().status()).isEqualTo(Status.Removed);
    assertThat(stored().isActive()).isFalse();
  }

  @Test
  @DisplayName("192 AC-6 a newer version replaces the role, status and teams")
  void newerVersionReplaces() {
    var team = UUID.randomUUID();
    service.apply(UUID.randomUUID(), membership(Role.RegularUser, Status.Active, Set.of(), 1));

    var outcome = service.apply(UUID.randomUUID(), membership(Role.EventAdmin, Status.Removed, Set.of(team), 2));

    assertThat(outcome).isEqualTo(Outcome.APPLIED);
    assertThat(stored()).isEqualTo(membership(Role.EventAdmin, Status.Removed, Set.of(team), 2));
  }
}
