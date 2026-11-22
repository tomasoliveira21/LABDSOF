package pt.isep.labdsof.transports;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What infra/compose.yaml must keep for Transports and its simulator (spec D8). Reads the file and starts nothing; an *IT
 * because the image build has only this module, not infra/ (#214 D17).
 */
class ComposeConfigIT {

  private static final Path COMPOSE = Path.of("../../infra/compose.yaml");

  /** The lines of one service, from its name to the next top-level service. */
  private static String service(String name) throws IOException {
    var matcher = Pattern.compile("(?ms)^  " + name + ":\\R(.*?)(?=^  \\S|\\z)").matcher(Files.readString(COMPOSE));
    assertThat(matcher.find()).as("service " + name).isTrue();
    return matcher.group(1);
  }

  @Test
  @DisplayName("193 D8 WireMock keeps at most 1000 requests in its journal, so a stack left running does not run out "
      + "of memory")
  void wiremockJournalIsBounded() throws IOException {
    assertThat(service("wiremock")).contains("--max-request-journal-entries=1000");
  }
}
