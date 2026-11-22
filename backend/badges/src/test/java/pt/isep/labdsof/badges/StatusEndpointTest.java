package pt.isep.labdsof.badges;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import pt.isep.labdsof.badges.adapters.in.web.StatusController;

class StatusEndpointTest {

  @Test
  void identifiesTheBadgesService() {
    assertThat(new StatusController().status()).isEqualTo(new StatusController.ServiceStatus("badges", "ok"));
  }
}
