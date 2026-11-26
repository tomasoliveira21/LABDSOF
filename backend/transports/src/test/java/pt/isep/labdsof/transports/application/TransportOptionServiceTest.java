package pt.isep.labdsof.transports.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pt.isep.labdsof.transports.application.TransportOptions.Added;
import pt.isep.labdsof.transports.domain.BusRoute;
import pt.isep.labdsof.transports.domain.EventCopy;
import pt.isep.labdsof.transports.domain.EventStatus;
import pt.isep.labdsof.transports.domain.Membership;
import pt.isep.labdsof.transports.domain.Membership.Role;
import pt.isep.labdsof.transports.domain.Membership.Status;
import pt.isep.labdsof.transports.domain.NewBusRoute;
import pt.isep.labdsof.transports.domain.Permission;
import pt.isep.labdsof.transports.domain.Team;

class TransportOptionServiceTest {

  private static final Instant NOW = Instant.parse("2027-05-08T23:14:05Z");
  private static final UUID EVENT = UUID.fromString("0b6f3c1e-5a3b-4f0e-9d0e-6a1b2c3d4e5f");
  private static final UUID USER = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");
  private static final UUID TEAM = UUID.fromString("16fd2706-8baf-433b-82eb-8c7fada847da");
  private static final NewBusRoute ROUTE = new NewBusRoute("500", "Hospital S. João");

  private final FakeCopies copies = new FakeCopies();
  private final FakeOptions options = new FakeOptions();
  private final TransportOptionService service =
      new TransportOptionService(copies, options, Clock.fixed(NOW, ZoneOffset.UTC));

  private void given(EventStatus status, Role role, UUID... teams) {
    copies.event = Optional.of(new EventCopy(EVENT, status, 1));
    copies.membership = Optional.of(new Membership(EVENT, USER, role, Status.Active, Set.of(teams), 1));
  }

  @Test
  @DisplayName("US-41 AC-2 an EventAdmin's route is stored with who added it and when")
  void stored() {
    given(EventStatus.Published, Role.EventAdmin);

    var route = service.addBusRoute(EVENT, USER, ROUTE);

    assertThat(route.eventId()).isEqualTo(EVENT);
    assertThat(route.externalRoute()).isEqualTo("500");
    assertThat(route.name()).isEqualTo("Hospital S. João");
    assertThat(route.addedAt()).isEqualTo(NOW);
    assertThat(route.addedBy()).isEqualTo(USER);
    assertThat(route.optionId()).isNotNull();
    assertThat(options.added).containsExactly(route);
    assertThat(options.limit).isEqualTo(NewBusRoute.MAX_PER_EVENT);
  }

  @Test
  @DisplayName("US-41 D3 a member of a team with Transport.Edit adds a route")
  void teamMember() {
    given(EventStatus.Draft, Role.RegularUser, TEAM);
    copies.teams.put(TEAM, new Team(TEAM, EVENT, Set.of(Permission.TRANSPORT_EDIT), false, 1));

    assertThat(service.addBusRoute(EVENT, USER, ROUTE).externalRoute()).isEqualTo("500");
  }

  @Test
  @DisplayName("US-41 AC-3 an outsider gets 404, whatever they send")
  void outsider() {
    copies.event = Optional.of(new EventCopy(EVENT, EventStatus.Published, 1));

    assertThatThrownBy(() -> service.addBusRoute(EVENT, USER, ROUTE)).isInstanceOf(EventNotFoundException.class);
    assertThatThrownBy(() -> service.refuseShuttle(EVENT, USER)).isInstanceOf(EventNotFoundException.class);
    assertThat(options.added).isEmpty();
  }

  @Test
  @DisplayName("US-41 D3 a member without Transport.Edit gets 403, also for a shuttle")
  void forbidden() {
    given(EventStatus.Published, Role.RegularUser, TEAM);
    copies.teams.put(TEAM, new Team(TEAM, EVENT, Set.of(Permission.MAP_EDIT), false, 1));

    assertThatThrownBy(() -> service.addBusRoute(EVENT, USER, ROUTE)).isInstanceOf(ForbiddenException.class);
    assertThatThrownBy(() -> service.refuseShuttle(EVENT, USER)).isInstanceOf(ForbiddenException.class);
  }

  @Test
  @DisplayName("US-41 D2 a shuttle answers 501 once access is granted, even in a closed event")
  void shuttle() {
    given(EventStatus.Closed, Role.EventAdmin);

    assertThatThrownBy(() -> service.refuseShuttle(EVENT, USER)).isInstanceOf(ShuttleNotYetException.class);
  }

  @Test
  @DisplayName("US-41 D3 a closed event refuses a new route")
  void closed() {
    given(EventStatus.Closed, Role.EventAdmin);

    assertThatThrownBy(() -> service.addBusRoute(EVENT, USER, ROUTE)).isInstanceOf(EventClosedException.class);
    assertThat(options.added).isEmpty();
  }

  @Test
  @DisplayName("US-41 D4 a route already on the event, or one route too many, is a conflict")
  void conflicts() {
    given(EventStatus.Published, Role.EventAdmin);

    options.answer = Added.TAKEN;
    assertThatThrownBy(() -> service.addBusRoute(EVENT, USER, ROUTE)).isInstanceOf(BusRouteTakenException.class);
    options.answer = Added.FULL;
    assertThatThrownBy(() -> service.addBusRoute(EVENT, USER, ROUTE)).isInstanceOf(BusRouteLimitException.class);
  }

  /** The copies of one user in one event, and some teams. */
  private static final class FakeCopies implements LocalCopies {
    Optional<Membership> membership = Optional.empty();
    Optional<EventCopy> event = Optional.empty();
    final Map<UUID, Team> teams = new HashMap<>();

    @Override
    public Optional<Membership> membership(UUID eventId, UUID userId) {
      return membership.filter(m -> m.eventId().equals(eventId) && m.userId().equals(userId));
    }

    @Override
    public Optional<EventCopy> event(UUID eventId) {
      return event.filter(e -> e.eventId().equals(eventId));
    }

    @Override
    public Optional<Team> team(UUID teamId) {
      return Optional.ofNullable(teams.get(teamId));
    }

    @Override
    public boolean upsertIfNewer(Membership m) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean upsertIfNewer(EventCopy e) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean upsertIfNewer(Team t) {
      throw new UnsupportedOperationException();
    }
  }

  private static final class FakeOptions implements TransportOptions {
    Added answer = Added.ADDED;
    final List<BusRoute> added = new ArrayList<>();
    int limit;

    @Override
    public Added addBusRoute(BusRoute route, int limit) {
      this.limit = limit;
      if (answer == Added.ADDED) {
        added.add(route);
      }
      return answer;
    }

    @Override
    public List<BusRoute> busRoutes(UUID eventId) {
      return added.stream().filter(r -> r.eventId().equals(eventId)).toList();
    }
  }
}
