package pt.isep.labdsof.transports.adapters.in.web;

import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Reads the caller's user id from the validated access token, as Events does; Transports reads no other claim. */
final class TokenIdentity {

  private TokenIdentity() {}

  /**
   * The sub is already a canonical UUID ({@link SubjectValidator} runs on every token), and every path but the status
   * and the contract needs an authenticated JWT, so any other principal is a bug (500).
   */
  static UUID userId() {
    var token = ((JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication()).getToken();
    return UUID.fromString(token.getSubject());
  }
}
