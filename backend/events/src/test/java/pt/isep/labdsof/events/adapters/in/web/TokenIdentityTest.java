package pt.isep.labdsof.events.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class TokenIdentityTest {

  private static final UUID USER = UUID.fromString("11111111-2222-3333-4444-555555555555");

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  private static Jwt token() {
    return Jwt.withTokenValue("token-value").header("alg", "RS256").subject(USER.toString())
        .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
  }

  @Test
  @DisplayName("192 AC-5 the user id is the token's sub")
  void userIdIsTheSub() {
    assertThat(TokenIdentity.userId(token())).isEqualTo(USER);
  }

  @Test
  @DisplayName("192 AC-5 the current user id is read from the authenticated token")
  void currentUserIdComesFromTheSecurityContext() {
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(token()));

    assertThat(TokenIdentity.currentUserId()).isEqualTo(USER);
  }
}
