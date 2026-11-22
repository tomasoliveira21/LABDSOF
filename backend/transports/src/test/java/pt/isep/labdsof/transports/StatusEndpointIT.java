package pt.isep.labdsof.transports;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StatusEndpointIT extends IntegrationTest {

  @Test
  @DisplayName("193 AC-1 the service starts on its migrated database and answers its status")
  void contextStartsAndStatusAnswers() {
    var response = new ApiClient(port).get("/api/v1/status", null);

    assertThat(jdbc.sql("SELECT success FROM flyway_schema_history WHERE version = '1'")
        .query(Boolean.class).single()).isTrue();
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
        type -> assertThat(type).startsWith("application/json"));
    assertThat(response.body()).isEqualTo("{\"service\":\"transports\",\"status\":\"ok\"}");
  }
}
