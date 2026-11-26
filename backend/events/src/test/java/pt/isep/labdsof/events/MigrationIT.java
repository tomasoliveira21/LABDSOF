package pt.isep.labdsof.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class MigrationIT extends IntegrationTest {

  private static final List<String> EVENT_DATA_TABLES = List.of("event", "team", "team_permission",
      "venue_map_version", "point_of_interest", "membership_copy");
  private static final List<String> PERMISSIONS = List.of("HelpRequests.Triage", "HelpRequests.Respond",
      "Reports.Review", "Reports.Respond", "Announcements.Publish", "Map.Edit", "Transport.Edit",
      "Entrances.Update");

  @Test
  @DisplayName("192 AC-1 Flyway creates the schema of D4 in the events database")
  void flywayCreatesTheSchema() {
    assertThat(jdbc.sql("SELECT current_database()").query(String.class).single()).isEqualTo("events");
    assertThat(jdbc.sql("SELECT success FROM flyway_schema_history WHERE version = '1'")
        .query(Boolean.class).single()).isTrue();
    // V2 brings the global role copy US-06 reads to recognise a FarolAdmin
    assertThat(jdbc.sql("SELECT success FROM flyway_schema_history WHERE version = '2'")
        .query(Boolean.class).single()).isTrue();
    assertThat(jdbc.sql("""
        SELECT table_name FROM information_schema.tables
        WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
        """).query(String.class).list())
        .containsExactlyInAnyOrder("event", "team", "team_permission", "venue_map_version",
            "point_of_interest", "membership_copy", "global_role_copy", "processed_message", "outbox",
            "flyway_schema_history");
  }

  @Test
  @DisplayName("192 AC-1 every table that holds event data has a non-null uuid event_id")
  void everyEventTableHasAnEventId() {
    for (var table : EVENT_DATA_TABLES) {
      var column = jdbc.sql("""
          SELECT data_type || '/' || is_nullable FROM information_schema.columns
          WHERE table_schema = 'public' AND table_name = :t AND column_name = 'event_id'
          """).param("t", table).query(String.class).single();
      assertThat(column).as(table).isEqualTo("uuid/NO");
    }
  }

  @Test
  @DisplayName("192 AC-1 the database refuses an event status outside Draft, Published, Suspended and Closed")
  void refusesStatusOutsideTheFour() {
    insertEvent("Draft", null, null);
    insertEvent("Published", null, code());
    insertEvent("Suspended", "Draft", null);
    insertEvent("Closed", null, code());
    for (var status : List.of("Deleted", "draft", "")) {
      refused("event_status", () -> insertEvent(status, null, null));
    }
  }

  @Test
  @DisplayName("192 AC-1 the database refuses an event that ends before it starts")
  void refusesAnEventThatEndsBeforeItStarts() {
    event("Festival", "FAP", "2027-05-02", "2027-05-02", "", "Draft", null, null);
    refused("event_dates", () -> event("Festival", "FAP", "2027-05-02", "2027-05-01", "", "Draft", null, null));
  }

  @Test
  @DisplayName("192 AC-1 the database keeps the previous status only while an event is suspended, and only "
      + "Draft or Published")
  void keepsThePreviousStatusOnlyWhileSuspended() {
    insertEvent("Suspended", "Draft", null);
    insertEvent("Suspended", "Published", code());
    refused("event_previous_status_only_suspended", () -> insertEvent("Suspended", null, null));
    refused("event_previous_status_only_suspended", () -> insertEvent("Published", "Draft", code()));
    refused("event_previous_status_value", () -> insertEvent("Suspended", "Closed", null));
  }

  @Test
  @DisplayName("192 AC-1 the database keeps a unique join code exactly when the event is or was published")
  void keepsAUniqueJoinCodeExactlyWhenPublished() {
    refused("event_join_code_once_published", () -> insertEvent("Draft", null, code()));
    refused("event_join_code_once_published", () -> insertEvent("Published", null, null));
    refused("event_join_code_once_published", () -> insertEvent("Closed", null, null));
    refused("event_join_code_once_published", () -> insertEvent("Suspended", "Draft", code()));
    refused("event_join_code_once_published", () -> insertEvent("Suspended", "Published", null));

    var taken = code();
    insertEvent("Published", null, taken);
    refused("event_join_code_unique", () -> insertEvent("Published", null, taken));
  }

  @Test
  @DisplayName("192 AC-1 the database refuses an empty, blank or over-100-character event name or organiser "
      + "name, and a description over 2000 characters")
  void refusesNamesAndDescriptionOutsideTheirLengths() {
    for (var ok : List.of("a", "a".repeat(100))) {
      event(ok, "FAP", "2027-05-02", "2027-05-03", "", "Draft", null, null);
      event("Festival", ok, "2027-05-02", "2027-05-03", "", "Draft", null, null);
    }
    for (var bad : List.of("", "   ", "a".repeat(101))) {
      refused("event_name", () -> event(bad, "FAP", "2027-05-02", "2027-05-03", "", "Draft", null, null));
      refused("event_organiser_name",
          () -> event("Festival", bad, "2027-05-02", "2027-05-03", "", "Draft", null, null));
    }
    event("Festival", "FAP", "2027-05-02", "2027-05-03", "d".repeat(2000), "Draft", null, null);
    refused("event_description",
        () -> event("Festival", "FAP", "2027-05-02", "2027-05-03", "d".repeat(2001), "Draft", null, null));
  }

  @Test
  @DisplayName("192 AC-1 a team name is unique per event ignoring case, but free in another event or once the "
      + "team is deleted")
  void teamNameIsUniquePerEventIgnoringCase() {
    var first = insertEvent("Draft", null, null);
    var second = insertEvent("Draft", null, null);
    var team = team(first, "Nursing", "{}");

    refused("team_name_per_event", () -> team(first, "NURSING", "{}"));
    team(second, "nursing", "{}");

    jdbc.sql("UPDATE team SET deleted_at = now() WHERE team_id = :id").param("id", team).update();
    team(first, "Nursing", "{}");
  }

  @Test
  @DisplayName("192 AC-1 the database refuses a help category outside the five of decision 8")
  void refusesAHelpCategoryOutsideTheFive() {
    var event = insertEvent("Draft", null, null);
    team(event, "None", "{}");
    team(event, "All", "{Health,Safety,LostPerson,Infrastructure,Other}");
    refused("team_handled_categories", () -> team(event, "Theft", "{Theft}"));
    refused("team_handled_categories", () -> team(event, "Lower", "{health}"));
    refused("team_handled_categories", () -> team(event, "Null", "{NULL}"));
  }

  @Test
  @DisplayName("192 AC-1 the database refuses a permission outside features 4.2, and report types outside D6 or "
      + "on another permission")
  void refusesPermissionsOutsideTheFixedList() {
    var event = insertEvent("Draft", null, null);
    var team = team(event, "Control room", "{}");
    for (var permission : PERMISSIONS) {
      permission(event, team, permission, "{}");
    }
    jdbc.sql("DELETE FROM team_permission WHERE team_id = :t").param("t", team).update();

    refused("team_permission_permission", () -> permission(event, team, "Everything", "{}"));
    permission(event, team, "Reports.Review", "{QueueAtEntrance,Hazard}");
    jdbc.sql("DELETE FROM team_permission WHERE team_id = :t").param("t", team).update();
    refused("team_permission_report_types", () -> permission(event, team, "Reports.Review", "{Litter}"));
    refused("team_permission_report_types_only_review", () -> permission(event, team, "Map.Edit", "{Hazard}"));
  }

  @Test
  @DisplayName("192 AC-1 a team permission needs a team of the same event")
  void permissionNeedsATeamOfTheSameEvent() {
    var event = insertEvent("Draft", null, null);
    var other = insertEvent("Draft", null, null);
    var team = team(event, "Control room", "{}");

    refused("team_permission_team", () -> permission(other, team, "Map.Edit", "{}"));
    permission(event, team, "Map.Edit", "{}");
  }

  @Test
  @DisplayName("192 AC-1 the database refuses a map upload that is not PNG, JPEG or PDF or is larger than 20 MB")
  void refusesMapUploadsOutsideTypeAndSize() {
    var event = insertEvent("Draft", null, null);
    mapVersion(event, 1, "image/png", 20_971_520);
    mapVersion(event, 2, "image/jpeg", 1);
    mapVersion(event, 3, "application/pdf", 1_000);

    refused("venue_map_version_source_type", () -> mapVersion(event, 4, "image/svg+xml", 1_000));
    refused("venue_map_version_source_size", () -> mapVersion(event, 4, "image/png", 20_971_521));
    refused("venue_map_version_source_size", () -> mapVersion(event, 4, "image/png", 0));
    refused("venue_map_version_version", () -> mapVersion(event, 0, "image/png", 1_000));
  }

  @Test
  @DisplayName("192 AC-1 a point of interest needs its map version and lies in [0, 1] with a type of D6")
  void pointOfInterestNeedsItsMapVersionAndLiesOnTheMap() {
    var event = insertEvent("Draft", null, null);
    var other = insertEvent("Draft", null, null);
    mapVersion(event, 1, "image/png", 1_000);
    mapVersion(other, 1, "image/png", 1_000);
    mapVersion(other, 2, "image/png", 1_000);

    poi(event, 1, "Stage", 0, 1);
    poi(event, 1, "Other", 1, 0);
    refused("point_of_interest_x", () -> poi(event, 1, "Stage", -0.01, 0.5));
    refused("point_of_interest_x", () -> poi(event, 1, "Stage", 1.01, 0.5));
    refused("point_of_interest_y", () -> poi(event, 1, "Stage", 0.5, Double.NaN));
    refused("point_of_interest_type", () -> poi(event, 1, "Toilet", 0.5, 0.5));
    // version 2 exists, but for the other event; version 3 for nobody
    refused("point_of_interest_map_version", () -> poi(event, 2, "Stage", 0.5, 0.5));
    refused("point_of_interest_map_version", () -> poi(other, 3, "Stage", 0.5, 0.5));
    poi(other, 2, "Stage", 0.5, 0.5);
  }

  @Test
  @DisplayName("192 AC-1 the membership copy accepts only EventAdmin or RegularUser and Active or Removed")
  void membershipCopyAcceptsOnlyItsRolesAndStatuses() {
    var event = UUID.randomUUID();
    for (var role : List.of("EventAdmin", "RegularUser")) {
      for (var status : List.of("Active", "Removed")) {
        membership(event, role, status);
      }
    }
    refused("membership_copy_role", () -> membership(event, "FarolAdmin", "Active"));
    refused("membership_copy_status", () -> membership(event, "EventAdmin", "Gone"));
    assertThat(jdbc.sql("""
        SELECT data_type FROM information_schema.columns
        WHERE table_name = 'membership_copy' AND column_name = 'user_id'
        """).query(String.class).single()).isEqualTo("uuid");
  }

  @Test
  @DisplayName("192 AC-1 the outbox has the columns of 191 and a partial index on unpublished rows, and "
      + "processed_message keys on message_id")
  void outboxAndProcessedMessage() {
    assertThat(jdbc.sql("SELECT column_name FROM information_schema.columns WHERE table_name = 'outbox'")
        .query(String.class).list())
        .containsExactlyInAnyOrder("id", "message_id", "exchange", "routing_key", "type", "body",
            "correlation_id", "created_at", "attempts", "next_attempt_at", "published_at", "last_error");
    assertThat(jdbc.sql("SELECT indexdef FROM pg_indexes WHERE indexname = 'outbox_due'")
        .query(String.class).single()).contains("WHERE (published_at IS NULL)");

    var message = UUID.randomUUID();
    jdbc.sql("INSERT INTO processed_message (message_id) VALUES (:id)").param("id", message).update();
    refused("processed_message_pkey",
        () -> jdbc.sql("INSERT INTO processed_message (message_id) VALUES (:id)").param("id", message).update());
  }

  private static String code() {
    return UUID.randomUUID().toString().substring(0, 8);
  }

  private static void refused(String constraint, ThrowingCallable insert) {
    assertThatThrownBy(insert).isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("\"" + constraint + "\"");
  }

  private void event(String name, String organiser, String start, String end, String description,
      String status, String previous, String code) {
    jdbc.sql("""
        INSERT INTO event (event_id, name, organiser_name, start_date, end_date, description, status,
                           previous_status, join_code, version, created_at, created_by, updated_at, updated_by)
        VALUES (:id, :name, :organiser, :start, :end, :description, :status, :previous, :code, 1, now(), :by,
                now(), :by)
        """)
        .param("id", UUID.randomUUID()).param("name", name).param("organiser", organiser)
        .param("start", LocalDate.parse(start)).param("end", LocalDate.parse(end))
        .param("description", description).param("status", status).param("previous", previous)
        .param("code", code).param("by", UUID.randomUUID()).update();
  }

  private UUID team(UUID event, String name, String categories) {
    var id = UUID.randomUUID();
    jdbc.sql("""
        INSERT INTO team (team_id, event_id, name, handled_categories, version, created_at, created_by,
                          updated_at, updated_by)
        VALUES (:id, :event, :name, CAST(:categories AS text[]), 1, now(), :by, now(), :by)
        """)
        .param("id", id).param("event", event).param("name", name).param("categories", categories)
        .param("by", UUID.randomUUID()).update();
    return id;
  }

  private void permission(UUID event, UUID team, String permission, String reportTypes) {
    jdbc.sql("""
        INSERT INTO team_permission (event_id, team_id, permission, report_types)
        VALUES (:event, :team, :permission, CAST(:reportTypes AS text[]))
        """)
        .param("event", event).param("team", team).param("permission", permission)
        .param("reportTypes", reportTypes).update();
  }

  private void mapVersion(UUID event, int version, String contentType, long size) {
    jdbc.sql("""
        INSERT INTO venue_map_version (event_id, version, image_file, source_content_type, source_size_bytes,
                                       uploaded_at, uploaded_by)
        VALUES (:event, :version, 'map.png', :type, :size, now(), :by)
        """)
        .param("event", event).param("version", version).param("type", contentType).param("size", size)
        .param("by", UUID.randomUUID()).update();
  }

  private void poi(UUID event, int mapVersion, String type, double x, double y) {
    jdbc.sql("""
        INSERT INTO point_of_interest (poi_id, event_id, map_version, type, name, x, y, version, updated_at,
                                       updated_by)
        VALUES (:id, :event, :mapVersion, :type, 'Main stage', :x, :y, 1, now(), :by)
        """)
        .param("id", UUID.randomUUID()).param("event", event).param("mapVersion", mapVersion)
        .param("type", type).param("x", x).param("y", y).param("by", UUID.randomUUID()).update();
  }

  private void membership(UUID event, String role, String status) {
    jdbc.sql("""
        INSERT INTO membership_copy (event_id, user_id, role, status, team_ids, version)
        VALUES (:event, :user, :role, :status, '{}', 1)
        """)
        .param("event", event).param("user", UUID.randomUUID()).param("role", role).param("status", status)
        .update();
  }
}
