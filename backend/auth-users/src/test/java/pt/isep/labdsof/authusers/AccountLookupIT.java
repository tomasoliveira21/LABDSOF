package pt.isep.labdsof.authusers;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import pt.isep.labdsof.authusers.application.AccountLookupService;

/** GET /api/v1/accounts?email= over HTTP with signed tokens (US-06 D7). */
@ExtendWith(OutputCaptureExtension.class)
class AccountLookupIT extends IntegrationTest {

  private ApiClient api() {
    return new ApiClient(port);
  }

  private static String path(String email) {
    return "/api/v1/accounts?email=" + URLEncoder.encode(email, StandardCharsets.UTF_8);
  }

  private HttpResponse<String> lookup(String email, UUID user) {
    return api().get(path(email), user == null ? null : JWKS.valid(user.toString()));
  }

  private UUID farolAdmin() {
    var user = UUID.randomUUID();
    seedAccount(user, "Daniel Platform");
    jdbc.sql("""
        INSERT INTO global_role_grant (user_id, role, granted_at, version)
        VALUES (:u, 'FarolAdmin', now(), 1)
        """).param("u", user).update();
    return user;
  }

  /** An account with a known address, so the exact match can be driven. */
  private UUID organiser(String email) {
    var user = UUID.randomUUID();
    jdbc.sql("""
        INSERT INTO user_account (user_id, email, email_verified, display_name, status, version, created_at,
                                  updated_at)
        VALUES (:id, :email, true, 'Sofia Organiser', 'Active', 1, now(), now())
        """).param("id", user).param("email", email).update();
    return user;
  }

  @Test
  @DisplayName("US-06 AC-1 the FarolAdmin finds the account and gets its id and display name, never the e-mail")
  void theFarolAdminFindsTheAccount() {
    var admin = farolAdmin();
    var email = "organiser-" + UUID.randomUUID() + "@fap.test";
    var organiser = organiser(email);

    var response = lookup(email, admin);

    assertThat(response.statusCode()).isEqualTo(200);
    var body = ApiClient.json(response);
    assertThat(body).containsEntry("userId", organiser.toString())
        .containsEntry("displayName", "Sofia Organiser");
    // The address is never echoed back, so a hit cannot confirm the spelling of a guess (D7)
    assertThat(body.keySet()).containsExactlyInAnyOrder("userId", "displayName");
    assertThat(response.body()).doesNotContain(email);
  }

  @Test
  @DisplayName("US-06 AC-1 the match ignores case but is exact: no prefix or partial address finds anything")
  void theMatchIsExactButCaseInsensitive() {
    var admin = farolAdmin();
    var local = "organiser-" + UUID.randomUUID();
    var email = local + "@fap.test";
    organiser(email);

    assertThat(lookup(email.toUpperCase(java.util.Locale.ROOT), admin).statusCode()).isEqualTo(200);
    assertThat(lookup(local, admin).statusCode()).isEqualTo(400); // not an e-mail at all
    assertThat(lookup(local + "@fap.test.pt", admin).statusCode()).isEqualTo(404);
    assertThat(lookup("other-" + local + "@fap.test", admin).statusCode()).isEqualTo(404);
  }

  @Test
  @DisplayName("US-06 AC-1 an address no account holds is 404 Problem Details without the address in it")
  void aMissIs404() {
    var admin = farolAdmin();
    var email = "nobody-" + UUID.randomUUID() + "@fap.test";

    var response = lookup(email, admin);

    assertThat(response.statusCode()).isEqualTo(404);
    assertThat(ApiClient.json(response)).containsEntry("detail", "Account not found.");
    assertThat(response.body()).doesNotContain(email);
  }

  @Test
  @DisplayName("US-06 AC-2 a signed-in caller who is not the FarolAdmin gets 403, and no token gets 401")
  void onlyTheFarolAdminLooksUp() {
    var email = "organiser-" + UUID.randomUUID() + "@fap.test";
    organiser(email);
    var user = UUID.randomUUID();
    seedAccount(user, "Rita Participante");

    var refused = lookup(email, user);
    assertThat(refused.statusCode()).isEqualTo(403);
    assertThat(ApiClient.json(refused)).containsEntry("detail", "Only the FarolAdmin may do this.");

    var anonymous = lookup(email, null);
    assertThat(anonymous.statusCode()).isEqualTo(401);
    assertThat(anonymous.headers().firstValue("www-authenticate").orElse("")).startsWith("Bearer");
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"not-an-email", "a@", "@b.test", " "})
  @DisplayName("US-06 AC-1 a malformed address is 400 and the parameter is never echoed in the problem")
  void aMalformedAddressIs400(String email) {
    var admin = farolAdmin();

    var response = lookup(email, admin);

    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(response.body()).doesNotContain(email.trim().isEmpty() ? "\"\"" : email);
  }

  @Test
  @DisplayName("US-06 AC-1 an address past the contract's 254-character bound is refused")
  void anOverLongAddressIsRefused() {
    var admin = farolAdmin();

    // A local part of 65 is already past what an address may hold (RFC 5321 caps it at 64), so the long case
    // is built on the domain instead, which is what the contract's maxLength actually bounds
    var label = "a".repeat(60);
    var email = "organiser@" + label + "." + label + "." + label + "." + label + ".test";
    assertThat(email).hasSizeGreaterThan(254);

    assertThat(lookup(email, admin).statusCode()).isEqualTo(400);
  }

  @Test
  @DisplayName("US-06 D7 the lookup is recorded with a hash of the address, and the address never reaches a log")
  void theLookupIsRecordedWithAHashOnly(CapturedOutput output) {
    var admin = farolAdmin();
    var email = "organiser-" + UUID.randomUUID() + "@fap.test";
    organiser(email);

    lookup(email, admin);
    lookup("nobody-" + UUID.randomUUID() + "@fap.test", admin);

    var hash = AccountLookupService.hash(email);
    assertThat(logLines(output)).anyMatch(line -> "Looked an account up by e-mail (US-06)".equals(
        line.get("message")) && hash.equals(line.get("emailHash")) && Boolean.TRUE.equals(line.get("found")));
    assertThat(logLines(output)).anyMatch(line -> "Looked an account up by e-mail (US-06)".equals(
        line.get("message")) && Boolean.FALSE.equals(line.get("found")));
    // M-10: neither the address nor the local part of it appears anywhere in the output
    assertThat(output.getAll()).doesNotContain(email).doesNotContain(email.split("@")[0]);
  }
}
