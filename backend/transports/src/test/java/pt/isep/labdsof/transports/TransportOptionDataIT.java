package pt.isep.labdsof.transports;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pt.isep.labdsof.transports.application.TransportOptions;
import pt.isep.labdsof.transports.application.TransportOptions.Added;
import pt.isep.labdsof.transports.domain.BusRoute;
import pt.isep.labdsof.transports.domain.EventCopy;
import pt.isep.labdsof.transports.domain.EventStatus;

/** Spec US-41 D4, D6: one route once per event, at most 20, also for requests at the same time. */
class TransportOptionDataIT extends IntegrationTest {

  private static final int LIMIT = 20;

  @Autowired private TransportOptions options;
  @Autowired private PlatformTransactionManager transactions;

  private UUID event() {
    var eventId = UUID.randomUUID();
    copies.upsertIfNewer(new EventCopy(eventId, EventStatus.Published, 1));
    return eventId;
  }

  private static BusRoute route(UUID eventId, String number, Instant at) {
    return new BusRoute(UUID.randomUUID(), eventId, number, null, at, UUID.randomUUID());
  }

  private Added add(BusRoute route) {
    return new TransactionTemplate(transactions).execute(status -> options.addBusRoute(route, LIMIT));
  }

  private int rows(UUID eventId) {
    return jdbc.sql("SELECT count(*) FROM transport_option WHERE event_id = :e").param("e", eventId)
        .query(Integer.class).single();
  }

  /** Runs {@code adds} at the same time, each in its own transaction. */
  private List<Added> together(List<BusRoute> adds) throws Exception {
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(adds.size())) {
      var futures = new ArrayList<Future<Added>>();
      for (var route : adds) {
        futures.add(pool.submit(() -> {
          start.await();
          return add(route);
        }));
      }
      start.countDown();
      var results = new ArrayList<Added>();
      for (var future : futures) {
        results.add(future.get(30, TimeUnit.SECONDS));
      }
      return results;
    }
  }

  @Test
  @DisplayName("US-41 D4 a route is stored once per event")
  void oncePerEvent() {
    var eventId = event();
    var other = event();
    var now = Instant.now().truncatedTo(ChronoUnit.MICROS);

    assertThat(add(route(eventId, "500", now))).isEqualTo(Added.ADDED);
    assertThat(add(route(eventId, "500", now))).isEqualTo(Added.TAKEN);
    assertThat(add(route(other, "500", now))).isEqualTo(Added.ADDED);
    assertThat(rows(eventId)).isEqualTo(1);
  }

  @Test
  @DisplayName("US-41 D4 the 21st route is refused")
  void limit() {
    var eventId = event();
    var now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    for (var i = 0; i < LIMIT; i++) {
      assertThat(add(route(eventId, "r" + i, now))).isEqualTo(Added.ADDED);
    }

    assertThat(add(route(eventId, "r20", now))).isEqualTo(Added.FULL);
    assertThat(rows(eventId)).isEqualTo(LIMIT);
  }

  @Test
  @DisplayName("US-41 D4 two adds of the same route at the same time store one row")
  void sameRouteTogether() throws Exception {
    var eventId = event();
    var now = Instant.now().truncatedTo(ChronoUnit.MICROS);

    var results = together(List.of(route(eventId, "500", now), route(eventId, "500", now)));

    assertThat(results).containsExactlyInAnyOrder(Added.ADDED, Added.TAKEN);
    assertThat(rows(eventId)).isEqualTo(1);
  }

  @Test
  @DisplayName("US-41 D4 twenty adds at the same time on an event with 19 routes store one")
  void limitTogether() throws Exception {
    var eventId = event();
    var now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    for (var i = 0; i < LIMIT - 1; i++) {
      add(route(eventId, "r" + i, now));
    }
    var adds = new ArrayList<BusRoute>();
    for (var i = 0; i < LIMIT; i++) {
      adds.add(route(eventId, "new" + i, now));
    }

    var results = together(adds);

    assertThat(results).filteredOn(Added.ADDED::equals).hasSize(1);
    assertThat(results).filteredOn(Added.FULL::equals).hasSize(LIMIT - 1);
    assertThat(rows(eventId)).isEqualTo(LIMIT);
  }

  @Test
  @DisplayName("US-41 D6 a route changed later (US-37) keeps its place: the order is when it was added")
  void orderSurvivesAChange() {
    var eventId = event();
    var at = Instant.parse("2027-05-08T22:00:00Z");
    add(route(eventId, "500", at));
    add(route(eventId, "502", at.plusSeconds(60)));

    jdbc.sql("UPDATE transport_option SET last_updated_at = :later WHERE event_id = :e AND external_route = '500'")
        .param("later", java.sql.Timestamp.from(at.plusSeconds(3600))).param("e", eventId).update();

    assertThat(options.busRoutes(eventId)).extracting(BusRoute::externalRoute).containsExactly("500", "502");
  }

  @Test
  @DisplayName("US-41 D3 the event's status is checked again under the lock: closed, suspended or gone stores nothing")
  void statusUnderTheLock() {
    var now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    var closed = UUID.randomUUID();
    copies.upsertIfNewer(new EventCopy(closed, EventStatus.Closed, 1));
    var suspended = UUID.randomUUID();
    copies.upsertIfNewer(new EventCopy(suspended, EventStatus.Suspended, 1));

    assertThat(add(route(closed, "500", now))).isEqualTo(Added.CLOSED);
    assertThat(add(route(suspended, "500", now))).isEqualTo(Added.GONE);
    assertThat(add(route(UUID.randomUUID(), "500", now))).isEqualTo(Added.GONE);
    assertThat(rows(closed) + rows(suspended)).isZero();
  }

  @Test
  @DisplayName("US-41 D6 the routes come back in the order they were added, with what was stored")
  void order() {
    var eventId = event();
    var at = Instant.parse("2027-05-08T22:00:00Z");
    var first = new BusRoute(UUID.randomUUID(), eventId, "502", "Hospital S. João", at, UUID.randomUUID());
    add(route(eventId, "500", at.plusSeconds(60)));
    add(first);
    add(route(eventId, "200", at.plusSeconds(120)));

    var routes = options.busRoutes(eventId);

    assertThat(routes).extracting(BusRoute::externalRoute).containsExactly("502", "500", "200");
    assertThat(routes.getFirst()).isEqualTo(first);
    assertThat(options.busRoutes(UUID.randomUUID())).isEmpty();
  }
}
