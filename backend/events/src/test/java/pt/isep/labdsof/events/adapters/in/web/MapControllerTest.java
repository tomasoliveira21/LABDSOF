package pt.isep.labdsof.events.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import pt.isep.labdsof.events.domain.InvalidMapFileException;

/**
 * The part of the controller a request cannot exercise: a file part the container accepted but cannot read back
 * (a lost temporary file, a full disk), which is the same {@code 400} as any other file that is not a map.
 */
class MapControllerTest {

  @Test
  @DisplayName("US-19 AC-1 the bytes of the file part are what the use case gets")
  void theBytesOfThePartAreRead() {
    var file = new MockMultipartFile("file", "map.png", "image/png", new byte[] {1, 2, 3});

    assertThat(MapController.content(file)).containsExactly(1, 2, 3);
  }

  @Test
  @DisplayName("US-19 AC-2 a file part that cannot be read is refused as a file that is not a map")
  void aPartThatCannotBeReadIsRefused() {
    var unreadable = new MockMultipartFile("file", "map.png", "image/png", new byte[0]) {
      @Override
      public byte[] getBytes() throws IOException {
        throw new IOException("the temporary file is gone");
      }
    };

    assertThatThrownBy(() -> MapController.content(unreadable))
        .isInstanceOf(InvalidMapFileException.class).hasMessage("the uploaded part could not be read");
  }
}
