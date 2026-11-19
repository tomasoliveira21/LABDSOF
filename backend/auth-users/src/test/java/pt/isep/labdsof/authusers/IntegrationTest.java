package pt.isep.labdsof.authusers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** One Spring context on the shared containers, reused by every *IT that extends this class. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class IntegrationTest {

  @DynamicPropertySource
  static void infrastructure(DynamicPropertyRegistry registry) {
    Infrastructure.properties().forEach((key, value) -> registry.add(key, () -> value));
  }

  @LocalServerPort protected int port;
  @Autowired protected JdbcClient jdbc;

  protected static final Jwks JWKS = Infrastructure.JWKS;
}
