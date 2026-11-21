package pt.isep.labdsof.reports;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Spec US-34 D3: the service sends what was evaluated. An *IT because it reads the repository's Docs/, which the
 * image build (unit tests only) does not have.
 */
class TriagePackageIT {

  static final Path PACKAGE = Path.of("../../Docs/Research/architecture-investigation/evaluation");

  @Test
  @DisplayName("229 AC-2 the prompt and schema are the evaluated package files")
  void evaluatedFiles() throws Exception {
    assertThat(sha256(Path.of("src/main/resources/triage/system-prompt.txt")))
        .isEqualTo(sha256(PACKAGE.resolve("prompts/v8.txt")));
    assertThat(sha256(Path.of("src/main/resources/triage/output.schema.json")))
        .isEqualTo(sha256(PACKAGE.resolve("output.schema.json")));
  }

  static String sha256(Path file) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
  }
}
