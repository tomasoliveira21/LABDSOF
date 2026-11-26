package pt.isep.labdsof.events.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import pt.isep.labdsof.events.application.AccessAudit.Refusal;
import pt.isep.labdsof.events.application.InMemoryPorts.Refused;
import pt.isep.labdsof.events.domain.DateRange;
import pt.isep.labdsof.events.domain.Event;
import pt.isep.labdsof.events.domain.EventStatus;
import pt.isep.labdsof.events.domain.Membership;
import pt.isep.labdsof.events.domain.Membership.Role;
import pt.isep.labdsof.events.domain.Membership.Status;

class EventQueryServiceTest {

  private static final Instant NOW = Instant.parse("2027-05-08T23:14:05Z");
  private static final String CODE = "JOIN2027";

  private final InMemoryPorts ports = new InMemoryPorts();
  private final EventQueryService service = new EventQueryService(
      new EventReader(ports, ports, ports, Clock.fixed(NOW, ZoneOffset.UTC)));
  private final UUID user = UUID.randomUUID();

  private Event event(EventStatus status) {
    var event = new Event(UUID.randomUUID(), "Queima das Fitas 2027", "FAP",
        new DateRange(LocalDate.of(2027, 5, 2), LocalDate.of(2027, 5, 9)), "Semana academica", status, CODE, 3);
    ports.events.put(event.eventId(), event);
    return event;
  }

  private void member(Event event, Role role, Status status) {
    ports.upsertIfNewer(new Membership(event.eventId(), user, role, status, Set.of(), 1));
  }

  private void assertRefusedAs(UUID eventId, Refusal refusal) {
    assertThatThrownBy(() -> service.get(user, eventId)).isInstanceOf(EventNotFoundException.class)
        .hasMessage("event not found");
    assertThat(ports.refusals).containsExactly(new Refused(user, eventId, NOW, refusal));
  }

  @Test
  @DisplayName("192 AC-5 a user with no membership of the event gets not-found and the refusal is recorded "
      + "with user, event and time")
  void nonMemberGetsNotFound() {
    var event = event(EventStatus.Published);

    assertRefusedAs(event.eventId(), Refusal.NOT_A_MEMBER);
  }

  @Test
  @DisplayName("192 AC-5 an unknown event gets not-found and the attempt is recorded")
  void unknownEventGetsNotFound() {
    assertRefusedAs(UUID.randomUUID(), Refusal.NO_SUCH_EVENT);
  }

  @Test
  @DisplayName("192 AC-5 a member of another event gets not-found and the cross-event attempt is recorded")
  void memberOfAnotherEventGetsNotFound() {
    var mine = event(EventStatus.Published);
    var other = event(EventStatus.Published);
    member(mine, Role.EventAdmin, Status.Active);

    assertRefusedAs(other.eventId(), Refusal.NOT_A_MEMBER);
  }

  @Test
  @DisplayName("192 AC-5 a removed member gets not-found and the attempt is recorded")
  void removedMemberGetsNotFound() {
    var event = event(EventStatus.Published);
    member(event, Role.EventAdmin, Status.Removed);

    assertRefusedAs(event.eventId(), Refusal.NOT_A_MEMBER);
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(EventStatus.class)
  @DisplayName("192 AC-5 an EventAdmin gets the event in every status, with the join code only while it is "
      + "Published")
  void eventAdminSeesEveryStatus(EventStatus status) {
    var event = event(status);
    member(event, Role.EventAdmin, Status.Active);

    var view = service.get(user, event.eventId());

    assertThat(view.status()).isEqualTo(status);
    assertThat(view.joinCode()).isEqualTo(status == EventStatus.Published ? CODE : null);
    assertThat(ports.refusals).isEmpty();
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(value = EventStatus.class, names = {"Published", "Closed"})
  @DisplayName("192 AC-5 a RegularUser gets a Published or Closed event, with the join code only while "
      + "Published")
  void regularUserSeesPublishedAndClosed(EventStatus status) {
    var event = event(status);
    member(event, Role.RegularUser, Status.Active);

    var view = service.get(user, event.eventId());

    assertThat(view.status()).isEqualTo(status);
    assertThat(view.joinCode()).isEqualTo(status == EventStatus.Published ? CODE : null);
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(value = EventStatus.class, names = {"Draft", "Suspended"})
  @DisplayName("192 AC-5 a RegularUser gets not-found for a Draft or Suspended event and the attempt is "
      + "recorded")
  void regularUserDoesNotSeeDraftOrSuspended(EventStatus status) {
    var event = event(status);
    member(event, Role.RegularUser, Status.Active);

    assertRefusedAs(event.eventId(), Refusal.NOT_VISIBLE);
  }

  @Test
  @DisplayName("192 AC-5 the event view carries the event's id, name, organiser, dates, description and status")
  void viewCarriesTheEventFields() {
    var event = event(EventStatus.Published);
    member(event, Role.RegularUser, Status.Active);

    var view = service.get(user, event.eventId());

    assertThat(view).isEqualTo(new EventView(event.eventId(), "Queima das Fitas 2027", "FAP",
        new DateRange(LocalDate.of(2027, 5, 2), LocalDate.of(2027, 5, 9)), "Semana academica",
        EventStatus.Published, CODE));
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(Role.class)
  @DisplayName("192 AC-5 an allowed read records no refusal")
  void allowedReadRecordsNothing(Role role) {
    var event = event(EventStatus.Published);
    member(event, role, Status.Active);

    service.get(user, event.eventId());

    assertThat(ports.refusals).isEmpty();
  }
}
