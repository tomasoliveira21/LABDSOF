package pt.isep.labdsof.transports.adapters.out.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import pt.isep.labdsof.transports.application.BusData;
import pt.isep.labdsof.transports.domain.ObservedBus;

/**
 * The buses in use (spec US-40 D4): one statement upserts every vehicle and its position from arrays, so an import of
 * 1000 buses is one round trip; a second deletes the positions of the buses that are not in the import.
 */
@Repository
class JdbcBusData implements BusData {

  private final JdbcClient jdbc;

  JdbcBusData(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void replace(List<ObservedBus> buses, Instant retrievedAt) {
    List<UUID> kept = jdbc.sql("""
        WITH incoming AS (
          SELECT * FROM unnest(CAST(:providers AS text[]), CAST(:ids AS text[]), CAST(:fleets AS text[]),
                               CAST(:routes AS text[]), CAST(:longitudes AS float8[]), CAST(:latitudes AS float8[]),
                               CAST(:observed AS text[]))
            AS bus(data_provider, external_entity_id, fleet_vehicle_id, route_id, longitude, latitude, observed_at)
        ), vehicle AS (
          INSERT INTO bus_vehicle (vehicle_id, data_provider, external_entity_id, fleet_vehicle_id)
          SELECT gen_random_uuid(), data_provider, external_entity_id, fleet_vehicle_id FROM incoming
          ON CONFLICT (data_provider, external_entity_id) DO UPDATE SET fleet_vehicle_id = excluded.fleet_vehicle_id
          RETURNING vehicle_id, data_provider, external_entity_id
        )
        INSERT INTO bus_observation (vehicle_id, longitude, latitude, route_id, observed_at, retrieved_at)
        SELECT vehicle.vehicle_id, incoming.longitude, incoming.latitude, incoming.route_id,
               CAST(incoming.observed_at AS timestamptz), :retrievedAt
        FROM incoming JOIN vehicle USING (data_provider, external_entity_id)
        ON CONFLICT (vehicle_id) DO UPDATE SET longitude = excluded.longitude, latitude = excluded.latitude,
          route_id = excluded.route_id, observed_at = excluded.observed_at, retrieved_at = excluded.retrieved_at
        RETURNING vehicle_id
        """)
        .param("providers", buses.stream().map(ObservedBus::dataProvider).toArray(String[]::new))
        .param("ids", buses.stream().map(ObservedBus::externalEntityId).toArray(String[]::new))
        .param("fleets", buses.stream().map(ObservedBus::fleetVehicleId).toArray(String[]::new))
        .param("routes", buses.stream().map(ObservedBus::routeId).toArray(String[]::new))
        .param("longitudes", buses.stream().map(ObservedBus::longitude).toArray(Double[]::new))
        .param("latitudes", buses.stream().map(ObservedBus::latitude).toArray(Double[]::new))
        .param("observed", buses.stream().map(b -> b.observedAt().toString()).toArray(String[]::new))
        .param("retrievedAt", Timestamp.from(retrievedAt))
        .query(UUID.class).list();
    jdbc.sql("DELETE FROM bus_observation WHERE NOT (vehicle_id = ANY(:kept))")
        .param("kept", kept.toArray(UUID[]::new))
        .update();
  }

  @Override
  public List<ObservedBus> inUse() {
    return jdbc.sql("""
        SELECT v.external_entity_id, v.data_provider, v.fleet_vehicle_id, o.route_id, o.longitude, o.latitude,
               o.observed_at
        FROM bus_observation o JOIN bus_vehicle v USING (vehicle_id)
        ORDER BY v.external_entity_id
        """)
        .query((rs, row) -> new ObservedBus(rs.getString("external_entity_id"), rs.getString("data_provider"),
            rs.getString("fleet_vehicle_id"), rs.getString("route_id"), rs.getDouble("longitude"),
            rs.getDouble("latitude"), rs.getTimestamp("observed_at").toInstant()))
        .list();
  }
}
