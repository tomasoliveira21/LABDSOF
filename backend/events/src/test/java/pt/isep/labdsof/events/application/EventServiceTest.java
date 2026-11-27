package pt.isep.labdsof.events.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import pt.isep.labdsof.events.application.AccessAudit.Refusal;
import pt.isep.labdsof.events.application.InMemoryPorts.Created;
import pt.isep.labdsof.events.application.InMemoryPorts.EventWrite;
import pt.isep.labdsof.events.application.InMemoryPorts.PlatformRefused;
import pt.isep.labdsof.events.application.InMemoryPorts.Published;
import pt.isep.labdsof.events.application.InMemoryPorts.Refused;
import pt.isep.labdsof.events.domain.DateRange;
import pt.isep.labdsof.events.domain.Event;
import pt.isep.labdsof.events.domain.EventNotDraftException;
import pt.isep.labdsof.events.domain.EventStatus;
import pt.isep.labdsof.events.domain.GlobalRole;
import pt.isep.labdsof.events.domain.GlobalRoleGrant;
import pt.isep.labdsof.events.domain.InvalidEventException;
import pt.isep.labdsof.events.domain.Membership;
import pt.isep.labdsof.events.domain.Membership.Role;
import pt.isep.labdsof.events.domain.Membership.Status;

/** US-11: publishing an event, with the ports faked (spec §4 F2). */
class EventServiceTest {

  private static final Instant NOW = Instant.parse("2027-05-08T23:14:05Z");
  private static final String CODE = "JOIN-CODE-1";
  private static final DateRange DATES = new DateRange(LocalDate.of(2027, 5, 2), LocalDate.of(2027, 5, 9));

  private final InMemoryPorts ports = new InMemoryPorts();
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  private final EventService service = new EventService(new EventReader(ports, ports, ports, ports, clock), ports, ports,
      ports, ports, ports, clock);
  private final UUID admin = UUID.randomUUID();

  private Event event(EventStatus status) {
    var event = new Event(UUID.randomUUID(), "Queima das Fitas 2027", "FAP", DATES, "Semana academica", status,
        status == EventStatus.Draft ? null : "OLD-CODE", 1);
    ports.events.put(event.eventId(), event);
    return event;
  }

  private void member(Event event, UUID user, Role role, Status status) {
    ports.upsertIfNewer(new Membership(event.eventId(), user, role, status, Set.of(), 1));
  }

  private Event draftOfAdmin() {
    var event = event(EventStatus.Draft);
    member(event, admin, Role.EventAdmin, Status.Active);
    return event;
  }

  private void assertNothingHappened() {
    assertThat(ports.eventWrites).isEmpty();
    assertThat(ports.published).isEmpty();
    assertThat(ports.publishes).isEmpty();
  }

  @Test
  @DisplayName("US-06 AC-1 the FarolAdmin creates a draft, with the first EventChanged, recorded and not joined")
  void creatingADraftStoresPublishesAndRecords() {
    var farolAdmin = UUID.randomUUID();
    ports.farolAdmin(farolAdmin);

    var created = service.create(farolAdmin, "Queima das Fitas 2027", "FAP", DATES.start(), DATES.end(), "Semana academica");

    assertThat(created.status()).isEqualTo(EventStatus.Draft);
    assertThat(created.joinCode()).isNull(); // only the publish generates one (US-11 D1)
    assertThat(created.name()).isEqualTo("Queima das Fitas 2027");
    assertThat(ports.inserted).singleElement()
        .satisfies(event -> assertThat(event.version()).isEqualTo(1));
    assertThat(ports.eventWrites).containsExactly(new EventWrite(created.eventId(), farolAdmin, NOW, 1));
    assertThat(ports.published).singleElement()
        .satisfies(event -> assertThat(event.type()).isEqualTo("EventChanged"));
    assertThat(ports.creations).containsExactly(new Created(farolAdmin, created.eventId(), NOW));
    // The creator is deliberately left out of the event (D3): no membership is written for them
    assertThat(ports.memberships).isEmpty();
    assertThat(ports.platformRefusals).isEmpty();
  }

  @Test
  @DisplayName("US-06 AC-2 a caller the role copy does not hold creates nothing, and the refusal is recorded")
  void aCallerWhoIsNotTheFarolAdminCreatesNothing() {
    var user = UUID.randomUUID();

    assertThatThrownBy(() -> service.create(user, "Queima", "FAP", DATES.start(), DATES.end(), null))
        .isInstanceOf(NotPlatformAdminException.class).hasMessage("not the FarolAdmin");

    assertThat(ports.inserted).isEmpty();
    assertThat(ports.platformRefusals)
        .containsExactly(new PlatformRefused(user, NOW, "createEvent"));
    assertNothingHappened();
  }

