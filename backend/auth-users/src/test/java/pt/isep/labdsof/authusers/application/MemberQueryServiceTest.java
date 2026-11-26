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
import pt.isep.labdsof.authusers.application.EventMemberships.Member;
import pt.isep.labdsof.authusers.application.MemberAudit.Refusal;
import pt.isep.labdsof.authusers.domain.EventMembership;
import pt.isep.labdsof.authusers.domain.EventRole;
import pt.isep.labdsof.authusers.domain.UserAccount;

/** US-18: the member list an EventAdmin picks from (spec D1, D10). */
class MemberQueryServiceTest {

  private static final Instant NOW = Instant.parse("2027-04-30T10:15:30.123456Z");

  private final InMemoryPorts ports = new InMemoryPorts();
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  private final MemberQueryService service = new MemberQueryService(new EventAdmins(ports, ports, clock), ports);
  private final UUID event = UUID.randomUUID();
  private final UUID sofia = UUID.randomUUID();
  private final UUID nursing = UUID.randomUUID();

  private UUID member(String displayName, EventRole role, UUID... teams) {
    var userId = UUID.randomUUID();
    ports.accounts.put(userId, UserAccount.provision(userId, displayName.toLowerCase() + "@farol.test", true,
        displayName, NOW));
    ports.member(new EventMembership(event, userId, role, List.of(teams), 1));
    return userId;
  }

  @Test
  @DisplayName("US-18 AC-1 the list carries every member of the event with role and teams, ordered by name")
  void theListCarriesEveryMember() {
    ports.accounts.put(sofia, UserAccount.provision(sofia, "sofia@farol.test", true, "Sofia", NOW));
    ports.member(new EventMembership(event, sofia, EventRole.EventAdmin, List.of(), 1));
    var rita = member("Rita", EventRole.RegularUser, nursing);
    var pedro = member("Pedro", EventRole.RegularUser);

    var members = service.list(sofia, event, null, null);

    assertThat(members).extracting(Member::userId).containsExactly(pedro, rita, sofia);
    assertThat(members).extracting(Member::displayName).containsExactly("Pedro", "Rita", "Sofia");
    assertThat(members.get(1).teamIds()).containsExactly(nursing);
    assertThat(members.get(1).role()).isEqualTo(EventRole.RegularUser);
    assertThat(members.get(2).role()).isEqualTo(EventRole.EventAdmin);
  }

  @Test
  @DisplayName("US-18 AC-1 the list can be filtered by role and by team")
  void theListCanBeFiltered() {
    ports.accounts.put(sofia, UserAccount.provision(sofia, "sofia@farol.test", true, "Sofia", NOW));
    ports.member(new EventMembership(event, sofia, EventRole.EventAdmin, List.of(), 1));
    var rita = member("Rita", EventRole.RegularUser, nursing);
    member("Pedro", EventRole.RegularUser);

    assertThat(service.list(sofia, event, EventRole.EventAdmin, null)).extracting(Member::userId)
        .containsExactly(sofia);
    assertThat(service.list(sofia, event, null, nursing)).extracting(Member::userId).containsExactly(rita);
    assertThat(service.list(sofia, event, EventRole.RegularUser, nursing)).extracting(Member::userId)
        .containsExactly(rita);
    assertThat(service.list(sofia, event, EventRole.EventAdmin, nursing)).isEmpty();
    assertThat(service.list(sofia, event, null, UUID.randomUUID())).isEmpty(); // a team of another event
  }

  @Test
  @DisplayName("US-18 AC-1 only an EventAdmin of the event may list its members")
  void onlyAnEventAdminMayListTheMembers() {
    var rita = member("Rita", EventRole.RegularUser);
    var stranger = UUID.randomUUID();

    assertThatThrownBy(() -> service.list(rita, event, null, null))
        .isInstanceOf(NotAnEventAdminException.class);
    assertThatThrownBy(() -> service.list(stranger, event, null, null))
        .isInstanceOf(EventNotFoundException.class);
    assertThatThrownBy(() -> service.list(stranger, UUID.randomUUID(), null, null))
        .isInstanceOf(EventNotFoundException.class);
    assertThat(ports.refusals).extracting(InMemoryPorts.Refused::refusal)
        .containsExactly(Refusal.NOT_AN_EVENT_ADMIN, Refusal.NOT_A_MEMBER, Refusal.NOT_A_MEMBER);
  }

  @Test
  @DisplayName("US-18 AC-1 the list holds this event's members only")
  void theListHoldsThisEventsMembersOnly() {
    ports.accounts.put(sofia, UserAccount.provision(sofia, "sofia@farol.test", true, "Sofia", NOW));
    ports.member(new EventMembership(event, sofia, EventRole.EventAdmin, List.of(), 1));
    var otherEvent = UUID.randomUUID();
    var elsewhere = UUID.randomUUID();
    ports.member(new EventMembership(otherEvent, elsewhere, EventRole.RegularUser, List.of(), 1));

    assertThat(service.list(sofia, event, null, null)).extracting(Member::userId).containsExactly(sofia);
  }
}
