package pt.isep.labdsof.events.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** One version of an event's venue map (US-19). */
class VenueMapVersionTest {

  private static final UUID EVENT = UUID.randomUUID();
  private static final UUID SOFIA = UUID.randomUUID();
  private static final Instant AT = Instant.parse("2027-04-30T10:15:30Z");

  private static VenueMapVersion uploaded(int version) {
    return VenueMapVersion.uploaded(EVENT, version, MapSourceType.PNG, 1024, AT, SOFIA);
  }

  @Test
  @DisplayName("US-19 AC-1 an upload is the next version of its event, with its file and no points yet")
  void anUploadIsTheNextVersion() {
    var version = uploaded(3);

    assertThat(version.version()).isEqualTo(3);
    assertThat(version.imageFile()).isEqualTo(EVENT + "/3.png");
    assertThat(version.sourceType()).isEqualTo(MapSourceType.PNG);
    assertThat(version.sourceSizeBytes()).isEqualTo(1024);
    assertThat(version.uploadedAt()).isEqualTo(AT);
    assertThat(version.uploadedBy()).isEqualTo(SOFIA);
    assertThat(version.pointsOfInterest()).isEmpty();
  }

  @Test
  @DisplayName("US-19 AC-1 the points a version starts with are the ones it is given, and nothing else changes")
  void theVersionCarriesItsPoints() {
    var point = new PointOfInterest(UUID.randomUUID(), EVENT, 3, PoiType.FirstAid, "Posto 2",
        new MapPosition(0.5, 0.25), false, 1);

    var version = uploaded(3).with(List.of(point));

    assertThat(version.pointsOfInterest()).containsExactly(point);
    assertThat(version).usingRecursiveComparison().ignoringFields("pointsOfInterest").isEqualTo(uploaded(3));
  }

  @Test
  @DisplayName("US-19 AC-1 the points of a version cannot be changed through the list it was given")
  void thePointsAreCopied() {
    var points = new java.util.ArrayList<PointOfInterest>();
    var version = uploaded(1).with(points);
    points.add(new PointOfInterest(UUID.randomUUID(), EVENT, 1, PoiType.Stage, "Palco", new MapPosition(0, 0),
        false, 1));

    assertThat(version.pointsOfInterest()).isEmpty();
  }

  @ParameterizedTest(name = "{0} bytes")
  @ValueSource(longs = {1, 1024, 20L * 1024 * 1024})
  @DisplayName("US-19 AC-3 a file up to 20 MB is accepted")
  void sizesUpToTheLimitAreAccepted(long size) {
    assertThat(VenueMapVersion.uploaded(EVENT, 1, MapSourceType.PDF, size, AT, SOFIA).sourceSizeBytes())
        .isEqualTo(size);
    VenueMapVersion.checkSourceSize(size);
  }

  @ParameterizedTest(name = "{0} bytes")
  @ValueSource(longs = {20L * 1024 * 1024 + 1, Long.MAX_VALUE})
  @DisplayName("US-19 AC-3 a file above 20 MB is refused, by the check and by the version itself")
  void sizesAboveTheLimitAreRefused(long size) {
    assertThatThrownBy(() -> VenueMapVersion.checkSourceSize(size))
        .isInstanceOf(MapFileTooLargeException.class).hasMessageContaining("20971520");
    assertThatThrownBy(() -> VenueMapVersion.uploaded(EVENT, 1, MapSourceType.PNG, size, AT, SOFIA))
        .isInstanceOf(MapFileTooLargeException.class);
  }

  @ParameterizedTest(name = "{0} bytes")
  @ValueSource(longs = {0, -1})
  @DisplayName("US-19 AC-2 an empty file is not a map")
  void anEmptyFileIsNotAMap(long size) {
    assertThatThrownBy(() -> VenueMapVersion.checkSourceSize(size))
        .isInstanceOf(InvalidMapFileException.class).hasMessage("empty file");
  }

  @ParameterizedTest(name = "version {0}")
  @ValueSource(ints = {0, -1})
  @DisplayName("US-19 AC-1 versions start at 1")
  void versionsStartAtOne(int version) {
    assertThatThrownBy(() -> uploaded(version))
        .isInstanceOf(IllegalArgumentException.class).hasMessage("version must be at least 1");
  }

  @ParameterizedTest(name = "[{index}]")
  @ValueSource(strings = {"", "   "})
  @DisplayName("US-19 AC-1 a version always names its file")
  void theFileIsNeverBlank(String file) {
    assertThatThrownBy(() -> new VenueMapVersion(EVENT, 1, file, MapSourceType.PNG, 1, AT, SOFIA, List.of()))
        .isInstanceOf(IllegalArgumentException.class).hasMessage("imageFile must not be blank");
  }

  @Test
  @DisplayName("US-19 AC-1 a version always names its event, its type, its time and who uploaded it")
  void theRequiredValuesAreRequired() {
    assertThatThrownBy(() -> new VenueMapVersion(null, 1, "f.png", MapSourceType.PNG, 1, AT, SOFIA, List.of()))
        .isInstanceOf(NullPointerException.class).hasMessage("eventId");
    assertThatThrownBy(() -> new VenueMapVersion(EVENT, 1, "f.png", null, 1, AT, SOFIA, List.of()))
        .isInstanceOf(NullPointerException.class).hasMessage("sourceType");
    assertThatThrownBy(() -> new VenueMapVersion(EVENT, 1, "f.png", MapSourceType.PNG, 1, null, SOFIA, List.of()))
        .isInstanceOf(NullPointerException.class).hasMessage("uploadedAt");
    assertThatThrownBy(() -> new VenueMapVersion(EVENT, 1, "f.png", MapSourceType.PNG, 1, AT, null, List.of()))
        .isInstanceOf(NullPointerException.class).hasMessage("uploadedBy");
    assertThatThrownBy(() -> new VenueMapVersion(EVENT, 1, null, MapSourceType.PNG, 1, AT, SOFIA, List.of()))
        .isInstanceOf(NullPointerException.class).hasMessage("imageFile");
  }

  @Test
  @DisplayName("US-19 AC-1 the file of a version is the event's folder and the version number")
  void theFileNameIsTheEventAndTheVersion() {
    assertThat(VenueMapVersion.imageFileOf(EVENT, 12)).isEqualTo(EVENT + "/12.png");
  }
}