  @Test
  @DisplayName("US-06 AC-2 an EventAdmin of an event is still not the FarolAdmin, and a revoked one is not either")
  void anEventAdminIsNotTheFarolAdminAndARevokeTakesTheRoleAway() {
    var event = draftOfAdmin();
    var revoked = UUID.randomUUID();
    ports.farolAdmin(revoked);
    ports.upsertIfNewer(new GlobalRoleGrant(revoked, GlobalRole.FarolAdmin, false, 2));

    // The EventAdmin of the draft above holds no platform role: being an admin somewhere is not one
    assertThatThrownBy(() -> service.create(admin, "Queima", "FAP", DATES.start(), DATES.end(), null))
        .isInstanceOf(NotPlatformAdminException.class);
    assertThatThrownBy(() -> service.create(revoked, "Queima", "FAP", DATES.start(), DATES.end(), null))
        .isInstanceOf(NotPlatformAdminException.class);

    assertThat(event.status()).isEqualTo(EventStatus.Draft);
    assertThat(ports.inserted).isEmpty();
  }

  @Test
  @DisplayName("US-06 AC-1 a range that ends before it starts, or a missing date, is the caller's mistake")
  void anInvalidDateRangeIsTheCallersMistake() {
    var farolAdmin = UUID.randomUUID();
    ports.farolAdmin(farolAdmin);
    var start = LocalDate.of(2027, 5, 9);
    var end = LocalDate.of(2027, 5, 2);

    // Not an IllegalArgumentException, which would leave the request a 500 (D8)
    assertThatThrownBy(() -> service.create(farolAdmin, "Queima", "FAP", start, end, null))
        .isInstanceOf(InvalidEventException.class).hasMessage("an event cannot end before it starts");
    assertThatThrownBy(() -> service.create(farolAdmin, "Queima", "FAP", null, end, null))
        .isInstanceOf(InvalidEventException.class).hasMessage("dates are required");
    assertThatThrownBy(() -> service.create(farolAdmin, "Queima", "FAP", start, null, null))
        .isInstanceOf(InvalidEventException.class).hasMessage("dates are required");

    assertThat(service.create(farolAdmin, "Queima", "FAP", end, end, null).dates().start()).isEqualTo(end);
    assertThat(ports.inserted).hasSize(1); // only the single-day event above
  }

  @Test
  @DisplayName("US-07 AC-1 the FarolAdmin lists every event, and nobody else lists any")
  void theFarolAdminListsEveryEvent() {
    var farolAdmin = UUID.randomUUID();
    ports.farolAdmin(farolAdmin);
    var summary = new EventSummaryView(UUID.randomUUID(), "Queima das Fitas 2027", "FAP", DATES,
        EventStatus.Draft, List.of());
    ports.summaries.add(summary);

    assertThat(service.list(farolAdmin)).containsExactly(summary);

    var user = UUID.randomUUID();
    assertThatThrownBy(() -> service.list(user)).isInstanceOf(NotPlatformAdminException.class);
    assertThat(ports.platformRefusals)
        .containsExactly(new PlatformRefused(user, NOW, "listEvents"));
  }

  @Test
  @DisplayName("US-07 AC-1 an empty platform lists nothing rather than failing")
  void anEmptyPlatformListsNothing() {
    var farolAdmin = UUID.randomUUID();
    ports.farolAdmin(farolAdmin);

    assertThat(service.list(farolAdmin)).isEmpty();
  }

