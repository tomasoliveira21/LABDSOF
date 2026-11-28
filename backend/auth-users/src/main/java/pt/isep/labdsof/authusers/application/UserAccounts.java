package pt.isep.labdsof.authusers.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import pt.isep.labdsof.authusers.domain.UserAccount;

public interface UserAccounts {

  Optional<UserAccount> findById(UUID userId);

  /**
   * The one account holding this e-mail, matched <em>exactly</em> but case-insensitively, as
   * {@link #releaseVerifiedEmail} already treats an address (US-06 D7). Exactness is the point: a prefix or
   * fuzzy search would turn the lookup into a directory of every account on the platform.
   */
  Optional<UserAccount> findByEmail(String email);

  /** False when a uniqueness conflict prevents the insert. */
  boolean insertIfAbsent(UserAccount account);

  /** Writes the e-mail, whether it is verified, and the update time of {@code account}. */
  void updateEmail(UserAccount account);

  /**
   * Marks the e-mail as unverified on every account other than {@code keep} that holds it, and answers how many.
   * Keycloak keeps verified e-mails unique, so such a row is a copy left by an earlier token and must give the
   * address up before {@code keep} claims it as verified (D5).
   */
  int releaseVerifiedEmail(String email, UUID keep, Instant at);
}
