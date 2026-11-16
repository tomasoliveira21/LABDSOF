package pt.isep.labdsof.reports;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** AC-5: the synthetic in-memory demo of Sprint A is gone (spec D16). */
class DemoRemovedIT extends IntegrationTest {

  @Test
  @DisplayName("194 AC-5 the demo routes no longer exist, even for an authenticated user")
  void demoRoutesAreGone() throws Exception {
    var token = JWKS.valid("anyone");
    assertThat(post("/api/v1/demo/help-requests", token,
        "{\"category\":\"Health\",\"description\":\"Synthetic exercise.\"}").statusCode()).isEqualTo(404);
    assertThat(get("/api/v1/demo/help-requests/" + UUID.randomUUID(), token).statusCode()).isEqualTo(404);
  }

  @Test
  @DisplayName("194 AC-5 the demo profile brings nothing back")
  void demoProfileIsInert() throws Exception {
    try (var demo = Infrastructure.startReports(Map.of("spring.profiles.active", "demo"))) {
      assertThat(demo.getBeanDefinitionNames()).noneMatch(name -> name.toLowerCase().contains("demo"));
    }
    assertThat(Files.readString(Path.of("contracts/openapi.yaml"))).doesNotContain("/demo/");
  }
}
