package pt.isep.labdsof.authusers.application;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.isep.labdsof.authusers.domain.AccountStatus;
import pt.isep.labdsof.authusers.domain.BootstrapAdmin;
import pt.isep.labdsof.authusers.domain.GlobalRole;
import pt.isep.labdsof.authusers.domain.UserAccount;

/** {@code GET /me}: the caller's account, created from the token on the first call (spec D5-D7). */
@Service
public class MeService {

  private final UserAccounts accounts;
  private final Tombstones tombstones;
  private final GlobalRoleGrants grants;
  private final Outbox outbox;
  private final Clock clock;
  private final BootstrapAdmin bootstrap;

  public MeService(UserAccounts accounts, Tombstones tombstones, GlobalRoleGrants grants, Outbox outbox,
      Clock clock, BootstrapAdmin bootstrap) {
    this.accounts = accounts;
    this.tombstones = tombstones;
    this.grants = grants;
    this.outbox = outbox;
    this.clock = clock;
    this.bootstrap = bootstrap;
  }

  @Transactional
  public MyAccount me(Identity identity) {
    var email = emailOf(identity);
    boolean verified = Boolean.TRUE.equals(identity.emailVerified());
    if (tombstones.exists(identity.userId())) {
      throw new AccountUnavailableException();
    }
    var existing = accounts.findById(identity.userId());
    if (existing.isPresent()) {
      return existing(existing.get(), email, verified);
    }
    if (verified) {
      accounts.releaseVerifiedEmail(email, identity.userId(), now());
    }
    var account = UserAccount.provision(identity.userId(), email, verified, identity.name(), clock.instant());
    if (!accounts.insertIfAbsent(account)) {
      // Another first call committed the same sub (ON CONFLICT waited for it): use that account, write no event (D5)
      return existing(accounts.findById(identity.userId()).orElseThrow(), email, verified);
    }
    outbox.add(OutgoingEvent.userProfileChanged(UUID.randomUUID(), account, identity.userId().toString()));
    if (!bootstrap.grants(email, identity.emailVerified())) {
      return new MyAccount(account, Set.of());
    }
    grantFarolAdmin(account.userId(), account.createdAt());
    return new MyAccount(account, Set.of(GlobalRole.FarolAdmin));
  }

  /** A token without a usable e-mail gets {@code 401}: NUL included, which no PostgreSQL text column accepts. */
  private static String emailOf(Identity identity) {
    var email = identity.email();
    if (email == null || email.isBlank() || email.indexOf('\0') >= 0) {
      throw new MissingEmailException();
    }
    return email.strip();
  }

  private MyAccount existing(UserAccount account, String email, boolean verified) {
    if (account.status() == AccountStatus.Disabled) {
      throw new AccountDisabledException();
    }
    if (account.status() == AccountStatus.DeletionPending) {
      throw new AccountUnavailableException();
    }
    var current = refreshEmail(account, email, verified);
    var roles = grants.rolesOf(account.userId());
    if (roles.contains(GlobalRole.FarolAdmin) || !bootstrap.grants(email, verified)) {
      return new MyAccount(current, roles);
    }
    // The bootstrap account's first call can happen before the e-mail is verified (D6): without this, no later call
    // would grant the role and the deployment would have no FarolAdmin at all
    grantFarolAdmin(account.userId(), now());
    var granted = EnumSet.of(GlobalRole.FarolAdmin);
    granted.addAll(roles);
    return new MyAccount(current, granted);
  }

  /**
   * Keycloak owns the e-mail, but only a verified claim is written (D5): an unverified token must never replace what
   * is stored, or it could take over another account's address and US-06's lookup would find the wrong account.
   */
  private UserAccount refreshEmail(UserAccount account, String email, boolean verified) {
    if (!verified || (account.email().equals(email) && account.emailVerified())) {
      return account;
    }
    var now = now();
    accounts.releaseVerifiedEmail(email, account.userId(), now);
    var current = account.withEmail(email, true, now);
    accounts.updateEmail(current);
    return current;
  }

  private void grantFarolAdmin(UUID userId, Instant at) {
    grants.grantBySystem(userId, GlobalRole.FarolAdmin, at);
    // A system grant: empty actor (§7.1) and no granting user in the table
    outbox.add(OutgoingEvent.globalRoleChanged(UUID.randomUUID(), userId, GlobalRole.FarolAdmin, true, 1, at, ""));
  }

  /** To the microsecond, as the database stores it and the events carry it. */
  private Instant now() {
    return clock.instant().truncatedTo(ChronoUnit.MICROS);
  }
}
