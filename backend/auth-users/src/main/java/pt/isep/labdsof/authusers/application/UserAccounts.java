package pt.isep.labdsof.authusers.application;

import java.util.Optional;
import java.util.UUID;
import pt.isep.labdsof.authusers.domain.UserAccount;

public interface UserAccounts {

  Optional<UserAccount> findById(UUID userId);

  /** False when the account already exists (another call created it first). */
  boolean insertIfAbsent(UserAccount account);

  /** Writes the e-mail and the update time of {@code account}. */
  void updateEmail(UserAccount account);
}
