package pt.isep.labdsof.reports;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** AC-8: the documentation follows the change, and the spec's test names are real (SDD rule R5). */
class DocumentationIT {

  static final Path ROOT = Path.of("../..").toAbsolutePath().normalize();
  static final Path SPEC = ROOT.resolve("Docs/specs/194-reports-postgres-outbox.md");

  @Test
  @DisplayName("194 AC-8 the changelog, the documentation index and local delivery mention #194")
  void documentsUpdated() throws Exception {
    assertThat(Files.readString(ROOT.resolve("CHANGELOG.md")))
        .contains("Reports on PostgreSQL with an outbox (#194, Bruno Lopes)");
    assertThat(Files.readString(ROOT.resolve("Docs/README.md"))).contains("from #194, Reports");
    assertThat(Files.readString(ROOT.resolve("Docs/Technical/local-delivery.md")))
        .contains("#194").doesNotContain("Reports still has the `demo` profile");
    assertThat(SPEC).exists();
  }

  @Test
  @DisplayName("194 AC-8 every test the spec names exists, and every AC has a test")
  void specNamesRealTests() throws Exception {
    specNamesRealTests(SPEC, "194", 20, 8);
  }

  @Test
  @DisplayName("US-26 the changelog has the story, every test its spec names exists, and every AC has a test")
  void us26SpecNamesRealTests() throws Exception {
    assertThat(Files.readString(ROOT.resolve("CHANGELOG.md")))
        .contains("Live list of help requests (US-26, #73, Bruno Lopes)");
    specNamesRealTests(ROOT.resolve("Docs/specs/US-26-live-help-request-list.md"), "US-26", 15, 2);
  }

  static void specNamesRealTests(Path spec, String prefix, int atLeast, int criteria) throws Exception {
    String tests;
    try (var files = Files.walk(Path.of("src/test/java"))) {
      tests = files.filter(f -> f.toString().endsWith(".java")).map(f -> {
        try {
          return Files.readString(f);
        } catch (java.io.IOException e) {
          throw new IllegalStateException(e);
        }
      }).collect(Collectors.joining("\n"));
    }
    var named = Pattern.compile("`(" + prefix + " AC-\\d [^`]+)`").matcher(Files.readString(spec));
    int count = 0;
    while (named.find()) {
      assertThat(tests).as(named.group(1)).contains("\"" + named.group(1) + "\"");
      count++;
    }
    assertThat(count).isGreaterThanOrEqualTo(atLeast);
    for (int ac = 1; ac <= criteria; ac++) {
      assertThat(tests).as("AC-" + ac).contains("@DisplayName(\"" + prefix + " AC-" + ac + " ");
    }
  }
}
