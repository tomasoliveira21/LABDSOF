package pt.isep.labdsof.authusers.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.isep.labdsof.authusers.domain.GlobalRole;

/**
 * Finding the account to name EventAdmin, by e-mail (US-06, API outline §2). The FarolAdmin knows the organiser's
 * address, not their {@code userId}, so this is the one lookup of a person by personal data in the product — and
 * it is kept as narrow as the story allows (D7):
 *
 * <ul>
 *   <li><strong>FarolAdmin only.</strong> This service owns the global roles, so the answer is never stale.</li>
 *   <li><strong>Exact match</strong>, case-insensitive. A prefix or fuzzy search would make it a directory.</li>
 *   <li><strong>The answer carries the {@code userId} and the display name only</strong>, never the address back,
 *       so a hit cannot be used to confirm the spelling of an address that was guessed.</li>
 *   <li><strong>Every call is recorded</strong>, hit or miss, with a <em>hash</em> of the address and never the
 *       address itself (M-10), so an enumeration attempt is visible in the trail without the log pipeline of
 *       #148 holding anyone's e-mail.</li>
 * </ul>
 *
 * <p>A {@code 404} for a miss and a {@code 200} for a hit still make this an existence oracle for one address at
 * a time. Bounding how fast it can be asked is the gateway's ({@code ADR-013}, #202) and the gateway has no such
 * filter today: the point is recorded on #61.
 */
@Service
public class AccountLookupService {

  /** What the FarolAdmin gets back: enough to name the person, and nothing more. */
  public record Found(UUID userId, String displayName) {}

  private final UserAccounts accounts;
  private final GlobalRoleGrants roles;
  private final MemberAudit audit;
  private final Clock clock;

  AccountLookupService(UserAccounts accounts, GlobalRoleGrants roles, MemberAudit audit, Clock clock) {
    this.accounts = accounts;
    this.roles = roles;
    this.audit = audit;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public Found byEmail(UUID actorUserId, String email) {
    var at = clock.instant().truncatedTo(ChronoUnit.MICROS);
    if (!roles.rolesOf(actorUserId).contains(GlobalRole.FarolAdmin)) {
      // Recorded before the address is even looked at, so a refused attempt is in the trail too
      audit.accountLookedUp(actorUserId, hash(email), false, at);
      throw new NotAFarolAdminException();
    }
    var found = accounts.findByEmail(email);
    audit.accountLookedUp(actorUserId, hash(email), found.isPresent(), at);
    return found.map(account -> new Found(account.userId(), account.displayName()))
        .orElseThrow(AccountNotFoundException::new);
  }

  /**
   * SHA-256 of the lower-cased address, Base64 without padding. Not a secret and not reversible in practice for
   * an address nobody guesses; the point is that two calls about one address are the same line in the trail
   * while the address itself never leaves the database.
   */
  public static String hash(String email) {
    try {
      var digest = MessageDigest.getInstance("SHA-256")
          .digest(String.valueOf(email).toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is required of every JVM", e);
    }
  }
}