  @Test
  @DisplayName("US-11 AC-1 publishing a draft stores it, publishes EventChanged and records who and when")
  void publishingADraftStoresPublishesAndRecords() {
    var event = draftOfAdmin();

    var view = service.publish(admin, event.eventId());

    assertThat(view).isEqualTo(new EventView(event.eventId(), "Queima das Fitas 2027", "FAP", DATES,
        "Semana academica", EventStatus.Published, CODE));
    assertThat(ports.findById(event.eventId()).orElseThrow())
        .isEqualTo(event.published(CODE))
        .extracting(Event::status, Event::joinCode, Event::version)
        .containsExactly(EventStatus.Published, CODE, 2L);
    assertThat(ports.eventWrites).containsExactly(new EventWrite(event.eventId(), admin, NOW, 2));
    assertThat(ports.publishes)
        .containsExactly(new Published(admin, event.eventId(), NOW, EventStatus.Published, 2));
    assertThat(ports.published).singleElement().satisfies(published -> {
      assertThat(published.type()).isEqualTo("EventChanged");
      assertThat(published.routingKey()).isEqualTo("event.changed");
      assertThat(published.eventId()).isEqualTo(event.eventId());
      assertThat(published.actorUserId()).isEqualTo(admin.toString());
      assertThat(published.occurredAt()).isEqualTo(NOW);
      assertThat(published.payload()).containsEntry("status", "Published").containsEntry("version", 2L)
          .containsEntry("name", "Queima das Fitas 2027").containsEntry("organiserName", "FAP")
          .doesNotContainKeys("joinCode", "description");
    });
    assertThat(ports.refusals).isEmpty();
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(value = EventStatus.class, names = {"Published", "Suspended", "Closed"})
  @DisplayName("US-11 AC-1 only a draft is published")
  void onlyADraftIsPublished(EventStatus status) {
    var event = event(status);
    member(event, admin, Role.EventAdmin, Status.Active);

    assertThatThrownBy(() -> service.publish(admin, event.eventId()))
        .isInstanceOf(EventNotDraftException.class).hasMessage("event is not a draft");
    assertThat(ports.findById(event.eventId()).orElseThrow()).isEqualTo(event);
    assertNothingHappened();
  }

  @Test
  @DisplayName("US-11 AC-1 a second publish is refused and publishes nothing")
  void secondPublishIsRefused() {
    var event = draftOfAdmin();
    var published = service.publish(admin, event.eventId());

    assertThatThrownBy(() -> service.publish(admin, event.eventId())).isInstanceOf(EventNotDraftException.class);
    assertThat(ports.findById(event.eventId()).orElseThrow().joinCode()).isEqualTo(published.joinCode());
    assertThat(ports.findById(event.eventId()).orElseThrow().version()).isEqualTo(2);
    assertThat(ports.eventWrites).hasSize(1);
    assertThat(ports.published).hasSize(1);
    assertThat(ports.publishes).hasSize(1);
  }

  @Test
  @DisplayName("US-11 AC-1 an event changed between the read and the write is a conflict, and nothing is "
      + "published")
  void eventChangedBetweenTheReadAndTheWriteIsAConflict() {
    var event = draftOfAdmin();
    ports.publishOutcome = Events.Write.CONFLICT;

    assertThatThrownBy(() -> service.publish(admin, event.eventId()))
        .isInstanceOf(EventChangedConcurrentlyException.class).hasMessage("event changed concurrently");
    assertNothingHappened();
  }

  @Test
  @DisplayName("US-11 AC-1 an event another request published between the read and the write is not a draft any "
      + "more, and nothing is published")
  void eventPublishedByAnotherRequestIsNotADraftAnyMore() {
    var event = draftOfAdmin();
    ports.publishOutcome = Events.Write.NOT_DRAFT;

    assertThatThrownBy(() -> service.publish(admin, event.eventId()))
        .isInstanceOf(EventNotDraftException.class).hasMessage("event is not a draft");
    assertNothingHappened();
  }

  @Test
  @DisplayName("US-11 AC-1 an event that is gone when the write lands is the same not-found, and is recorded")
  void eventGoneOnWriteIsNotFound() {
    var event = draftOfAdmin();
    ports.publishOutcome = Events.Write.GONE;

    assertThatThrownBy(() -> service.publish(admin, event.eventId()))
        .isInstanceOf(EventNotFoundException.class);
    assertThat(ports.refusals)
        .containsExactly(new Refused(admin, event.eventId(), NOW, Refusal.NO_SUCH_EVENT));
    assertNothingHappened();
  }

  @Test
  @DisplayName("US-11 AC-1 a RegularUser cannot see a draft, so publishing it is not found")
  void regularUserCannotSeeADraft() {
    var event = event(EventStatus.Draft);
    var user = UUID.randomUUID();
    member(event, user, Role.RegularUser, Status.Active);

    assertThatThrownBy(() -> service.publish(user, event.eventId())).isInstanceOf(EventNotFoundException.class);
    assertThat(ports.refusals).containsExactly(new Refused(user, event.eventId(), NOW, Refusal.NOT_VISIBLE));
    assertNothingHappened();
  }

  @Test
  @DisplayName("US-11 AC-1 a RegularUser of a published event has no permission to publish")
  void regularUserOfAPublishedEventIsForbidden() {
    var event = event(EventStatus.Published);
    var user = UUID.randomUUID();
    member(event, user, Role.RegularUser, Status.Active);

    assertThatThrownBy(() -> service.publish(user, event.eventId())).isInstanceOf(ForbiddenException.class);
    assertThat(ports.refusals).isEmpty();
    assertNothingHappened();
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource({"unknown event,NO_SUCH_EVENT", "non-member,NOT_A_MEMBER", "removed member,NOT_A_MEMBER",
      "admin of another event,NOT_A_MEMBER"})
  @DisplayName("US-11 AC-1 every refusal is the same not-found and is recorded")
  void everyRefusalIsTheSameNotFound(String label, Refusal refusal) {
    var event = draftOfAdmin();
    var user = UUID.randomUUID();
    var asked = event.eventId();
    switch (label) {
      case "unknown event" -> asked = UUID.randomUUID();
      case "removed member" -> member(event, user, Role.EventAdmin, Status.Removed);
      case "admin of another event" -> {
        var other = event(EventStatus.Draft);
        member(other, user, Role.EventAdmin, Status.Active);
      }
      default -> { /* a non-member has no membership copy at all */ }
    }
    var eventId = asked;

    assertThatThrownBy(() -> service.publish(user, eventId)).isInstanceOf(EventNotFoundException.class)
        .hasMessage("event not found");
    assertThat(ports.refusals).containsExactly(new Refused(user, eventId, NOW, refusal));
    assertNothingHappened();
  }
}
