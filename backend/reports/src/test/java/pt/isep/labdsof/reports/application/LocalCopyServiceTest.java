package pt.isep.labdsof.reports.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pt.isep.labdsof.reports.application.LocalCopyService.Outcome;
import pt.isep.labdsof.reports.domain.Membership;
import pt.isep.labdsof.reports.domain.Membership.Role;
import pt.isep.labdsof.reports.domain.Membership.Status;
import pt.isep.labdsof.reports.domain.Team;

class LocalCopyServiceTest {

  static final UUID EVENT = UUID.randomUUID();
  static final UUID TEAM = UUID.randomUUID();

  final InMemoryPorts ports = new InMemoryPorts();
  final LocalCopyService service = new LocalCopyService(ports, ports);

  static Membership membership(Role role, long version) {
    return new Membership(EVENT, "ana", role, Status.Active, Set.of(), version);
  }

  static Team team(String name, long version) {
    return new Team(TEAM, EVENT, name, List.of(), Set.of(), false, version);
  }

  @Test
  @DisplayName("194 AC-3 a MembershipChanged is applied, a duplicate messageId is ignored")
  void membershipDuplicate() {
    var message = UUID.randomUUID();
    assertThat(service.apply(message, membership(Role.RegularUser, 1))).isEqualTo(Outcome.APPLIED);
    assertThat(service.apply(message, membership(Role.EventAdmin, 2))).isEqualTo(Outcome.DUPLICATE);
    assertThat(ports.membership(EVENT, "ana").orElseThrow().role()).isEqualTo(Role.RegularUser);
  }

  @Test
  @DisplayName("194 AC-3 an older or equal MembershipChanged version is ignored")
  void membershipStale() {
    service.apply(UUID.randomUUID(), membership(Role.EventAdmin, 2));
    assertThat(service.apply(UUID.randomUUID(), membership(Role.RegularUser, 1)))
        .isEqualTo(Outcome.STALE);
    assertThat(service.apply(UUID.randomUUID(), membership(Role.RegularUser, 2)))
        .isEqualTo(Outcome.STALE);
    assertThat(ports.membership(EVENT, "ana").orElseThrow().role()).isEqualTo(Role.EventAdmin);
  }

  @Test
  @DisplayName("194 AC-3 a TeamChanged is applied, a duplicate or older one is ignored")
  void teams() {
    var message = UUID.randomUUID();
    assertThat(service.apply(message, team("Nursing", 1))).isEqualTo(Outcome.APPLIED);
    assertThat(service.apply(message, team("Renamed", 2))).isEqualTo(Outcome.DUPLICATE);
    assertThat(service.apply(UUID.randomUUID(), team("Security", 3))).isEqualTo(Outcome.APPLIED);
    assertThat(service.apply(UUID.randomUUID(), team("Old", 2))).isEqualTo(Outcome.STALE);
    assertThat(ports.teams(EVENT, Set.of(TEAM))).singleElement()
        .extracting(Team::name).isEqualTo("Security");
  }
}
