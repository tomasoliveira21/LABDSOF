package pt.isep.labdsof.authusers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Real PostgreSQL and RabbitMQ for the *IT tests (spec §2, gate "Integration"), started once per JVM:
 * the images of infra/compose.yaml (same digests, read from the file), the production Flyway migrations,
 * and the broker set up by the production infra/rabbitmq/setup.sh and topology.json. Auth &amp; Users
 * connects as the least-privileged {@code auth-users} user, exactly as in Compose.
 */
public final class Infrastructure {

  public static final Path REPOSITORY = Path.of("../..").toAbsolutePath().normalize();
  public static final String AUTH_USERS_AMQP_PASSWORD = "test-only-auth-users-amqp";
  public static final String BOOTSTRAP_EMAIL = "admin@farol.test";

  public static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
      DockerImageName.parse(composeImage("postgres")).asCompatibleSubstituteFor("postgres"))
      .withDatabaseName("auth_users")
      .withUsername("auth_users")
      .withPassword("test-only-auth-users-db");

  public static final RabbitMQContainer RABBIT = new RabbitMQContainer(
      DockerImageName.parse(composeImage("rabbitmq")).asCompatibleSubstituteFor("rabbitmq"))
      .withCopyFileToContainer(MountableFile.forHostPath(REPOSITORY.resolve("infra/rabbitmq/setup.sh")),
          "/etc/farol/setup.sh")
      .withCopyFileToContainer(
          MountableFile.forHostPath(REPOSITORY.resolve("infra/rabbitmq/topology.json")),
          "/etc/farol/topology.json");

  public static final Jwks JWKS = new Jwks();

  static {
    POSTGRES.start();
    RABBIT.start();
    setUpBroker();
  }

  private Infrastructure() {}

  /** Spring properties pointing at the containers and the test JWKS; tests add their own on top. */
  public static Map<String, Object> properties() {
    var properties = new HashMap<String, Object>();
    properties.put("spring.datasource.url", POSTGRES.getJdbcUrl());
    properties.put("spring.datasource.username", POSTGRES.getUsername());
    properties.put("spring.datasource.password", POSTGRES.getPassword());
    properties.put("spring.rabbitmq.host", RABBIT.getHost());
    properties.put("spring.rabbitmq.port", RABBIT.getAmqpPort());
    properties.put("spring.rabbitmq.username", "auth-users");
    properties.put("spring.rabbitmq.password", AUTH_USERS_AMQP_PASSWORD);
    properties.put("spring.security.oauth2.resourceserver.jwt.issuer-uri", Jwks.ISSUER);
    properties.put("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", JWKS.uri());
    properties.put("farol.bootstrap-farol-admin-email", BOOTSTRAP_EMAIL);
    return properties;
  }

  private static void setUpBroker() {
    try {
      var result = RABBIT.execInContainer("env",
          "RABBITMQADMIN_TARGET_HOST=localhost",
          "RABBITMQADMIN_USERNAME=" + RABBIT.getAdminUsername(),
          "RABBITMQADMIN_PASSWORD=" + RABBIT.getAdminPassword(),
          "RABBITMQ_AUTH_USERS_PASSWORD=" + AUTH_USERS_AMQP_PASSWORD,
          "RABBITMQ_EVENTS_PASSWORD=test-only-events",
          "RABBITMQ_REPORTS_PASSWORD=test-only-reports",
          "RABBITMQ_TRANSPORTS_PASSWORD=test-only-transports",
          "RABBITMQ_BADGES_PASSWORD=test-only-badges",
          "sh", "/etc/farol/setup.sh");
      if (result.getExitCode() != 0) {
        throw new IllegalStateException("rabbitmq-setup failed: " + result.getStdout() + result.getStderr());
      }
    } catch (IOException e) {
      throw new IllegalStateException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
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
