package pt.isep.labdsof.events.adapters.out.codes;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;
import pt.isep.labdsof.events.application.JoinCodes;

/**
 * 160 bits from {@link SecureRandom}, Base64 URL-safe without padding: 27 characters that need no
 * percent-encoding, so the link in the QR code carries the string itself (US-11 D1). Unguessable, which keeps
 * open the option of US-12 checking it.
 */
@Component
class RandomJoinCodes implements JoinCodes {

  static final int BYTES = 20;

  private final SecureRandom random = new SecureRandom();
  private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();

  @Override
  public String next() {
    var bytes = new byte[BYTES];
    random.nextBytes(bytes);
    return encoder.encodeToString(bytes);
  }
}
