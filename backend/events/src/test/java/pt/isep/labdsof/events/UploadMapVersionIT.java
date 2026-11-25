package pt.isep.labdsof.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import pt.isep.labdsof.events.domain.Membership.Role;
import pt.isep.labdsof.events.domain.Membership.Status;
import pt.isep.labdsof.events.domain.VenueMapVersion;

/** POST /api/v1/events/{eventId}/map-versions over HTTP with signed tokens and real files (US-19). */
@ExtendWith(OutputCaptureExtension.class)
class UploadMapVersionIT extends IntegrationTest {

  private ApiClient api() {
    return new ApiClient(port);
  }

  private static String path(Object eventId) {
    return "/api/v1/events/" + eventId + "/map-versions";
  }

  private static byte[] image(String format, int width, int height) {
    var bytes = new ByteArrayOutputStream();
    try {
      ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format, bytes);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return bytes.toByteArray();
  }

  private static byte[] png() {
    return image("png", 20, 10);
  }

  private static byte[] pdf(int pages) {
    try (var document = new PDDocument(); var bytes = new ByteArrayOutputStream()) {
      for (var page = 0; page < pages; page++) {
        document.addPage(new PDPage(PDRectangle.A5));
      }
      document.save(bytes);
      return bytes.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** A file of exactly {@code size} bytes that is a real PNG at the front: the size is what is being tested. */
  private static byte[] padded(int size) {
    var png = png();
    var padded = new byte[size];
    System.arraycopy(png, 0, padded, 0, Math.min(png.length, size));
    return padded;
  }

  private UUID published() {
    return insertEvent("Published", null, "code-" + UUID.randomUUID());
  }

  private UUID admin(UUID eventId) {
    var user = UUID.randomUUID();
    member(eventId, user, Role.EventAdmin, Status.Active, 1);
    return user;
  }

  /** A live team of the event with one permission, and a member of it. */
  private UUID teamMember(UUID eventId, String permission) {
    var teamId = UUID.randomUUID();
    jdbc.sql("""
        INSERT INTO team (team_id, event_id, name, handled_categories, version, created_at, created_by,
                          updated_at, updated_by)
        VALUES (:team, :event, :name, '{}', 1, now(), :by, now(), :by)
        """).param("team", teamId).param("event", eventId).param("name", "Equipa " + teamId)
        .param("by", UUID.randomUUID()).update();
    jdbc.sql("""
        INSERT INTO team_permission (event_id, team_id, permission, report_types)
        VALUES (:event, :team, :permission, '{}')
        """).param("event", eventId).param("team", teamId).param("permission", permission).update();
    var user = UUID.randomUUID();
    member(eventId, user, Role.RegularUser, Status.Active, 1, Set.of(teamId));
    return user;
  }

  private HttpResponse<String> upload(Object eventId, UUID user, byte[] content) {
    return api().upload(path(eventId), JWKS.valid(user.toString()), ApiClient.Part.file(content));
  }

  private List<Integer> storedVersions(UUID eventId) {
    return jdbc.sql("SELECT version FROM venue_map_version WHERE event_id = :id ORDER BY version")
        .param("id", eventId).query(Integer.class).list();
  }

  /** The VenueMapVersionPublished payloads the outbox holds for the event, oldest first. */
  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> publishedVersions(UUID eventId) {
    return jdbc.sql("SELECT body::text FROM outbox WHERE type = 'VenueMapVersionPublished' "
        + "AND body->>'eventId' = :event ORDER BY id").param("event", eventId.toString())
        .query(String.class).list().stream()
        .map(body -> (Map<String, Object>) ApiClient.readJson(body).get("payload")).toList();
  }

  @Test
  @DisplayName("US-19 AC-1 a member with Map.Edit uploads a PNG and it becomes the current version")
  void mapEditUploadsAPng() throws IOException {
    var event = published();
    var pedro = teamMember(event, "Map.Edit");

    var response = upload(event, pedro, png());

    assertThat(response.statusCode()).isEqualTo(201);
    var body = ApiClient.json(response);
    assertThat(body).containsEntry("version", 1).containsEntry("pointsOfInterest", List.of());
    assertThat(body.keySet()).containsExactlyInAnyOrder("version", "uploadedAt", "pointsOfInterest");
    assertThat(body.get("uploadedAt")).asString().isNotBlank();
    assertThat(storedVersions(event)).containsExactly(1);
    assertThat(jdbc.sql("""
        SELECT image_file || ' ' || source_content_type || ' ' || source_size_bytes || ' ' || uploaded_by
        FROM venue_map_version WHERE event_id = :id AND version = 1
        """).param("id", event).query(String.class).single())
        .isEqualTo(event + "/1.png image/png " + png().length + " " + pedro);
    var stored = MAP_DIRECTORY.resolve(VenueMapVersion.imageFileOf(event, 1));
    assertThat(stored).exists();
    assertThat(ImageIO.read(stored.toFile()).getWidth()).isEqualTo(20);
    assertThat(publishedVersions(event)).singleElement().satisfies(payload ->
        assertThat(payload).containsEntry("mapVersion", 1).containsKey("publishedAt"));
  }

  @Test
  @DisplayName("US-19 AC-1 each upload becomes the next version and keeps the one before it")
  void eachUploadIsTheNextVersion() {
    var event = published();
    var sofia = admin(event);

    assertThat(ApiClient.json(upload(event, sofia, png()))).containsEntry("version", 1);
    assertThat(ApiClient.json(upload(event, sofia, image("jpg", 10, 20)))).containsEntry("version", 2);
    assertThat(ApiClient.json(upload(event, sofia, pdf(1)))).containsEntry("version", 3);

    assertThat(storedVersions(event)).containsExactly(1, 2, 3);
    assertThat(jdbc.sql("SELECT source_content_type FROM venue_map_version WHERE event_id = :id ORDER BY version")
        .param("id", event).query(String.class).list())
        .containsExactly("image/png", "image/jpeg", "application/pdf");
    for (var version : List.of(1, 2, 3)) {
      assertThat(MAP_DIRECTORY.resolve(VenueMapVersion.imageFileOf(event, version))).exists();
    }
    assertThat(publishedVersions(event)).hasSize(3);
  }

  @Test
  @DisplayName("US-19 AC-1 a new version starts with a copy of the live points of the one before it")
  void aNewVersionCopiesThePoints() {
    var event = published();
    var sofia = admin(event);
    upload(event, sofia, png());
    insertPoint(event, 1, "Posto 1", false);
    insertPoint(event, 1, "Posto fechado", true);

    var response = upload(event, sofia, png());

    assertThat(response.statusCode()).isEqualTo(201);
    var points = points(response);
    assertThat(points).singleElement().satisfies(point -> {
      assertThat(point).containsEntry("name", "Posto 1").containsEntry("type", "FirstAid")
          .containsEntry("position", Map.of("x", 0.25, "y", 0.5));
      assertThat(point.get("poiId")).asString().isNotBlank();
    });
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @ValueSource(strings = {"<svg xmlns=\"http://www.w3.org/2000/svg\"/>", "PK a zip", "a text file", ""})
  @DisplayName("US-19 AC-2 a file that is not a map is refused and the current version stays")
  void aFileThatIsNotAMapIsRefused(String content) {
    var event = published();
    var sofia = admin(event);
    upload(event, sofia, png());

    var response = upload(event, sofia, content.getBytes(StandardCharsets.ISO_8859_1));

    ApiClient.assertProblem(response, 400);
    assertThat(ApiClient.json(response).get("detail"))
        .isEqualTo("The map must be a PNG, JPEG or single-page PDF.");
    assertThat(storedVersions(event)).containsExactly(1);
    assertThat(publishedVersions(event)).hasSize(1);
  }

  @Test
  @DisplayName("US-19 AC-2 a PDF of two pages is not a map either, and the current version stays")
  void aPdfOfTwoPagesIsRefused() {
    var event = published();
    var sofia = admin(event);
    upload(event, sofia, png());

    ApiClient.assertProblem(upload(event, sofia, pdf(2)), 400);
    assertThat(storedVersions(event)).containsExactly(1);
  }

  @Test
  @DisplayName("US-19 AC-3 a file of exactly the limit is accepted and one byte more is refused with the limit "
      + "in the message")
  void theSizeLimitIsEnforced() {
    var event = published();
    var sofia = admin(event);

    var atTheLimit = upload(event, sofia, padded((int) VenueMapVersion.MAX_SOURCE_SIZE_BYTES));
    var overTheLimit = upload(event, sofia, padded((int) VenueMapVersion.MAX_SOURCE_SIZE_BYTES + 1));

    assertThat(atTheLimit.statusCode()).isEqualTo(201);
    ApiClient.assertProblem(overTheLimit, 413);
    assertThat(ApiClient.json(overTheLimit).get("detail")).isEqualTo("The map file must be at most 20 MB.");
    assertThat(storedVersions(event)).containsExactly(1);
    assertThat(publishedVersions(event)).hasSize(1);
  }

  @Test
  @DisplayName("US-19 AC-2 a request without the file part is refused")
  void aRequestWithoutTheFilePartIsRefused() {
    var event = published();
    var sofia = admin(event);
    var token = JWKS.valid(sofia.toString());

    var noPart = api().upload(path(event), token);
    var wrongName = api().upload(path(event), token, ApiClient.Part.field("map", "x"));
    var json = api().send("POST", path(event), "Bearer " + token, "{}");

    assertThat(List.of(noPart.statusCode(), wrongName.statusCode())).containsOnly(400);
    assertThat(json.statusCode()).isIn(400, 415);
    assertThat(storedVersions(event)).isEmpty();
  }

  @Test
  @DisplayName("US-19 AC-1 the file name and the Content-Type of the part are ignored: the content decides")
  void theContentDecidesNotTheName() {
    var event = published();
    var sofia = admin(event);

    var lying = api().upload(path(event), JWKS.valid(sofia.toString()),
        new ApiClient.Part("file", "map.pdf", "image/svg+xml", png()));
    var svgNamedPng = api().upload(path(event), JWKS.valid(sofia.toString()),
        new ApiClient.Part("file", "map.png", "image/png", "<svg/>".getBytes(StandardCharsets.ISO_8859_1)));

    assertThat(lying.statusCode()).isEqualTo(201);
    ApiClient.assertProblem(svgNamedPng, 400);
    assertThat(storedVersions(event)).containsExactly(1);
  }

  @Test
  @DisplayName("US-19 AC-1 extra form fields next to the file change nothing (mass assignment)")
  void extraFormFieldsChangeNothing() {
    var event = published();
    var sofia = admin(event);

    var response = api().upload(path(event), JWKS.valid(sofia.toString()), ApiClient.Part.file(png()),
        ApiClient.Part.field("version", "99"), ApiClient.Part.field("uploadedBy", UUID.randomUUID().toString()));

    assertThat(response.statusCode()).isEqualTo(201);
    assertThat(ApiClient.json(response)).containsEntry("version", 1);
    assertThat(jdbc.sql("SELECT uploaded_by FROM venue_map_version WHERE event_id = :id").param("id", event)
        .query(UUID.class).single()).isEqualTo(sofia);
  }

  @Test
  @DisplayName("US-19 AC-1 a member without Map.Edit may not upload")
  void aMemberWithoutMapEditMayNotUpload() {
    var event = published();
    var triage = teamMember(event, "HelpRequests.Triage");
    var rita = UUID.randomUUID();
    member(event, rita, Role.RegularUser, Status.Active, 1);

    ApiClient.assertProblem(upload(event, triage, png()), 403);
    ApiClient.assertProblem(upload(event, rita, png()), 403);
    assertThat(storedVersions(event)).isEmpty();
  }

  @Test
  @DisplayName("US-19 AC-1 a RegularUser cannot see a draft, so uploading to it is not found")
  void aRegularUserCannotSeeADraft() {
    var event = insertEvent("Draft", null, null);
    var pedro = teamMember(event, "Map.Edit");

    ApiClient.assertProblem(upload(event, pedro, png()), 404);
    assertThat(storedVersions(event)).isEmpty();
  }

  @Test
  @DisplayName("US-19 AC-1 an unknown event, a non-member, another event's admin and a removed member are all "
      + "the same 404")
  void everyRefusalIsTheSame404() {
    var event = published();
    var removed = UUID.randomUUID();
    member(event, removed, Role.EventAdmin, Status.Removed, 1);
    var otherEvent = published();
    var otherAdmin = admin(otherEvent);

    ApiClient.assertProblem(upload(UUID.randomUUID(), admin(event), png()), 404);
    ApiClient.assertProblem(upload(event, UUID.randomUUID(), png()), 404);
    ApiClient.assertProblem(upload(event, otherAdmin, png()), 404);
    ApiClient.assertProblem(upload(event, removed, png()), 404);
    assertThat(storedVersions(event)).isEmpty();
    assertThat(storedVersions(otherEvent)).isEmpty();
  }

  @Test
  @DisplayName("US-19 AC-1 a malformed event id is refused with 400")
  void aMalformedEventIdIsRefused() {
    ApiClient.assertProblem(upload("not-a-uuid", UUID.randomUUID(), png()), 400);
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource({"Suspended, Published", "Closed,"})
  @DisplayName("US-19 AC-1 a suspended or closed event takes no map")
  void aSuspendedOrClosedEventTakesNoMap(String status, String previous) {
    var event = insertEvent(status, previous, "code-" + UUID.randomUUID());
    var sofia = admin(event);

    var response = upload(event, sofia, png());

    ApiClient.assertProblem(response, 409);
    assertThat(ApiClient.json(response).get("detail")).isEqualTo("This event is not open to changes.");
    assertThat(storedVersions(event)).isEmpty();
  }

  @Test
  @DisplayName("US-19 AC-1 the upload is recorded with who made it and when, and never with the file")
  void theUploadIsRecordedWithoutTheFile(CapturedOutput output) {
    var event = published();
    var sofia = admin(event);
    var correlationId = UUID.randomUUID().toString();

    var response = api().upload(path(event), JWKS.valid(sofia.toString()),
        List.of(ApiClient.Part.file(png())), "X-Correlation-Id", correlationId);

    assertThat(response.statusCode()).isEqualTo(201);
    assertThat(logLines(output)).anySatisfy(line -> {
      assertThat(line.get("message")).isEqualTo("Uploaded a venue map version (US-19)");
      assertThat(line).containsEntry("userId", sofia.toString()).containsEntry("eventId", event.toString())
          .containsEntry("mapVersion", 1).containsEntry("sourceContentType", "image/png")
          .containsEntry("sourceSizeBytes", png().length);
      assertThat(line).doesNotContainKeys("imageFile", "file", "content");
      assertThat(line).containsEntry("correlationId", correlationId);
    });
  }

  @Test
  @DisplayName("US-19 AC-1 nothing of the map image reaches the log")
  void theFileNeverReachesTheLog(CapturedOutput output) {
    var event = published();
    var sofia = admin(event);

    upload(event, sofia, png());

    assertThat(output.getOut()).doesNotContain(VenueMapVersion.imageFileOf(event, 1));
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> points(HttpResponse<String> response) {
    return (List<Map<String, Object>>) ApiClient.json(response).get("pointsOfInterest");
  }

  private void insertPoint(UUID eventId, int mapVersion, String name, boolean deleted) {
    jdbc.sql("""
        INSERT INTO point_of_interest (poi_id, event_id, map_version, type, name, x, y, deleted_at, version,
                                       updated_at, updated_by)
        VALUES (:id, :event, :version, 'FirstAid', :name, 0.25, 0.5, :deleted, 1, now(), :by)
        """).param("id", UUID.randomUUID()).param("event", eventId).param("version", mapVersion)
        .param("name", name).param("deleted", deleted ? java.sql.Timestamp.from(java.time.Instant.now()) : null)
        .param("by", UUID.randomUUID()).update();
  }
}
