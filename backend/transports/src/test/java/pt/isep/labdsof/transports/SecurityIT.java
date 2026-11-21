package pt.isep.labdsof.transports;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Authentication of every request (spec AC-2, D10), over real HTTP with signed tokens. */
class SecurityIT extends IntegrationTest {

  private ApiClient api() {
    return new ApiClient(port);
  }

  private static String user() {
    return UUID.randomUUID().toString();
  }

  /** A 401 Problem Details with a Bearer challenge that repeats none of the given texts. */
  static void assertRefused(HttpResponse<String> response, String... mustNotContain) {
    ApiClient.assertProblem(response, 401, mustNotContain);
    assertThat(response.headers().firstValue("WWW-Authenticate")).hasValueSatisfying(
        challenge -> assertThat(challenge).startsWith("Bearer"));
  }

  /** The token passed the security layer: whatever the operation answers, it is not a refusal of the token. */
  static void assertAccepted(HttpResponse<String> response) {
    assertThat(response.statusCode()).isNotIn(401, 403);
  }

  private static String busObservations() {
    return "/api/v1/events/" + UUID.randomUUID() + "/bus-observations";
  }

  @Test
  @DisplayName("193 AC-2 a request without a token gets 401 Problem Details with WWW-Authenticate Bearer")
  void noTokenIsRefused() {
    assertRefused(api().get(busObservations(), null));
  }

  @Test
  @DisplayName("193 AC-2 a valid token is accepted")
  void validTokenIsAccepted() {
    assertAccepted(api().get(busObservations(), JWKS.valid(user())));
  }

  @Test
  @DisplayName("193 AC-2 a token expired more than 60 s ago gets 401")
  void expiredTokenIsRefused() {
    var token = JWKS.expired(user(), Duration.ofSeconds(120));

    assertRefused(api().get(busObservations(), token), token);
  }

  @Test
  @DisplayName("193 AC-2 a token expired less than 60 s ago is still accepted (clock skew)")
  void slightlyExpiredTokenIsAccepted() {
    assertAccepted(api().get(busObservations(), JWKS.expired(user(), Duration.ofSeconds(30))));
  }

  @Test
  @DisplayName("193 AC-2 a token whose payload was changed after signing gets 401")
  void tamperedTokenIsRefused() {
    var token = Jwks.tampered(JWKS.valid(user()));

    assertRefused(api().get(busObservations(), token), token);
  }

  @Test
  @DisplayName("193 AC-2 a token signed by another key under the same key id gets 401")
  void wrongSignatureIsRefused() {
    var token = JWKS.wrongSignature(user());

    assertRefused(api().get(busObservations(), token), token);
  }

  @Test
  @DisplayName("193 AC-2 a token signed by an unknown key gets 401")
  void unknownKeyIsRefused() {
    var token = JWKS.unknownKey(user());

    assertRefused(api().get(busObservations(), token), token);
  }

  @Test
  @DisplayName("193 AC-2 a token from another issuer gets 401")
  void wrongIssuerIsRefused() {
    var token = JWKS.wrongIssuer(user());

    assertRefused(api().get(busObservations(), token), token);
  }

  @Test
  @DisplayName("193 AC-2 a token for another audience gets 401")
  void wrongAudienceIsRefused() {
    var token = JWKS.wrongAudience(user());

    assertRefused(api().get(busObservations(), token), token);
  }

  @Test
  @DisplayName("193 AC-2 an unsigned token (alg none) gets 401")
  void unsignedTokenIsRefused() {
    var token = JWKS.unsigned(user());

    assertRefused(api().get(busObservations(), token), token);
  }

  @Test
  @DisplayName("193 AC-2 a token signed with HS256 instead of RS256 gets 401")
  void hmacTokenIsRefused() {
    var token = JWKS.hmac(user());

    assertRefused(api().get(busObservations(), token), token);
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @ValueSource(strings = {"Bearer", "Bearer not a jwt", "Bearer a.b.c", "Bearer not-a-jwt",
      "Basic dXNlcjpwYXNz", "Token {valid}"})
  @DisplayName("193 AC-2 a malformed Authorization header gets 401")
  void malformedHeaderIsRefused(String header) {
    var valid = JWKS.valid(user());

    assertRefused(api().send("GET", busObservations(), header.replace("{valid}", valid)), valid);
  }

  private static Stream<String> badSubjects() {
    return Stream.of("not-a-uuid", "1-1-1-1-1", "UPPER", "", "ABSENT");
  }

  @ParameterizedTest(name = "[{index}] sub {0}")
  @MethodSource("badSubjects")
  @DisplayName("193 AC-2 a token whose sub is not a canonical UUID gets 401")
  void subThatIsNotACanonicalUuidIsRefused(String subject) {
    var claims = JWKS.claims(subject.equals("UPPER") ? user().toUpperCase() : subject);
    if (subject.equals("ABSENT")) {
      claims = JWKS.claims("placeholder").subject(null);
    }
    var token = JWKS.sign(claims.build());

    assertRefused(api().get(busObservations(), token), token);
  }

  @Test
  @DisplayName("193 AC-2 GET /api/v1/status is public for the health check")
  void statusIsPublic() {
    var response = api().get("/api/v1/status", null);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).isEqualTo("{\"service\":\"transports\",\"status\":\"ok\"}");
  }

  @Test
  @DisplayName("193 D12 the actuator health endpoint needs a token")
  void actuatorHealthNeedsAToken() {
    assertRefused(api().get("/actuator/health", null));
  }
}
