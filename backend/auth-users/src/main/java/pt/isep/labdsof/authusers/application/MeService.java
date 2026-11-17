package pt.isep.labdsof.authusers.application;

import java.time.Clock;
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
    if (identity.email() == null || identity.email().isBlank()) {
      throw new MissingEmailException();
    }
    if (tombstones.exists(identity.userId())) {
      throw new AccountUnavailableException();
    }
    var existing = accounts.findById(identity.userId());
    if (existing.isPresent()) {
      return existing(existing.get(), identity);
    }
    var account = UserAccount.provision(identity.userId(), identity.email(), identity.name(), clock.instant());
    if (!accounts.insertIfAbsent(account)) {
      // Another first call committed the same sub (ON CONFLICT waited for it): use that account, write no event (D5)
      return existing(accounts.findById(identity.userId()).orElseThrow(), identity);
    }
    outbox.add(OutgoingEvent.userProfileChanged(UUID.randomUUID(), account, identity.userId().toString()));
    if (!bootstrap.grants(identity.email(), identity.emailVerified())) {
      return new MyAccount(account, Set.of());
    }
    grants.grantBySystem(account.userId(), GlobalRole.FarolAdmin, account.createdAt());
    // A system grant: empty actor (§7.1) and no granting user in the table
    outbox.add(OutgoingEvent.globalRoleChanged(UUID.randomUUID(), account.userId(), GlobalRole.FarolAdmin, true, 1,
        account.createdAt(), ""));
    return new MyAccount(account, Set.of(GlobalRole.FarolAdmin));
  }

  private MyAccount existing(UserAccount account, Identity identity) {
    if (account.status() == AccountStatus.Disabled) {
      throw new AccountDisabledException();
    }
    if (account.status() == AccountStatus.DeletionPending) {
      throw new AccountUnavailableException();
    }
    var current = account;
    if (!account.email().equals(identity.email())) {
      current = account.withEmail(identity.email(), clock.instant());
      accounts.updateEmail(current);
    }
    return new MyAccount(current, grants.rolesOf(account.userId()));
  }
}
