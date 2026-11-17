package pt.isep.labdsof.authusers;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jwt.JWTClaimsSet;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** {@code GET /api/v1/me} over real HTTP with signed tokens; every test uses its own user id. */
class MeIT extends IntegrationTest {

  private HttpResponse<String> me(String token) {
    return new ApiClient(port).get("/api/v1/me", token);
  }

  private HttpResponse<String> me(JWTClaimsSet.Builder claims) {
    return me(JWKS.sign(claims.build()));
  }

  private long accountRows(UUID user) {
    return jdbc.sql("SELECT count(*) FROM user_account WHERE user_id = :id")
        .param("id", user).query(Long.class).single();
  }

  private long outboxRows(UUID user) {
    return jdbc.sql("SELECT count(*) FROM outbox WHERE body->'payload'->>'userId' = :id")
        .param("id", user.toString()).query(Long.class).single();
  }

  private String emailOf(UUID user) {
    return jdbc.sql("SELECT email FROM user_account WHERE user_id = :id")
        .param("id", user).query(String.class).single();
  }

  private void insertAccount(UUID user, String email, String status) {
    jdbc.sql("""
        INSERT INTO user_account (user_id, email, display_name, status, version, created_at, updated_at)
        VALUES (:id, :email, 'Ana', :status, 1, '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')
        """).param("id", user).param("email", email).param("status", status).update();
  }

