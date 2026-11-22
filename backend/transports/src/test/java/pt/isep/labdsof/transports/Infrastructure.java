package pt.isep.labdsof.transports;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Real PostgreSQL and RabbitMQ for the *IT tests (spec D1, gate "Integration"), started once per JVM: the images
 * of infra/compose.yaml (same digests, read from the file), the production Flyway migrations, and the broker set
 * up by the production infra/rabbitmq/setup.sh and topology.json. Transports connects as the least-privileged
 * {@code transports} user, exactly as in Compose.
 */
public final class Infrastructure {

  public static final Path REPOSITORY = Path.of("../..").toAbsolutePath().normalize();
  /** The password setup.sh gives the {@code transports} user in {@link #setUpBroker()}. */
  public static final String TRANSPORTS_AMQP_PASSWORD = "test-only-transports";

  public static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
      DockerImageName.parse(composeImage("postgres")).asCompatibleSubstituteFor("postgres"))
      .withDatabaseName("transports")
      .withUsername("transports")
      .withPassword("test-only-transports-db");

  public static final RabbitMQContainer RABBIT = new RabbitMQContainer(
      DockerImageName.parse(composeImage("rabbitmq")).asCompatibleSubstituteFor("rabbitmq"))
      .withCopyFileToContainer(MountableFile.forHostPath(REPOSITORY.resolve("infra/rabbitmq/setup.sh")),
          "/etc/farol/setup.sh")
      .withCopyFileToContainer(
          MountableFile.forHostPath(REPOSITORY.resolve("infra/rabbitmq/topology.json")),
          "/etc/farol/topology.json");

  /** The Porto Digital simulator with the recorded stubs of infra/wiremock (spec D8), as Compose runs it. */
  public static final GenericContainer<?> WIREMOCK = new GenericContainer<>(DockerImageName.parse(composeImage("wiremock")))
      .withCopyFileToContainer(MountableFile.forHostPath(REPOSITORY.resolve("infra/wiremock/mappings")),
          "/home/wiremock/mappings")
      .withCopyFileToContainer(MountableFile.forHostPath(REPOSITORY.resolve("infra/wiremock/__files")),
          "/home/wiremock/__files")
      .withExposedPorts(8080)
      .waitingFor(Wait.forHttp("/__admin/health").forStatusCode(200));

  public static final Jwks JWKS = new Jwks();

  static {
    POSTGRES.start();
    RABBIT.start();
    WIREMOCK.start();
    setUpBroker();
  }

  /** The base URL of the simulator, as Transports is configured with it. */
  public static String wiremockUrl() {
    return "http://" + WIREMOCK.getHost() + ":" + WIREMOCK.getMappedPort(8080);
  }

  private Infrastructure() {}

  /** Spring properties pointing at the containers; tests add their own on top. */
  public static Map<String, Object> properties() {
    var properties = new HashMap<String, Object>();
    properties.put("spring.datasource.url", POSTGRES.getJdbcUrl());
    properties.put("spring.datasource.username", POSTGRES.getUsername());
    properties.put("spring.datasource.password", POSTGRES.getPassword());
    properties.put("spring.rabbitmq.host", RABBIT.getHost());
    properties.put("spring.rabbitmq.port", RABBIT.getAmqpPort());
    properties.put("spring.rabbitmq.username", "transports");
    properties.put("spring.rabbitmq.password", TRANSPORTS_AMQP_PASSWORD);
    properties.put("spring.security.oauth2.resourceserver.jwt.issuer-uri", Jwks.ISSUER);
    properties.put("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", JWKS.uri());
    // Short backoff keeps the retry tests fast; production values are in application.yaml
    properties.put("spring.rabbitmq.listener.simple.retry.initial-interval", "50ms");
    properties.put("spring.rabbitmq.listener.simple.retry.max-interval", "200ms");
    // Only the tests of the scheduled read switch it on (SourceImportIT)
    properties.put("farol.transport.source.enabled", "false");
    return properties;
  }

  /**
   * A fresh Transports process on the same database and broker (what a container restart does), with
   * {@code overrides} on top. Passed as arguments so they win over application.yaml.
   */
  public static ConfigurableApplicationContext startTransports(Map<String, Object> overrides) {
    var all = properties();
    all.put("server.port", "0");
    all.putAll(overrides);
    var args = all.entrySet().stream().map(e -> "--" + e.getKey() + "=" + e.getValue())
        .toArray(String[]::new);
    return new SpringApplicationBuilder(TransportsApplication.class).run(args);
  }

  /** A template with the broker's admin, standing in for the producers and for the operator. */
  public static RabbitTemplate adminTemplate() {
    var factory = new CachingConnectionFactory(RABBIT.getHost(), RABBIT.getAmqpPort());
    factory.setUsername(RABBIT.getAdminUsername());
    factory.setPassword(RABBIT.getAdminPassword());
    return new RabbitTemplate(factory);
  }

  /** Runs {@code rabbitmqctl -q args...} in the broker container and returns its output. */
  public static String rabbitmqctl(String... args) {
    var command = Stream.concat(Stream.of("rabbitmqctl", "-q"), Stream.of(args)).toArray(String[]::new);
    try {
      var result = RABBIT.execInContainer(command);
      if (result.getExitCode() != 0) {
        throw new IllegalStateException("rabbitmqctl " + String.join(" ", args) + " failed: "
            + result.getStderr());
      }
      return result.getStdout();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  /** Re-run to restore or recreate the users exactly as setup.sh declares them; idempotent. */
  public static void setUpBroker() {
    try {
      var result = RABBIT.execInContainer("env",
          "RABBITMQADMIN_TARGET_HOST=localhost",
          "RABBITMQADMIN_USERNAME=" + RABBIT.getAdminUsername(),
          "RABBITMQADMIN_PASSWORD=" + RABBIT.getAdminPassword(),
          "RABBITMQ_AUTH_USERS_PASSWORD=test-only-auth-users",
          "RABBITMQ_EVENTS_PASSWORD=test-only-events",
          "RABBITMQ_REPORTS_PASSWORD=test-only-reports",
          "RABBITMQ_TRANSPORTS_PASSWORD=" + TRANSPORTS_AMQP_PASSWORD,
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
