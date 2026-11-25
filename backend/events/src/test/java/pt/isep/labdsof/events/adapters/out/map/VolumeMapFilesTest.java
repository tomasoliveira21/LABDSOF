package pt.isep.labdsof.events.adapters.out.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pt.isep.labdsof.events.domain.VenueMapVersion;

/** The map images on the service's volume (US-19 D9). */
class VolumeMapFilesTest {

  private static final byte[] PNG = "a map".getBytes(StandardCharsets.UTF_8);

  @TempDir Path volume;

  private VolumeMapFiles files() {
    return new VolumeMapFiles(volume);
  }

  @Test
  @DisplayName("US-19 AC-1 a map is written under the event's folder and read back as it was")
  void aMapIsWrittenAndReadBack() throws IOException {
    var file = VenueMapVersion.imageFileOf(UUID.randomUUID(), 2);

    files().write(file, PNG);

    assertThat(Files.readAllBytes(volume.resolve(file))).isEqualTo(PNG);
  }

  @Test
  @DisplayName("US-19 AC-1 writing the same version again replaces the file, leaving nothing of the old one")
  void writingAgainReplacesTheFile() throws IOException {
    var file = VenueMapVersion.imageFileOf(UUID.randomUUID(), 1);
    files().write(file, "a much longer first map".getBytes(StandardCharsets.UTF_8));

    files().write(file, PNG);

    assertThat(Files.readAllBytes(volume.resolve(file))).isEqualTo(PNG);
  }

  @Test
  @DisplayName("US-19 AC-1 a failed upload's file is deleted, and deleting what is not there changes nothing")
  void aFileIsDeleted() {
    var file = VenueMapVersion.imageFileOf(UUID.randomUUID(), 1);
    files().write(file, PNG);

    files().delete(file);
    files().delete(file);

    assertThat(volume.resolve(file)).doesNotExist();
  }

  @Test
  @DisplayName("US-19 AC-1 a delete that cannot be done is logged, never thrown: the row was not stored anyway")
  void aDeleteThatFailsIsSwallowed() {
    var directory = VenueMapVersion.imageFileOf(UUID.randomUUID(), 1);
    files().write(directory + "/inside.png", PNG); // the name to delete is now a non-empty directory

    files().delete(directory);

    assertThat(volume.resolve(directory)).exists();
  }

  @Test
  @DisplayName("US-19 AC-1 a volume that cannot be written fails the upload instead of losing the map")
  void aVolumeThatCannotBeWrittenFailsTheUpload() throws IOException {
    var file = VenueMapVersion.imageFileOf(UUID.randomUUID(), 1);
    Files.createFile(volume.resolve("blocked")); // a file where the event's folder would go

    assertThatThrownBy(() -> files().write("blocked/" + file, PNG))
        .isInstanceOf(UncheckedIOException.class).hasMessageContaining("could not be written");
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @ValueSource(strings = {"../outside.png", "a/../../outside.png"})
  @DisplayName("US-19 AC-1 a name that would leave the volume is refused")
  void aNameThatLeavesTheVolumeIsRefused(String file) {
    assertThatThrownBy(() -> files().write(file, PNG))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("inside the map directory");
    assertThatThrownBy(() -> files().delete(file)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("US-19 AC-1 a relative map directory is resolved once, so every file lands in the same place")
  void aRelativeDirectoryIsResolved() {
    var directory = Path.of("target/map-directory-" + UUID.randomUUID());
    var relative = new VolumeMapFiles(directory);
    var file = VenueMapVersion.imageFileOf(UUID.randomUUID(), 1);

    relative.write(file, PNG);

    assertThat(directory.resolve(file).toAbsolutePath().normalize()).exists();
    relative.delete(file);
    assertThat(directory.resolve(file)).doesNotExist();
  }
}
