package pt.isep.labdsof.authusers.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class TokenIdentityTest {

  private static final UUID USER = UUID.fromString("11111111-2222-3333-4444-555555555555");

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  private static Jwt.Builder token() {
    return Jwt.withTokenValue("token-value").header("alg", "RS256").subject(USER.toString())
        .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
  }

  private static Stream<Arguments> verifiedValues() {
    return Stream.of(Arguments.of(true, true), Arguments.of(false, false), Arguments.of("true", null),
        Arguments.of(1, null));
  }

  @Test
  @DisplayName("191 D4 the identity takes the sub as user id and the email, email_verified and name claims")
  void identityComesFromTheClaims() {
    var identity = TokenIdentity.of(token().claim("email", "ana@farol.test").claim("email_verified", true)
        .claim("name", "Ana Silva").build());

    assertThat(identity.userId()).isEqualTo(USER);
    assertThat(identity.email()).isEqualTo("ana@farol.test");
    assertThat(identity.emailVerified()).isTrue();
    assertThat(identity.name()).isEqualTo("Ana Silva");
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("verifiedValues")
  @DisplayName("191 AC-6 only a JSON boolean true or false counts as email_verified")
  void onlyABooleanCounts(Object claim, Boolean expected) {
    assertThat(TokenIdentity.of(token().claim("email_verified", claim).build()).emailVerified())
        .isEqualTo(expected);
  }

  @Test
  @DisplayName("191 AC-6 only a JSON boolean true or false counts as email_verified")
  void absentEmailVerifiedIsUnknown() {
    assertThat(TokenIdentity.of(token().claim("email", "ana@farol.test").build()).emailVerified()).isNull();
  }

  @Test
  @DisplayName("191 AC-5 an email or name claim that is not text is read as missing")
  void claimsThatAreNotTextAreMissing() {
    var identity = TokenIdentity.of(token().claim("email", 42).claim("name", true).build());

    assertThat(identity.email()).isNull();
    assertThat(identity.name()).isNull();
    var absent = TokenIdentity.of(token().claim("other", "x").build());
    assertThat(absent.email()).isNull();
    assertThat(absent.name()).isNull();
  }

  @Test
  @DisplayName("191 D4 the current identity is read from the authenticated token")
  void currentIdentityComesFromTheSecurityContext() {
    var jwt = token().claim("email", "ana@farol.test").build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

    var identity = TokenIdentity.current();

    assertThat(identity.userId()).isEqualTo(USER);
    assertThat(identity.email()).isEqualTo("ana@farol.test");
  }
}
