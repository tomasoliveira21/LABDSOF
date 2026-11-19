package pt.isep.labdsof.authusers.adapters.in.web;

import java.util.UUID;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The user id is the token's {@code sub} (spec D4). It must be a canonical lower-case UUID: {@code UUID.fromString}
 * alone accepts forms such as {@code 1-1-1-1-1}, and other services key users by the {@code sub} text, so a
 * different spelling would be a different user there. The error never repeats the value.
 */
final class SubjectValidator implements OAuth2TokenValidator<Jwt> {

  private static final OAuth2Error NOT_A_UUID =
      new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "The sub claim is not a UUID", null);

  @Override
  public OAuth2TokenValidatorResult validate(Jwt jwt) {
    return isCanonicalUuid(jwt.getSubject())
        ? OAuth2TokenValidatorResult.success()
        : OAuth2TokenValidatorResult.failure(NOT_A_UUID);
  }

  private static boolean isCanonicalUuid(String subject) {
    if (subject == null) {
      return false;
    }
    try {
      return UUID.fromString(subject).toString().equals(subject);
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
}
