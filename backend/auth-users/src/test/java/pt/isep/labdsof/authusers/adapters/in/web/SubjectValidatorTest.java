package pt.isep.labdsof.authusers.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.jwt.Jwt;

class SubjectValidatorTest {

  private final SubjectValidator validator = new SubjectValidator();

  private static Jwt token(String subject) {
    var builder = Jwt.withTokenValue("token-value").header("alg", "RS256")
        .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
    return (subject == null ? builder.claim("other", "x") : builder.subject(subject)).build();
  }

  @Test
  @DisplayName("191 D4 a canonical UUID sub is accepted")
  void canonicalUuidIsAccepted() {
    assertThat(validator.validate(token(UUID.randomUUID().toString())).hasErrors()).isFalse();
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @NullAndEmptySource
  @ValueSource(strings = {"not-a-uuid", "1-1-1-1-1", "AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE",
      "{11111111-2222-3333-4444-555555555555}", "11111111-2222-3333-4444-555555555555 "})
  @DisplayName("191 D4 a missing, non-UUID or non-canonical sub is refused without echoing it")
  void otherSubjectsAreRefused(String subject) {
    var result = validator.validate(token(subject));

    assertThat(result.hasErrors()).isTrue();
    assertThat(result.getErrors()).singleElement().satisfies(error -> {
      assertThat(error.getErrorCode()).isEqualTo(OAuth2ErrorCodes.INVALID_TOKEN);
      assertThat(error.getDescription()).isNotBlank();
      if (subject != null && !subject.isBlank()) {
        assertThat(error.getDescription()).doesNotContain(subject.trim());
      }
    });
  }
}
