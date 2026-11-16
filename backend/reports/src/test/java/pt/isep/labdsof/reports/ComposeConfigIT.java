package pt.isep.labdsof.reports;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** AC-7: Reports in Compose and CI, configured only through environment variables (spec D18). */
class ComposeConfigIT {

  static final Path ROOT = Path.of("../..").toAbsolutePath().normalize();

  static String reportsService() throws Exception {
    var compose = Files.readString(ROOT.resolve("infra/compose.yaml"));
    var matcher = Pattern.compile("(?ms)^  reports:\\R(.*?)(?=^  \\S)").matcher(compose);
    assertThat(matcher.find()).isTrue();
    return matcher.group(1);
  }

  @Test
  @DisplayName("194 AC-7 Reports gets its database, broker and Keycloak from the environment, secrets from infra/.env")
  void composeWiring() throws Exception {
    var reports = reportsService();
    assertThat(reports)
        .contains("SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/reports")
        .contains("SPRING_DATASOURCE_USERNAME: reports")
        .contains("SPRING_DATASOURCE_PASSWORD: ${REPORTS_DB_PASSWORD:?")
        .contains("SPRING_RABBITMQ_HOST: rabbitmq")
        .contains("SPRING_RABBITMQ_USERNAME: reports")
        .contains("SPRING_RABBITMQ_PASSWORD: ${RABBITMQ_REPORTS_PASSWORD:?")
        .contains("FAROL_ISSUER_URI: http://${PUBLIC_HOST:-localhost}:${KEYCLOAK_PORT:-8180}/realms/farol")
        .contains("FAROL_JWKS_URI: http://keycloak:8080/realms/farol/protocol/openid-connect/certs")
        .contains("condition: service_healthy");
    // Every variable Reports uses is in infra/.env.example
    var example = Files.readString(ROOT.resolve("infra/.env.example"));
    var variables = Pattern.compile("\\$\\{([A-Z_]+)").matcher(reports);
    while (variables.find()) {
      assertThat(example).as(variables.group(1)).containsPattern("(?m)^" + variables.group(1) + "=");
    }
  }

  @Test
  @DisplayName("194 AC-7 no secret has a default in the Reports configuration")
  void noSecretInCode() throws Exception {
    var properties = Files.readString(Path.of("src/main/resources/application.properties"));
    assertThat(properties)
        .contains("spring.datasource.password=${SPRING_DATASOURCE_PASSWORD:}")
        .contains("spring.rabbitmq.password=${SPRING_RABBITMQ_PASSWORD:}")
        .doesNotContainPattern("(?im)^[^#]*password=(?!\\$\\{)");
  }

  @Test
  @DisplayName("194 AC-7 CI builds and tests Reports and runs its smoke test against the stack")
  void ciRunsReports() throws Exception {
    var ci = Files.readString(ROOT.resolve(".github/workflows/ci.yml"));
    assertThat(ci).contains("for module in auth-users events reports transports badges gateway")
        .contains("verify")
        .contains("python3 -I infra/tests/reports_smoke.py infra/.env.example");
    assertThat(ROOT.resolve("infra/tests/reports_smoke.py")).exists();
  }
}
