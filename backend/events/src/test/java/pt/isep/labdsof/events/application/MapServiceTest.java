package pt.isep.labdsof.events.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import pt.isep.labdsof.events.application.AccessAudit.Refusal;
import pt.isep.labdsof.events.application.InMemoryPorts.Refused;
import pt.isep.labdsof.events.application.InMemoryPorts.Uploaded;
import pt.isep.labdsof.events.domain.DateRange;
import pt.isep.labdsof.events.domain.Event;
import pt.isep.labdsof.events.domain.EventStatus;
import pt.isep.labdsof.events.domain.InvalidMapFileException;
import pt.isep.labdsof.events.domain.MapFileTooLargeException;
import pt.isep.labdsof.events.domain.MapPosition;
import pt.isep.labdsof.events.domain.MapSourceType;
import pt.isep.labdsof.events.domain.Membership;
import pt.isep.labdsof.events.domain.Membership.Role;
import pt.isep.labdsof.events.domain.Membership.Status;
import pt.isep.labdsof.events.domain.Permission;
import pt.isep.labdsof.events.domain.PoiType;
import pt.isep.labdsof.events.domain.PointOfInterest;
import pt.isep.labdsof.events.domain.Team;
import pt.isep.labdsof.events.domain.TeamPermission;
import pt.isep.labdsof.events.domain.VenueMapVersion;

/** US-19: uploading a venue map, with the ports faked (spec §4 F2). */
class MapServiceTest {

  private static final Instant NOW = Instant.parse("2027-04-30T10:15:30Z");
  private static final DateRange DATES = new DateRange(LocalDate.of(2027, 5, 2), LocalDate.of(2027, 5, 9));
  private static final byte[] PNG = HexFormat.of().parseHex("89504E470D0A1A0A0000000D49484452");

  private final InMemoryPorts ports = new InMemoryPorts();
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  private final MapService service = new MapService(new EventReader(ports, ports, ports, ports, clock), ports,
      ports, ports, ports, ports, clock);
  private final UUID admin = UUID.randomUUID();

  private Event event(EventStatus status) {
    var event = new Event(UUID.randomUUID(), "Queima das Fitas 2027", "FAP", DATES, "Semana academica", status,
        status == EventStatus.Draft ? null : "CODE", 1);
    ports.events.put(event.eventId(), event);
    return event;
  }

  private void member(Event event, UUID user, Role role, Status status, Set<UUID> teamIds) {
    ports.upsertIfNewer(new Membership(event.eventId(), user, role, status, teamIds, 1));
  }

  private Event eventOfAdmin(EventStatus status) {
    var event = event(status);
    member(event, admin, Role.EventAdmin, Status.Active, Set.of());
    return event;
  }

  /** A live team of the event with those permissions, and a member of it. */
  private UUID teamMember(Event event, Permission... permissions) {
    var team = Team.created(UUID.randomUUID(), event.eventId(), "Logistica " + UUID.randomUUID())
        .withPermissions(List.of(permissions).stream().map(p -> new TeamPermission(p, Set.of())).toList());
    ports.teams.put(team.teamId(), team);
    var user = UUID.randomUUID();
    member(event, user, Role.RegularUser, Status.Active, Set.of(team.teamId()));
    return user;
  }

  private PointOfInterest point(Event event, int mapVersion, String name, boolean deleted) {
    var stored = new PointOfInterest(UUID.randomUUID(), event.eventId(), mapVersion, PoiType.FirstAid, name,
        new MapPosition(0.25, 0.5), deleted, 1);
    ports.points.add(stored);
    return stored;
  }

  private void assertNothingStored() {
    assertThat(ports.mapVersions).isEmpty();
    assertThat(ports.published).isEmpty();
    assertThat(ports.uploads).isEmpty();
    assertThat(ports.files).isEmpty();
  }

  @Test
  @DisplayName("US-19 AC-1 an upload becomes the next version, with its file, its event and its log line")
  void anUploadBecomesTheNextVersion() {
    var event = eventOfAdmin(EventStatus.Published);

    var version = service.upload(admin, event.eventId(), PNG);

    assertThat(version.version()).isEqualTo(1);
    assertThat(version.sourceType()).isEqualTo(MapSourceType.PNG);
    assertThat(version.sourceSizeBytes()).isEqualTo(PNG.length);
    assertThat(version.uploadedAt()).isEqualTo(NOW);
    assertThat(version.uploadedBy()).isEqualTo(admin);
    assertThat(version.pointsOfInterest()).isEmpty();
    assertThat(ports.mapVersions).containsExactly(VenueMapVersion.uploaded(event.eventId(), 1, MapSourceType.PNG,
        PNG.length, NOW, admin));
    assertThat(ports.rendered).containsExactly(MapSourceType.PNG);
    assertThat(ports.files).containsOnlyKeys(event.eventId() + "/1.png");
    assertThat(ports.files.get(event.eventId() + "/1.png"))
        .isEqualTo(("png of image/png (" + PNG.length + " bytes)").getBytes(StandardCharsets.UTF_8));
    assertThat(ports.uploads).containsExactly(new Uploaded(admin, event.eventId(), NOW, 1, MapSourceType.PNG,
        PNG.length));
    assertThat(ports.published).singleElement().satisfies(published -> {
      assertThat(published.type()).isEqualTo("VenueMapVersionPublished");
      assertThat(published.routingKey()).isEqualTo("venue-map-version.published");
      assertThat(published.eventId()).isEqualTo(event.eventId());
      assertThat(published.actorUserId()).isEqualTo(admin.toString());
      // The envelope's payload is a Map.copyOf, whose iteration order is not the insertion one
      assertThat(published.payload())
          .containsExactlyInAnyOrderEntriesOf(Map.of("mapVersion", 1, "publishedAt", NOW.toString()));
    });
  }

