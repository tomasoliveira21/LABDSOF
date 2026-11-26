package pt.isep.labdsof.events.adapters.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import pt.isep.labdsof.events.application.VenueMaps;
import pt.isep.labdsof.events.domain.MapPosition;
import pt.isep.labdsof.events.domain.PoiType;
import pt.isep.labdsof.events.domain.PointOfInterest;
import pt.isep.labdsof.events.domain.VenueMapVersion;

/**
 * The venue map versions and their points (spec #192 D3). Versions are numbered per event, so the next number is
 * read with the <strong>event row locked</strong> for the rest of the transaction (US-19 D7): two uploads of one
 * event would otherwise read the same maximum, and the second would die on the primary key — which, as the review
 * of #249 showed, cannot be retried inside a transaction Postgres has already aborted.
 */
@Repository
class JdbcVenueMaps implements VenueMaps {

  private static final String EVENT_ID = "eventId";
  private static final String MAP_VERSION = "mapVersion";

  private final JdbcClient jdbc;

  JdbcVenueMaps(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public int nextVersionOf(UUID eventId) {
    // The lock is on the event every version belongs to; no other write of this service takes it
    jdbc.sql("SELECT 1 FROM event WHERE event_id = :eventId FOR UPDATE")
        .param(EVENT_ID, eventId)
        .query(Integer.class)
        .optional();
    return jdbc.sql("SELECT coalesce(max(version), 0) + 1 FROM venue_map_version WHERE event_id = :eventId")
        .param(EVENT_ID, eventId)
        .query(Integer.class)
        .single();
  }

  @Override
  public void add(VenueMapVersion version) {
    jdbc.sql("""
        INSERT INTO venue_map_version (event_id, version, image_file, source_content_type, source_size_bytes,
                                       uploaded_at, uploaded_by)
        VALUES (:eventId, :version, :imageFile, :sourceContentType, :sourceSizeBytes, :at, :actor)
        """)
        .param(EVENT_ID, version.eventId())
        .param("version", version.version())
        .param("imageFile", version.imageFile())
        .param("sourceContentType", version.sourceType().contentType())
        .param("sourceSizeBytes", version.sourceSizeBytes())
        .param("at", Timestamp.from(version.uploadedAt()))
        .param("actor", version.uploadedBy())
        .update();
  }

  @Override
  public List<PointOfInterest> livePointsOf(UUID eventId, int mapVersion) {
    return jdbc.sql("""
        SELECT poi_id, event_id, map_version, type, name, x, y, version
        FROM point_of_interest
        WHERE event_id = :eventId AND map_version = :mapVersion AND deleted_at IS NULL
        ORDER BY poi_id
        """)
        .param(EVENT_ID, eventId)
        .param(MAP_VERSION, mapVersion)
        .query(JdbcVenueMaps::point)
        .list();
  }

  @Override
  public void addPoints(List<PointOfInterest> points, UUID actor, Instant at) {
    for (var point : points) {
      jdbc.sql("""
          INSERT INTO point_of_interest (poi_id, event_id, map_version, type, name, x, y, version, updated_at,
                                         updated_by)
          VALUES (:poiId, :eventId, :mapVersion, :type, :name, :x, :y, :version, :at, :actor)
          """)
          .param("poiId", point.poiId())
          .param(EVENT_ID, point.eventId())
          .param(MAP_VERSION, point.mapVersion())
          .param("type", point.type().name())
          .param("name", point.name())
          .param("x", point.position().x())
          .param("y", point.position().y())
          .param("version", point.version())
          .param("at", Timestamp.from(at))
          .param("actor", actor)
          .update();
    }
  }

  private static PointOfInterest point(ResultSet rs, int row) throws SQLException {
    return new PointOfInterest(
        rs.getObject("poi_id", UUID.class),
        rs.getObject("event_id", UUID.class),
        rs.getInt("map_version"),
        PoiType.valueOf(rs.getString("type")),
        rs.getString("name"),
        new MapPosition(rs.getDouble("x"), rs.getDouble("y")),
        false,
        rs.getLong("version"));
  }
}
