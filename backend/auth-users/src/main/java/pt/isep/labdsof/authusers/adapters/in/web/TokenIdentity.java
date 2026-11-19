package pt.isep.labdsof.authusers.adapters.in.web;

import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import pt.isep.labdsof.authusers.application.Identity;

/** Reads the caller's {@link Identity} from the validated access token (spec D4, D5, D6). */
final class TokenIdentity {

  private TokenIdentity() {}

  /** The {@code sub} is already known to be a canonical UUID: {@link SubjectValidator} runs on every token. */
  static Identity of(Jwt jwt) {
    var claims = jwt.getClaims();
    // Only a JSON boolean counts: the string "true" must not grant the bootstrap role (spec T4)
    Boolean emailVerified = claims.get("email_verified") instanceof Boolean verified ? verified : null;
    return new Identity(UUID.fromString(jwt.getSubject()), text(claims, "email"), emailVerified, text(claims, "name"));
  }

  /** Every path but status and the contract needs an authenticated JWT, so any other principal is a bug (500). */
  static Identity current() {
    return of(((JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication()).getToken());
  }

  private static String text(Map<String, Object> claims, String name) {
    return claims.get(name) instanceof String value ? value : null;
  }
}
