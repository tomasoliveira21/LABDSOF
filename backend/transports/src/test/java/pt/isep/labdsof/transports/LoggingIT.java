package pt.isep.labdsof.transports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** Correlation id (spec AC-7, D9) and what reaches the logs (M-10), read from the structured console output. */
@ExtendWith(OutputCaptureExtension.class)
class LoggingIT extends IntegrationTest {

  private static final String HEADER = "X-Correlation-Id";

  private ApiClient api() {
    return new ApiClient(port);
  }

  private HttpResponse<String> get(String path, String token, String correlationId) {
    return api().send("GET", path, token == null ? null : "Bearer " + token, null,
        correlationId == null ? new String[0] : new String[] {HEADER, correlationId});
  }

  private static String busObservations() {
    return "/api/v1/events/" + UUID.randomUUID() + "/bus-observations";
  }

  private static boolean accessLine(Map<String, Object> line, String correlationId) {
    return correlationId.equals(line.get("correlationId")) && line.get("message") instanceof String message
        && message.contains(" -> ") && message.contains(" in ");
  }

  /** The access line is written after the response may have reached the client, so wait for it. */
  private static void awaitAccessLine(CapturedOutput output, String correlationId) {
    await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
        assertThat(logLines(output)).anyMatch(line -> accessLine(line, correlationId)));
  }

  private static Stream<Arguments> correlationIds() {
    return Stream.of(Arguments.of("abcdefgh-1234-5678", true), Arguments.of(null, false),
        Arguments.of("abcd efgh", false), Arguments.of("abcdefg", false), Arguments.of("a".repeat(65), false));
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("correlationIds")
  @DisplayName("193 AC-7 a valid X-Correlation-Id is returned as sent, any other gets a new UUID")
  void correlationIdIsKeptOrReplaced(String sent, boolean kept) {
    var response = get("/api/v1/status", null, sent);

    var returned = response.headers().firstValue(HEADER).orElseThrow();
    if (kept) {
      assertThat(returned).isEqualTo(sent);
    } else {
      assertThat(returned).isNotEqualTo(sent);
      assertThat(UUID.fromString(returned)).hasToString(returned);
    }
  }

  @Test
  @DisplayName("193 AC-7 a 401 of the security layer carries the correlation id")
  void securityRefusalCarriesTheCorrelationId() {
    var path = busObservations();

    var response = get(path, null, "from-the-client-401");

    assertThat(response.statusCode()).isEqualTo(401);
    assertThat(response.headers().allValues(HEADER)).containsExactly("from-the-client-401");
    var generated = get(path, null, null);
    assertThat(generated.statusCode()).isEqualTo(401);
    assertThat(generated.headers().firstValue(HEADER)).isPresent();
  }

  @Test
  @DisplayName("193 AC-7 every request is logged as logstash JSON carrying its correlation id")
  void requestsAreLoggedAsJsonWithTheCorrelationId(CapturedOutput output) {
    var correlationId = "logged-" + UUID.randomUUID();
    var path = busObservations();

    var response = get(path, JWKS.valid(UUID.randomUUID().toString()), correlationId);

    awaitAccessLine(output, correlationId);
    var mine = logLines(output).stream().filter(line -> correlationId.equals(line.get("correlationId"))).toList();
    assertThat(mine).allSatisfy(line -> assertThat(line).containsKeys("@timestamp", "level", "logger_name",
        "message"));
    assertThat(mine).anyMatch(line -> String.valueOf(line.get("message"))
        .startsWith("GET " + path + " -> " + response.statusCode() + " in "));
  }

  @Test
  @DisplayName("193 AC-7 no token, e-mail or name reaches the logs")
  void noPersonalDataReachesTheLogs(CapturedOutput output) {
    var id = UUID.randomUUID();
    var email = "leak-" + id + "@farol.test";
    var name = "Leaky Name " + id;
    var valid = JWKS.sign(JWKS.claims(UUID.randomUUID().toString()).claim("email", email).claim("name", name)
        .build());
    var expired = JWKS.sign(JWKS.claims(UUID.randomUUID().toString()).claim("email", email).claim("name", name)
        .expirationTime(Date.from(Instant.now().minusSeconds(120))).build());
    var tampered = Jwks.tampered(JWKS.sign(JWKS.claims(UUID.randomUUID().toString()).claim("email", email)
        .claim("name", name).build()));

    for (var token : List.of(valid, expired, tampered)) {
      var correlationId = "leak-" + UUID.randomUUID();
      get(busObservations(), token, correlationId);
      awaitAccessLine(output, correlationId);
    }

    var logged = output.getAll();
    for (var secret : List.of(email, name, valid, expired, tampered, "Leaky Name", valid.split("\\.")[1])) {
      assertThat(logged).doesNotContain(secret);
    }
  }

  @Test
  @DisplayName("193 AC-7 a rejected correlation id is replaced and not logged")
  void rejectedCorrelationIdIsNotLogged(CapturedOutput output) {
    var rejected = "reject me " + UUID.randomUUID();

    var response = get(busObservations(), null, rejected);

    var replaced = response.headers().firstValue(HEADER).orElseThrow();
    awaitAccessLine(output, replaced);
    assertThat(output.getAll()).doesNotContain(rejected).doesNotContain(rejected.substring(10));
  }
}
