package pt.isep.labdsof.events;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StatusEndpointIT extends IntegrationTest {

  @Test
  @DisplayName("192 AC-1 the service starts on its migrated database and answers its status")
  void contextStartsAndStatusAnswers() {
    var response = new ApiClient(port).get("/api/v1/status", null);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
        type -> assertThat(type).startsWith("application/json"));
    assertThat(response.body()).isEqualTo("{\"service\":\"events\",\"status\":\"ok\"}");
  }
}