  @Test
  @DisplayName("191 AC-5 a first GET /api/v1/me creates the account from the token and answers the Me schema")
  void firstCallCreatesTheAccount() {
    var user = UUID.randomUUID();

    var response = me(JWKS.claims(user.toString()).claim("name", "Ana Silva"));

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
        type -> assertThat(type).startsWith("application/json"));
    var body = ApiClient.json(response);
    assertThat(body).containsOnlyKeys("userId", "email", "displayName", "globalRoles");
    assertThat(body).containsEntry("userId", user.toString()).containsEntry("email", user + "@farol.test")
        .containsEntry("displayName", "Ana Silva").containsEntry("globalRoles", List.of());
    assertThat(accountRows(user)).isEqualTo(1);
    assertThat(jdbc.sql("SELECT type FROM outbox WHERE body->'payload'->>'userId' = :id")
        .param("id", user.toString()).query(String.class).list()).containsExactly("UserProfileChanged");
  }

  @Test
  @DisplayName("191 AC-5 a second GET /api/v1/me answers the same account, refreshes the e-mail, keeps the name "
      + "and writes no event")
  void secondCallRefreshesTheEmail() {
    var user = UUID.randomUUID();
    me(JWKS.claims(user.toString()).claim("name", "Ana Silva"));

    var response = me(JWKS.claims(user.toString()).claim("email", "new-" + user + "@farol.test")
        .claim("name", "Another Name"));

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(ApiClient.json(response)).containsEntry("userId", user.toString())
        .containsEntry("email", "new-" + user + "@farol.test").containsEntry("displayName", "Ana Silva");
    assertThat(emailOf(user)).isEqualTo("new-" + user + "@farol.test");
    assertThat(outboxRows(user)).isEqualTo(1);
  }

  @Test
  @DisplayName("191 AC-5 concurrent first calls create one account and one event")
  void concurrentFirstCallsCreateOneAccount() throws Exception {
    var user = UUID.randomUUID();
    var token = JWKS.valid(user.toString());

    try (var pool = Executors.newFixedThreadPool(3)) {
      var calls = List.of(pool.submit(() -> me(token)), pool.submit(() -> me(token)), pool.submit(() -> me(token)));
      for (var call : calls) {
        assertThat(call.get().statusCode()).isEqualTo(200);
      }
    }

    assertThat(accountRows(user)).isEqualTo(1);
    assertThat(outboxRows(user)).isEqualTo(1);
  }

  @Test
  @DisplayName("191 AC-5 the display name comes from the name claim, trimmed")
  void displayNameIsTrimmed() {
    var response = me(JWKS.claims(UUID.randomUUID().toString()).claim("name", "  Ana Silva  "));

    assertThat(ApiClient.json(response)).containsEntry("displayName", "Ana Silva");
  }

  private static Stream<Arguments> tokensWithoutEmail() {
    return Stream.of(Arguments.of("absent", null), Arguments.of("empty", ""), Arguments.of("blank", "   "),
        Arguments.of("a number", 42));
  }

  @ParameterizedTest(name = "[{index}] e-mail {0}")
  @MethodSource("tokensWithoutEmail")
  @DisplayName("191 AC-5 a token without e-mail gets 401 Problem Details and nothing is created")
  void tokenWithoutEmailIsRefused(String description, Object email) {
    var user = UUID.randomUUID();

    var response = me(JWKS.claims(user.toString()).claim("email", email));

    SecurityIT.assertRefused(response);
    assertThat(accountRows(user)).isZero();
    assertThat(outboxRows(user)).isZero();
  }

  @ParameterizedTest(name = "[{index}] sub {0}")
  @MethodSource("subjectsThatAreNotUuids")
  @DisplayName("191 AC-2 a token whose sub is not a uuid is refused")
  void subjectThatIsNotAUuidIsRefused(String description, String subject) {
    var response = me(JWKS.claims("placeholder").subject(subject));

    SecurityIT.assertRefused(response);
  }

  private static Stream<Arguments> subjectsThatAreNotUuids() {
    return Stream.of(Arguments.of("text", "not-a-uuid"), Arguments.of("short groups", "1-1-1-1-1"),
        Arguments.of("empty", ""), Arguments.of("absent", null));
  }

  @Test
  @DisplayName("191 AC-2 a token whose sub is not a uuid is refused")
  void upperCaseSubjectIsRefused() {
    var user = UUID.randomUUID();

    var response = me(JWKS.claims(user.toString().toUpperCase()));

    SecurityIT.assertRefused(response);
    assertThat(accountRows(user)).isZero();
  }

  @Test
  @DisplayName("191 AC-6 the verified bootstrap e-mail gets FarolAdmin in globalRoles")
  void verifiedBootstrapEmailGetsFarolAdmin() {
    var response = me(JWKS.claims(UUID.randomUUID().toString()).claim("email", "Admin@Farol.test")
        .claim("email_verified", true));

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(ApiClient.json(response)).containsEntry("globalRoles", List.of("FarolAdmin"));
  }

  private static Stream<Arguments> unverified() {
    return Stream.of(Arguments.of("false", false), Arguments.of("absent", null), Arguments.of("a string", "true"));
  }

  @ParameterizedTest(name = "[{index}] email_verified {0}")
  @MethodSource("unverified")
  @DisplayName("191 AC-6 an unverified bootstrap e-mail, or one without email_verified, gets no role")
  void unverifiedBootstrapEmailGetsNoRole(String description, Object verified) {
    var response = me(JWKS.claims(UUID.randomUUID().toString()).claim("email", Infrastructure.BOOTSTRAP_EMAIL)
        .claim("email_verified", verified));

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(ApiClient.json(response)).containsEntry("globalRoles", List.of());
  }

  @Test
  @DisplayName("191 AC-7 a deleted (tombstone) account gets 401 Problem Details and nothing is created")
  void tombstoneIsRefused() {
    var user = UUID.randomUUID();
    jdbc.sql("INSERT INTO user_tombstone (user_id, deleted_at) VALUES (:id, now())").param("id", user).update();

    var response = me(JWKS.valid(user.toString()));

    SecurityIT.assertRefused(response, user + "@farol.test");
    assertThat(accountRows(user)).isZero();
    assertThat(outboxRows(user)).isZero();
  }

  @Test
  @DisplayName("191 AC-7 an account pending deletion gets 401 Problem Details and nothing changes")
  void deletionPendingIsRefused() {
    var user = UUID.randomUUID();
    insertAccount(user, "old@farol.test", "DeletionPending");

    var response = me(JWKS.valid(user.toString()));

    SecurityIT.assertRefused(response, user + "@farol.test");
    assertThat(emailOf(user)).isEqualTo("old@farol.test");
    assertThat(outboxRows(user)).isZero();
  }

  @Test
  @DisplayName("191 AC-7 a disabled account gets 403 Problem Details and nothing changes")
  void disabledIsRefused() {
    var user = UUID.randomUUID();
    insertAccount(user, "old@farol.test", "Disabled");

    var response = me(JWKS.valid(user.toString()));

    ApiClient.assertProblem(response, 403, user + "@farol.test");
    assertThat(emailOf(user)).isEqualTo("old@farol.test");
    assertThat(outboxRows(user)).isZero();
  }
}
