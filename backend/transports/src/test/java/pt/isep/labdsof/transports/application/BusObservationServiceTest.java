package pt.isep.labdsof.transports.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pt.isep.labdsof.transports.application.AccessAudit.Refusal;
import pt.isep.labdsof.transports.application.FakeAccessAudit.Entry;
import pt.isep.labdsof.transports.domain.BusRoute;
import pt.isep.labdsof.transports.domain.BusView.RouteSummary;
import pt.isep.labdsof.transports.domain.EventCopy;
import pt.isep.labdsof.transports.domain.EventStatus;
import pt.isep.labdsof.transports.domain.Membership;
import pt.isep.labdsof.transports.domain.Membership.Role;
import pt.isep.labdsof.transports.domain.Membership.Status;
import pt.isep.labdsof.transports.domain.ObservedBus;
import pt.isep.labdsof.transports.domain.SourceImport;
import pt.isep.labdsof.transports.domain.Venue;

class BusObservationServiceTest {

  private static final Instant NOW = Instant.parse("2027-05-08T23:14:05Z");
  private static final UUID EVENT = UUID.fromString("0b6f3c1e-5a3b-4f0e-9d0e-6a1b2c3d4e5f");
  private static final UUID USER = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");
  private static final BusRoute ROUTE = new BusRoute(UUID.randomUUID(), EVENT, "500", null, NOW, USER);
  private static final ObservedBus BUS =
      new ObservedBus("stcp:3412", "STCP", "3412", "500", -8.6841, 41.1734, NOW.minusSeconds(57));

  private final FakeAccessAudit audit = new FakeAccessAudit();
  private final FakeLocalCopies copies = new FakeLocalCopies();
  private final BusObservationService service = new BusObservationService(copies,
      new TransportOptions() {
        @Override
        public Added addBusRoute(BusRoute route, int limit) {
          throw new UnsupportedOperationException();
        }

        @Override
        public List<BusRoute> busRoutes(UUID eventId) {
          return eventId.equals(EVENT) ? List.of(ROUTE) : List.of();
        }
      },
      new BusData() {
        @Override
        public void replace(List<ObservedBus> buses, Instant retrievedAt) {
          throw new UnsupportedOperationException();
        }

        @Override
        public List<ObservedBus> inUse() {
          return List.of(BUS);
        }
      },
      new SourceImports() {
        @Override
        public void record(SourceImport sourceImport) {
          throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Instant> lastSucceededAt() {
          return Optional.of(NOW.minusSeconds(20));
        }
      },
      Clock.fixed(NOW, ZoneOffset.UTC), audit, "41.1724,-8.6841", Duration.ofMinutes(5));

  private void given(EventStatus status, Role role, Status membership) {
    copies.event = Optional.of(new EventCopy(EVENT, status, 1));
    copies.membership = Optional.of(new Membership(EVENT, USER, role, membership, Set.of(), 1));
  }

  @Test
  @DisplayName("US-41 AC-1 a member gets the event's view: venue latitude first, routes, buses, data time")
  void view() {
    given(EventStatus.Published, Role.RegularUser, Status.Active);

    var view = service.view(EVENT, USER);

    assertThat(view.venue()).isEqualTo(new Venue(-8.6841, 41.1724));
    assertThat(view.routes()).containsExactly(new RouteSummary("500", null, 1));
    assertThat(view.buses()).singleElement().satisfies(shown -> {
      assertThat(shown.bus()).isEqualTo(BUS);
      assertThat(shown.distanceMetres()).isEqualTo(111);
    });
    assertThat(view.dataAsOf()).contains(NOW.minusSeconds(20));
    assertThat(view.stale()).isFalse();
  }

  @Test
  @DisplayName("US-41 AC-3 an outsider, a removed member, a suspended event and a RegularUser's draft get 404")
  void notFound() {
    assertThatThrownBy(() -> service.view(EVENT, USER)).isInstanceOf(EventNotFoundException.class);

    assertThat(audit.refusals).isEmpty();

    given(EventStatus.Published, Role.EventAdmin, Status.Removed);
    assertThatThrownBy(() -> service.view(EVENT, USER)).isInstanceOf(EventNotFoundException.class);

    given(EventStatus.Suspended, Role.EventAdmin, Status.Active);
    assertThatThrownBy(() -> service.view(EVENT, USER)).isInstanceOf(EventNotFoundException.class);

    given(EventStatus.Draft, Role.RegularUser, Status.Active);
    assertThatThrownBy(() -> service.view(EVENT, USER)).isInstanceOf(EventNotFoundException.class);
    var notVisible = new Entry(USER, EVENT, NOW, Refusal.NOT_VISIBLE);
    assertThat(audit.refusals).containsExactly(new Entry(USER, EVENT, NOW, Refusal.NOT_A_MEMBER),
        notVisible, notVisible);
  }
}
