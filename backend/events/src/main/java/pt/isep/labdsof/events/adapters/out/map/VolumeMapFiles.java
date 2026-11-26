package pt.isep.labdsof.events.adapters.out.map;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pt.isep.labdsof.events.application.MapFiles;

/**
 * The map images on a volume of the service, with only their names in the database (API outline D6, US-19 D9). The
 * directory is {@code farol.map.directory}; in the local deployment it is a named volume of the Events container
 * (ADR-011).
 *
 * <p>The name always comes from {@code VenueMapVersion.imageFileOf} — an event id and a version number — so no
 * part of it is ever the caller's input, and it is resolved inside the directory and checked to still be inside
 * it, so a future caller-made name cannot walk out of the volume either.
 */
@Component
class VolumeMapFiles implements MapFiles {

  private static final Logger log = LoggerFactory.getLogger(VolumeMapFiles.class);

  private final Path directory;

  VolumeMapFiles(@Value("${farol.map.directory}") Path directory) {
    this.directory = directory.toAbsolutePath().normalize();
  }

  @Override
  public void write(String file, byte[] png) {
    var path = resolved(file);
    try {
      Files.createDirectories(path.getParent());
      Files.write(path, png, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
          StandardOpenOption.WRITE);
    } catch (IOException e) {
      throw new UncheckedIOException("the map image could not be written", e);
    }
  }

  @Override
  public void delete(String file) {
    var path = resolved(file); // outside the try: a name that is not a map file of this service is a bug, not IO
    try {
      Files.deleteIfExists(path);
    } catch (IOException | RuntimeException e) {
      // The row was not stored, so nothing reads this file; losing the cleanup is not worth failing the answer
      log.warn("Could not delete the map image of a failed upload", e);
    }
  }

  private Path resolved(String file) {
    var path = directory.resolve(file).toAbsolutePath().normalize();
    if (!path.startsWith(directory)) {
      throw new IllegalArgumentException("a map file is always inside the map directory");
    }
    return path;
  }
}
