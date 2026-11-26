package pt.isep.labdsof.events.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import pt.isep.labdsof.events.IntegrationTest;
import pt.isep.labdsof.events.application.VenueMaps;
import pt.isep.labdsof.events.domain.MapPosition;
import pt.isep.labdsof.events.domain.MapSourceType;
import pt.isep.labdsof.events.domain.PoiType;
import pt.isep.labdsof.events.domain.PointOfInterest;
import pt.isep.labdsof.events.domain.VenueMapVersion;

/**
 * The map versions and their points through their port. The lock of US-19 D7 can only be shown here: two
 * transactions numbering a version of one event at the same time.
 */
class JdbcVenueMapsIT extends IntegrationTest {

  private static final Instant AT = Instant.parse("2027-04-30T10:15:30Z");

  @Autowired VenueMaps maps;
  @Autowired TransactionTemplate transactions;

  private UUID event() {
    return insertEvent("Published", null, "code-" + UUID.randomUUID());
  }

  private VenueMapVersion uploaded(UUID eventId, int version) {
    return VenueMapVersion.uploaded(eventId, version, MapSourceType.PNG, 2048, AT, UUID.randomUUID());
  }

  private PointOfInterest point(UUID eventId, int mapVersion, String name) {
    return new PointOfInterest(UUID.randomUUID(), eventId, mapVersion, PoiType.FirstAid, name,
        new MapPosition(0.25, 0.5), false, 1);
  }

  /** The version numbers stored for that event, in order. */
  private List<Integer> storedVersions(UUID eventId) {
    return jdbc.sql("SELECT version FROM venue_map_version WHERE event_id = :id ORDER BY version")
        .param("id", eventId).query(Integer.class).list();
  }

  @Test
  @DisplayName("US-19 AC-1 a version is stored with its file, its source and who uploaded it")
  void aVersionIsStoredWhole() {
    var eventId = event();
    var version = transactions.execute(status -> {
      var stored = uploaded(eventId, maps.nextVersionOf(eventId));
      maps.add(stored);
      return stored;
    });

    assertThat(version.version()).isEqualTo(1);
    assertThat(jdbc.sql("""
        SELECT image_file || ' ' || source_content_type || ' ' || source_size_bytes || ' ' || uploaded_by
        FROM venue_map_version WHERE event_id = :id AND version = 1
        """).param("id", eventId).query(String.class).single())
        .isEqualTo(eventId + "/1.png image/png 2048 " + version.uploadedBy());
  }

  @Test
  @DisplayName("US-19 AC-1 each upload of an event gets the next version, and other events are untouched")
  void versionsAreNumberedPerEvent() {
    var first = event();
    var second = event();

    for (var event : List.of(first, first, first, second)) {
      transactions.executeWithoutResult(status -> maps.add(uploaded(event, maps.nextVersionOf(event))));
    }

    assertThat(storedVersions(first)).containsExactly(1, 2, 3);
    assertThat(storedVersions(second)).containsExactly(1);
  }

  @Test
  @DisplayName("US-19 AC-1 two uploads of one event get consecutive versions")
  void twoUploadsOfOneEventGetConsecutiveVersions() throws Exception {
    var eventId = event();

    // Both transactions number their version at the same time: without the FOR UPDATE of D7 they would both
    // read 0 and the second INSERT would die on venue_map_version's primary key.
    try (var executor = Executors.newFixedThreadPool(2)) {
      var uploads = executor.invokeAll(List.of(upload(eventId), upload(eventId)));
      for (var upload : uploads) {
        assertThat(upload.get(30, TimeUnit.SECONDS)).isNotNull();
      }
    }

    assertThat(storedVersions(eventId)).containsExactly(1, 2);
  }

  @Test
  @DisplayName("US-19 AC-1 uploads of different events do not block each other")
  void uploadsOfDifferentEventsDoNotBlockEachOther() throws Exception {
    var first = event();
    var second = event();

    try (var executor = Executors.newFixedThreadPool(2)) {
      for (var upload : executor.invokeAll(List.of(upload(first), upload(second)))) {
        assertThat(upload.get(30, TimeUnit.SECONDS)).isNotNull();
      }
    }

    assertThat(storedVersions(first)).containsExactly(1);
    assertThat(storedVersions(second)).containsExactly(1);
  }

  @Test
  @DisplayName("US-19 AC-1 the new version starts with a copy of the live points of the one before it")
  void theNewVersionCopiesTheLivePoints() {
    var eventId = event();
    transactions.executeWithoutResult(status -> maps.add(uploaded(eventId, 1)));
    var live = point(eventId, 1, "Posto 1");
    var deleted = point(eventId, 1, "Posto fechado");
    var actor = UUID.randomUUID();
    transactions.executeWithoutResult(status -> maps.addPoints(List.of(live, deleted), actor, AT));
    jdbc.sql("UPDATE point_of_interest SET deleted_at = now() WHERE poi_id = :id")
        .param("id", deleted.poiId()).update();

    var copies = transactions.execute(status -> {
      maps.add(uploaded(eventId, 2));
      var copied = maps.livePointsOf(eventId, 1).stream()
          .map(point -> point.copiedTo(2, UUID.randomUUID())).toList();
      maps.addPoints(copied, actor, AT);
      return copied;
    });

    assertThat(copies).singleElement().satisfies(copy -> assertThat(copy.name()).isEqualTo("Posto 1"));
    assertThat(maps.livePointsOf(eventId, 2)).singleElement().satisfies(copy -> {
      assertThat(copy.name()).isEqualTo("Posto 1");
      assertThat(copy.type()).isEqualTo(PoiType.FirstAid);
      assertThat(copy.position()).isEqualTo(new MapPosition(0.25, 0.5));
      assertThat(copy.version()).isEqualTo(1);
      assertThat(copy.poiId()).isNotEqualTo(live.poiId());
    });
    // The version before it keeps its own two rows, the deleted one included
    assertThat(maps.livePointsOf(eventId, 1)).hasSize(1);
    assertThat(jdbc.sql("SELECT count(*) FROM point_of_interest WHERE event_id = :id AND map_version = 1")
        .param("id", eventId).query(Integer.class).single()).isEqualTo(2);
  }

  @Test
  @DisplayName("US-19 AC-1 a version with no points, and a version that is not there, read as no points")
  void noPointsIsNoPoints() {
    var eventId = event();
    transactions.executeWithoutResult(status -> maps.add(uploaded(eventId, 1)));

    assertThat(maps.livePointsOf(eventId, 1)).isEmpty();
    assertThat(maps.livePointsOf(eventId, 0)).isEmpty();
    assertThat(maps.livePointsOf(UUID.randomUUID(), 1)).isEmpty();
  }

  @Test
  @DisplayName("US-19 AC-1 the points of another event's version are never read")
  void anotherEventsPointsAreNeverRead() {
    var mine = event();
    var other = event();
    transactions.executeWithoutResult(status -> {
      maps.add(uploaded(mine, 1));
      maps.add(uploaded(other, 1));
      maps.addPoints(List.of(point(other, 1, "Outro posto")), UUID.randomUUID(), AT);
    });

    assertThat(maps.livePointsOf(mine, 1)).isEmpty();
    assertThat(maps.livePointsOf(other, 1)).hasSize(1);
  }

  private java.util.concurrent.Callable<Integer> upload(UUID eventId) {
    return () -> transactions.execute(status -> {
      var version = maps.nextVersionOf(eventId);
      maps.add(uploaded(eventId, version));
      return version;
    });
  }
}