  @Test
  @DisplayName("US-19 AC-1 a member with Map.Edit uploads, and each upload is the next version of the event")
  void mapEditUploadsAndVersionsFollowEachOther() {
    var event = eventOfAdmin(EventStatus.Published);
    var pedro = teamMember(event, Permission.MAP_EDIT);

    assertThat(service.upload(pedro, event.eventId(), PNG).version()).isEqualTo(1);
    assertThat(service.upload(admin, event.eventId(), PNG).version()).isEqualTo(2);

    assertThat(ports.mapVersions).extracting(VenueMapVersion::version).containsExactly(1, 2);
    assertThat(ports.files).containsOnlyKeys(event.eventId() + "/1.png", event.eventId() + "/2.png");
    assertThat(ports.published).hasSize(2);
  }

  @Test
  @DisplayName("US-19 AC-1 the answer carries the copied points, and the deleted ones are not copied")
  void theAnswerCarriesTheCopiedPoints() {
    var event = eventOfAdmin(EventStatus.Published);
    service.upload(admin, event.eventId(), PNG);
    var live = point(event, 1, "Posto 1", false);
    point(event, 1, "Posto fechado", true);

    var version = service.upload(admin, event.eventId(), PNG);

    assertThat(version.pointsOfInterest()).singleElement().satisfies(copy -> {
      assertThat(copy.name()).isEqualTo("Posto 1");
      assertThat(copy.type()).isEqualTo(live.type());
      assertThat(copy.position()).isEqualTo(live.position());
      assertThat(copy.mapVersion()).isEqualTo(2);
      assertThat(copy.version()).isEqualTo(1);
      assertThat(copy.poiId()).isNotEqualTo(live.poiId());
    });
    assertThat(ports.pointWrites).last().satisfies(write -> {
      assertThat(write.points()).isEqualTo(1);
      assertThat(write.actor()).isEqualTo(admin);
      assertThat(write.at()).isEqualTo(NOW);
    });
  }

