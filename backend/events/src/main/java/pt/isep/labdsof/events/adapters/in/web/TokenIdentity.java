package pt.isep.labdsof.events.adapters.in.web;

import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Reads the caller's user id from the validated access token (spec AC-5). Events reads no other claim. */
final class TokenIdentity {

  private TokenIdentity() {}

  /** The sub is already a canonical UUID: {@link SubjectValidator} runs on every token. */
  static UUID userId(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }

  /** Every path but status and the contract needs an authenticated JWT, so any other principal is a bug (500). */
  static UUID currentUserId() {
    return userId(((JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication()).getToken());
  }
}
