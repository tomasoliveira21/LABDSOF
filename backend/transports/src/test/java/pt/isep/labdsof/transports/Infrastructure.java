package pt.isep.labdsof.transports;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Real PostgreSQL for the *IT tests (spec D1, gate "Integration"), started once per JVM: the image of
 * infra/compose.yaml (same digest, read from the file) and the production Flyway migrations.
 */
public final class Infrastructure {

  public static final Path REPOSITORY = Path.of("../..").toAbsolutePath().normalize();

  public static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
      DockerImageName.parse(composeImage("postgres")).asCompatibleSubstituteFor("postgres"))
      .withDatabaseName("transports")
      .withUsername("transports")
      .withPassword("test-only-transports-db");

  static {
    POSTGRES.start();
  }

  private Infrastructure() {}

  /** Spring properties pointing at the containers; tests add their own on top. */
  public static Map<String, Object> properties() {
    var properties = new HashMap<String, Object>();
    properties.put("spring.datasource.url", POSTGRES.getJdbcUrl());
    properties.put("spring.datasource.username", POSTGRES.getUsername());
    properties.put("spring.datasource.password", POSTGRES.getPassword());
    return properties;
  }

  /**
   * A fresh Transports process on the same database (what a container restart does), with {@code overrides} on
   * top. Passed as arguments so they win over application.yaml.
   */
  public static ConfigurableApplicationContext startTransports(Map<String, Object> overrides) {
    var all = properties();
    all.put("server.port", "0");
    all.putAll(overrides);
    var args = all.entrySet().stream().map(e -> "--" + e.getKey() + "=" + e.getValue())
        .toArray(String[]::new);
    return new SpringApplicationBuilder(TransportsApplication.class).run(args);
  }

  /** The image of a Compose service, so the tests always run what the stack runs. */
  static String composeImage(String service) {
    try {
      var compose = Files.readString(REPOSITORY.resolve("infra/compose.yaml"));
      var matcher = Pattern.compile("(?m)^  " + service + ":\\R(?:    .*\\R)*?    image: (\\S+)")
          .matcher(compose);
      if (!matcher.find()) {
        throw new IllegalStateException("no image for " + service + " in infra/compose.yaml");
      }
      return matcher.group(1);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