  @Test
  @DisplayName("US-19 AC-1 the first version of an event starts with no points")
  void theFirstVersionStartsEmpty() {
    var event = eventOfAdmin(EventStatus.Draft);

    assertThat(service.upload(admin, event.eventId(), PNG).pointsOfInterest()).isEmpty();
    assertThat(ports.points).isEmpty();
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @CsvSource(value = {"hex:3C7376673E", "hex:504B0304", "Queima das Fitas", "hex:"}, delimiter = ';')
  @DisplayName("US-19 AC-2 a file that is not a map is refused and nothing is stored")
  void aFileThatIsNotAMapIsRefused(String content) {
    var event = eventOfAdmin(EventStatus.Published);
    var bytes = content.startsWith("hex:")
        ? HexFormat.of().parseHex(content.substring(4))
        : content.getBytes(StandardCharsets.UTF_8);

    assertThatThrownBy(() -> service.upload(admin, event.eventId(), bytes))
        .isInstanceOf(InvalidMapFileException.class);
    assertNothingStored();
    assertThat(ports.rendered).isEmpty();
  }

  @Test
  @DisplayName("US-19 AC-3 a file above 20 MB is refused before it is rendered, and nothing is stored")
  void aFileAboveTheLimitIsRefused() {
    var event = eventOfAdmin(EventStatus.Published);
    var tooBig = new byte[(int) VenueMapVersion.MAX_SOURCE_SIZE_BYTES + 1];
    System.arraycopy(PNG, 0, tooBig, 0, PNG.length);

    assertThatThrownBy(() -> service.upload(admin, event.eventId(), tooBig))
        .isInstanceOf(MapFileTooLargeException.class);
    assertNothingStored();
    assertThat(ports.rendered).isEmpty();
  }

  @Test
  @DisplayName("US-19 AC-1 a member without Map.Edit may not upload")
  void aMemberWithoutMapEditMayNotUpload() {
    var event = eventOfAdmin(EventStatus.Published);
    var pedro = teamMember(event, Permission.HELP_REQUESTS_TRIAGE);
    var rita = UUID.randomUUID();
    member(event, rita, Role.RegularUser, Status.Active, Set.of());

    assertThatThrownBy(() -> service.upload(pedro, event.eventId(), PNG)).isInstanceOf(ForbiddenException.class);
    assertThatThrownBy(() -> service.upload(rita, event.eventId(), PNG)).isInstanceOf(ForbiddenException.class);
    assertNothingStored();
    assertThat(ports.refusals).isEmpty();
  }

  @Test
  @DisplayName("US-19 AC-1 a team with Map.Edit grants it only to its own members")
  void aTeamGrantsOnlyItsOwnMembers() {
    var event = eventOfAdmin(EventStatus.Published);
    teamMember(event, Permission.MAP_EDIT); // the event has a team that may upload
    var rita = UUID.randomUUID();
    member(event, rita, Role.RegularUser, Status.Active, Set.of()); // she is not in it

    assertThatThrownBy(() -> service.upload(rita, event.eventId(), PNG)).isInstanceOf(ForbiddenException.class);
    assertNothingStored();
  }

  @Test
  @DisplayName("US-19 AC-1 a deleted team grants Map.Edit to nobody")
  void aDeletedTeamGrantsNothing() {
    var event = eventOfAdmin(EventStatus.Published);
    var pedro = teamMember(event, Permission.MAP_EDIT);
    ports.teams.replaceAll((id, team) -> team.asDeleted());

    assertThatThrownBy(() -> service.upload(pedro, event.eventId(), PNG)).isInstanceOf(ForbiddenException.class);
    assertNothingStored();
  }

  @Test
  @DisplayName("US-19 AC-1 a RegularUser cannot see a draft, so uploading to it is not found")
  void aRegularUserCannotSeeADraft() {
    var event = event(EventStatus.Draft);
    var pedro = teamMember(event, Permission.MAP_EDIT);

    assertThatThrownBy(() -> service.upload(pedro, event.eventId(), PNG))
        .isInstanceOf(EventNotFoundException.class);
    assertThat(ports.refusals).containsExactly(new Refused(pedro, event.eventId(), NOW, Refusal.NOT_VISIBLE));
    assertNothingStored();
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(value = EventStatus.class, names = {"Suspended", "Closed"})
  @DisplayName("US-19 AC-1 a suspended or closed event takes no map")
  void aSuspendedOrClosedEventTakesNoMap(EventStatus status) {
    var event = eventOfAdmin(status);

    assertThatThrownBy(() -> service.upload(admin, event.eventId(), PNG))
        .isInstanceOf(EventNotOpenException.class);
    assertNothingStored();
  }

  @Test
  @DisplayName("US-19 AC-1 every refusal is the same not-found and is recorded")
  void everyRefusalIsTheSameNotFoundAndIsRecorded() {
    var event = eventOfAdmin(EventStatus.Published);
    var stranger = UUID.randomUUID();
    var removed = UUID.randomUUID();
    member(event, removed, Role.EventAdmin, Status.Removed, Set.of());
    var otherEvent = event(EventStatus.Published);
    var otherAdmin = UUID.randomUUID();
    member(otherEvent, otherAdmin, Role.EventAdmin, Status.Active, Set.of());

    assertThatThrownBy(() -> service.upload(stranger, event.eventId(), PNG))
        .isInstanceOf(EventNotFoundException.class);
    assertThatThrownBy(() -> service.upload(removed, event.eventId(), PNG))
        .isInstanceOf(EventNotFoundException.class);
    assertThatThrownBy(() -> service.upload(otherAdmin, event.eventId(), PNG))
        .isInstanceOf(EventNotFoundException.class);
    assertThatThrownBy(() -> service.upload(admin, UUID.randomUUID(), PNG))
        .isInstanceOf(EventNotFoundException.class);

    assertThat(ports.refusals).extracting(Refused::refusal).containsExactly(Refusal.NOT_A_MEMBER,
        Refusal.NOT_A_MEMBER, Refusal.NOT_A_MEMBER, Refusal.NO_SUCH_EVENT);
    assertNothingStored();
  }

  @Test
  @DisplayName("US-19 AC-1 an upload that cannot be written stores nothing")
  void anUploadThatCannotBeWrittenStoresNothing() {
    var event = eventOfAdmin(EventStatus.Published);
    ports.fileWriteFails = true;

    assertThatThrownBy(() -> service.upload(admin, event.eventId(), PNG))
        .isInstanceOf(UncheckedIOException.class);
    assertNothingStored();
    assertThat(ports.deletedFiles).isEmpty();
  }

  @Test
  @DisplayName("US-19 AC-1 a failed upload leaves no file behind")
  void aFailedUploadLeavesNoFileBehind() {
    var event = eventOfAdmin(EventStatus.Published);
    ports.mapStoreFails = true;

    assertThatThrownBy(() -> service.upload(admin, event.eventId(), PNG))
        .isInstanceOf(IllegalStateException.class);
    assertThat(ports.deletedFiles).containsExactly(event.eventId() + "/1.png");
    assertNothingStored();
  }
}
