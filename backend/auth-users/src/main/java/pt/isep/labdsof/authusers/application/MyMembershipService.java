package pt.isep.labdsof.authusers.application;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MyMembershipService {

  private final UserAccounts accounts;
  private final Tombstones tombstones;
  private final MyMemberships memberships;

  public MyMembershipService(UserAccounts accounts, Tombstones tombstones, MyMemberships memberships) {
    this.accounts = accounts;
    this.tombstones = tombstones;
    this.memberships = memberships;
  }

  @Transactional(readOnly = true)
  public List<MyMemberships.Entry> list(UUID userId) {
    if (tombstones.exists(userId)) {
      throw new AccountUnavailableException();
    }
    var account = accounts.findById(userId);
    if (account.isEmpty()) {
      return List.of();
    }
    return switch (account.get().status()) {
      case Active -> memberships.findForUser(userId);
      case Disabled -> throw new AccountDisabledException();
      case DeletionPending -> throw new AccountUnavailableException();
    };
  }
}
