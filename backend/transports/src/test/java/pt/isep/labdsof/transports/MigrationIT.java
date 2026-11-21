package pt.isep.labdsof.transports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataIntegrityViolationException;

class MigrationIT extends IntegrationTest {

  private static final List<String> EVENT_DATA_TABLES = List.of("transport_option", "transport_departure",
      "membership_copy", "event_copy", "team_copy");

  @Test
  @DisplayName("193 AC-1 Flyway creates the schema of D4 in the transports database")
  void flywayCreatesTheSchema() {
    assertThat(jdbc.sql("SELECT current_database()").query(String.class).single()).isEqualTo("transports");
    assertThat(jdbc.sql("SELECT success FROM flyway_schema_history WHERE version = '1'")
        .query(Boolean.class).single()).isTrue();
    assertThat(jdbc.sql("""
        SELECT table_name FROM information_schema.tables
        WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
        """).query(String.class).list())
        .containsExactlyInAnyOrder("transport_option", "transport_departure", "bus_vehicle", "bus_observation",
            "source_import", "membership_copy", "event_copy", "team_copy", "processed_message",
            "flyway_schema_history");
  }

  @Test
  @DisplayName("193 D2 Transports has no outbox table: it publishes nothing")
  void thereIsNoOutbox() {
    assertThat(jdbc.sql("SELECT count(*) FROM information_schema.tables WHERE table_name = 'outbox'")
        .query(Integer.class).single()).isZero();
  }

  @Test
  @DisplayName("193 AC-1 every table that holds event data has a non-null uuid event_id")
  void everyEventTableHasAnEventId() {
    for (var table : EVENT_DATA_TABLES) {
      var column = jdbc.sql("""
          SELECT data_type || '/' || is_nullable FROM information_schema.columns
          WHERE table_schema = 'public' AND table_name = :t AND column_name = 'event_id'
          """).param("t", table).query(String.class).single();
      assertThat(column).as(table).isEqualTo("uuid/NO");
    }
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(delimiter = '|', textBlock = """
      transport mode Train          | transport_option_mode       | INSERT INTO transport_option (option_id, event_id, mode, name, version, last_updated_at, last_updated_by) VALUES (gen_random_uuid(), gen_random_uuid(), 'Train', 'Night train', 1, now(), gen_random_uuid())
      transport option version 0    | transport_option_version    | INSERT INTO transport_option (option_id, event_id, mode, name, version, last_updated_at, last_updated_by) VALUES (gen_random_uuid(), gen_random_uuid(), 'Shuttle', 'Shuttle A', 0, now(), gen_random_uuid())
      import outcome Maybe          | source_import_outcome       | INSERT INTO source_import (started_at, finished_at, outcome, error) VALUES (now(), now(), 'Maybe', 'x')
      event status Open             | event_copy_status           | INSERT INTO event_copy (event_id, status, version) VALUES (gen_random_uuid(), 'Open', 1)
      event version 0               | event_copy_version          | INSERT INTO event_copy (event_id, status, version) VALUES (gen_random_uuid(), 'Draft', 0)
      membership role Owner         | membership_copy_role        | INSERT INTO membership_copy (event_id, user_id, role, status, team_ids, version) VALUES (gen_random_uuid(), gen_random_uuid(), 'Owner', 'Active', '{}', 1)
      membership status Gone        | membership_copy_status      | INSERT INTO membership_copy (event_id, user_id, role, status, team_ids, version) VALUES (gen_random_uuid(), gen_random_uuid(), 'EventAdmin', 'Gone', '{}', 1)
      membership version 0          | membership_copy_version     | INSERT INTO membership_copy (event_id, user_id, role, status, team_ids, version) VALUES (gen_random_uuid(), gen_random_uuid(), 'EventAdmin', 'Active', '{}', 0)
      team permission Bus.Drive     | team_copy_permissions       | INSERT INTO team_copy (team_id, event_id, permissions, deleted, version) VALUES (gen_random_uuid(), gen_random_uuid(), '{Transport.Edit,Bus.Drive}', false, 1)
      team version 0                | team_copy_version           | INSERT INTO team_copy (team_id, event_id, permissions, deleted, version) VALUES (gen_random_uuid(), gen_random_uuid(), '{}', false, 0)
      """)
  @DisplayName("193 AC-1 the database refuses values outside the agreed lists")
  void refusesValuesOutsideTheAgreedLists(String name, String constraint, String sql) {
    refused(constraint, () -> jdbc.sql(sql).update());
  }

  @Test
  @DisplayName("193 AC-1 the database accepts every agreed value")
  void acceptsEveryAgreedValue() {
    var event = UUID.randomUUID();
    option(event, "Shuttle", "Shuttle A", null);
    option(event, "ExternalBus", null, "205");
    for (var status : List.of("Draft", "Published", "Suspended", "Closed")) {
      jdbc.sql("INSERT INTO event_copy (event_id, status, version) VALUES (gen_random_uuid(), :s, 1)")
          .param("s", status).update();
    }
    for (var role : List.of("EventAdmin", "RegularUser")) {
      for (var status : List.of("Active", "Removed")) {
        jdbc.sql("""
            INSERT INTO membership_copy (event_id, user_id, role, status, team_ids, version)
            VALUES (:event, gen_random_uuid(), :role, :status, '{}', 1)
            """).param("event", event).param("role", role).param("status", status).update();
      }
    }
    jdbc.sql("""
        INSERT INTO team_copy (team_id, event_id, permissions, deleted, version)
        VALUES (gen_random_uuid(), :event, CAST(:permissions AS text[]), false, 1)
        """).param("event", event).param("permissions", "{HelpRequests.Triage,HelpRequests.Respond,"
            + "Reports.Review,Reports.Respond,Announcements.Publish,Map.Edit,Transport.Edit,Entrances.Update}")
        .update();
    assertThat(jdbc.sql("SELECT count(*) FROM team_copy WHERE event_id = :event").param("event", event)
        .query(Integer.class).single()).isOne();
  }

  @Test
  @DisplayName("193 AC-1 an external bus route needs its route reference and a shuttle its name")
  void optionNeedsWhatItsModeNeeds() {
    var event = UUID.randomUUID();
    refused("transport_option_mode_fields", () -> option(event, "ExternalBus", "Line 205", null));
    refused("transport_option_mode_fields", () -> option(event, "Shuttle", null, "205"));
    refused("transport_option_name", () -> option(event, "Shuttle", "   ", null));
    refused("transport_option_name", () -> option(event, "Shuttle", "a".repeat(101), null));
    refused("transport_option_external_route", () -> option(event, "ExternalBus", null, ""));
    refused("transport_option_external_route", () -> option(event, "ExternalBus", null, "2".repeat(21)));
    option(event, "Shuttle", "a".repeat(100), null);
    option(event, "ExternalBus", null, "2".repeat(20));
  }

  @Test
  @DisplayName("193 AC-1 a departure needs an option of the same event and a destination")
  void departureNeedsAnOptionOfTheSameEvent() {
    var event = UUID.randomUUID();
    var option = option(event, "Shuttle", "Shuttle A", null);

    departure(option, event, "Trindade");
    refused("transport_departure_option", () -> departure(option, UUID.randomUUID(), "Trindade"));
    refused("transport_departure_option", () -> departure(UUID.randomUUID(), event, "Trindade"));
    refused("transport_departure_destination", () -> departure(option, event, " "));

    jdbc.sql("DELETE FROM transport_option WHERE option_id = :id").param("id", option).update();
    assertThat(jdbc.sql("SELECT count(*) FROM transport_departure WHERE option_id = :id").param("id", option)
        .query(Integer.class).single()).isZero();
  }

  @Test
  @DisplayName("193 AC-1 a bus position outside the globe is refused")
  void busPositionOutsideTheGlobeIsRefused() {
    observation(vehicle("stcp:3301"), -8.6841, 41.1724);
    observation(vehicle("stcp:3302"), 180, 90);
    observation(vehicle("stcp:3303"), -180, -90);
    refused("bus_observation_latitude", () -> observation(vehicle("stcp:3304"), -8.6, 91));
    refused("bus_observation_latitude", () -> observation(vehicle("stcp:3305"), -8.6, -90.01));
    refused("bus_observation_longitude", () -> observation(vehicle("stcp:3306"), 181, 41.1));
    refused("bus_observation_longitude", () -> observation(vehicle("stcp:3307"), -180.01, 41.1));
  }

  @Test
  @DisplayName("193 AC-1 a vehicle is unique per provider and has at most one latest observation")
  void vehicleIsUniquePerProvider() {
    var vehicle = vehicle("stcp:3401");
    refused("bus_vehicle_external_entity", () -> vehicle("stcp:3401"));
    observation(vehicle, -8.6, 41.1);
    refused("bus_observation_pkey", () -> observation(vehicle, -8.7, 41.2));
  }

  @Test
  @DisplayName("193 AC-1 a successful import has a vehicle count and no error, a failed one an error and no count")
  void importOutcomeMatchesItsFields() {
    sourceImport("Succeeded", 0, null);
    sourceImport("Unavailable", null, "HTTP 503");
    sourceImport("Invalid", null, "not a JSON array");
    refused("source_import_outcome_fields", () -> sourceImport("Succeeded", null, null));
    refused("source_import_outcome_fields", () -> sourceImport("Succeeded", 3, "HTTP 503"));
    refused("source_import_outcome_fields", () -> sourceImport("Unavailable", 3, "HTTP 503"));
    refused("source_import_outcome_fields", () -> sourceImport("Invalid", null, null));
    refused("source_import_vehicle_count", () -> sourceImport("Succeeded", -1, null));
    refused("source_import_error", () -> sourceImport("Invalid", null, "e".repeat(501)));
    sourceImport("Invalid", null, "e".repeat(500));
    refused("source_import_finished_at", () -> jdbc.sql("""
        INSERT INTO source_import (started_at, finished_at, outcome, vehicle_count)
        VALUES (now(), now() - interval '1 second', 'Succeeded', 0)
        """).update());
  }

  @Test
  @DisplayName("193 AC-1 processed_message keys on message_id")
  void processedMessageKeysOnMessageId() {
    var message = UUID.randomUUID();
    jdbc.sql("INSERT INTO processed_message (message_id) VALUES (:id)").param("id", message).update();
    refused("processed_message_pkey",
        () -> jdbc.sql("INSERT INTO processed_message (message_id) VALUES (:id)").param("id", message).update());
  }

  private static void refused(String constraint, ThrowingCallable insert) {
    assertThatThrownBy(insert).isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("\"" + constraint + "\"");
  }

  private UUID option(UUID event, String mode, String name, String externalRoute) {
    var id = UUID.randomUUID();
    jdbc.sql("""
        INSERT INTO transport_option (option_id, event_id, mode, name, external_route, version, last_updated_at,
                                      last_updated_by)
        VALUES (:id, :event, :mode, :name, :route, 1, now(), :by)
        """)
        .param("id", id).param("event", event).param("mode", mode).param("name", name)
        .param("route", externalRoute).param("by", UUID.randomUUID()).update();
    return id;
  }

  private void departure(UUID option, UUID event, String destination) {
    jdbc.sql("""
        INSERT INTO transport_departure (departure_id, option_id, event_id, destination, scheduled_at)
        VALUES (gen_random_uuid(), :option, :event, :destination, now())
        """).param("option", option).param("event", event).param("destination", destination).update();
  }

  private UUID vehicle(String externalId) {
    var id = UUID.randomUUID();
    jdbc.sql("""
        INSERT INTO bus_vehicle (vehicle_id, data_provider, external_entity_id, fleet_vehicle_id)
        VALUES (:id, 'PortoDigital', :external, '3301')
        """).param("id", id).param("external", externalId).update();
    return id;
  }

  private void observation(UUID vehicle, double longitude, double latitude) {
    jdbc.sql("""
        INSERT INTO bus_observation (vehicle_id, longitude, latitude, route_id, observed_at, retrieved_at)
        VALUES (:vehicle, :longitude, :latitude, '205', now() - interval '57 seconds', now())
        """).param("vehicle", vehicle).param("longitude", longitude).param("latitude", latitude).update();
  }

  private void sourceImport(String outcome, Integer vehicleCount, String error) {
    jdbc.sql("""
        INSERT INTO source_import (started_at, finished_at, outcome, vehicle_count, error)
        VALUES (now(), now(), :outcome, :count, :error)
        """).param("outcome", outcome).param("count", vehicleCount).param("error", error).update();
  }
}
