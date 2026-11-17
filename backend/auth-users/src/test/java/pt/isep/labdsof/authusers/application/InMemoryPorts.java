package pt.isep.labdsof.authusers.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import pt.isep.labdsof.authusers.domain.GlobalRole;
import pt.isep.labdsof.authusers.domain.UserAccount;

/** Fakes of every port, for the use-case tests. */
final class InMemoryPorts implements UserAccounts, Tombstones, GlobalRoleGrants, Outbox {

  final Map<UUID, UserAccount> accounts = new HashMap<>();
  final Set<UUID> tombstones = new HashSet<>();
  final Map<UUID, EnumSet<GlobalRole>> roles = new HashMap<>();
  final List<OutgoingEvent> outbox = new ArrayList<>();
  int emailUpdates;
  int insertAttempts;
  /** When set, the next insert loses the race: this account is stored as if another call just committed it. */
  UserAccount concurrentWinner;

  @Override
  public Optional<UserAccount> findById(UUID userId) {
    return Optional.ofNullable(accounts.get(userId));
  }

  @Override
  public boolean insertIfAbsent(UserAccount account) {
    insertAttempts++;
    if (concurrentWinner != null) {
      accounts.put(concurrentWinner.userId(), concurrentWinner);
      concurrentWinner = null;
      return false;
    }
    return accounts.putIfAbsent(account.userId(), account) == null;
  }

  @Override
  public void updateEmail(UserAccount account) {
    emailUpdates++;
    accounts.put(account.userId(), account);
  }

  @Override
  public boolean exists(UUID userId) {
    return tombstones.contains(userId);
  }

  @Override
  public void grantBySystem(UUID userId, GlobalRole role, Instant at) {
    roles.computeIfAbsent(userId, id -> EnumSet.noneOf(GlobalRole.class)).add(role);
  }

  @Override
  public Set<GlobalRole> rolesOf(UUID userId) {
    return roles.getOrDefault(userId, EnumSet.noneOf(GlobalRole.class));
  }

  @Override
  public void add(OutgoingEvent event) {
    outbox.add(event);
  }

  static final class MutableClock extends Clock {

    private Instant now;

    MutableClock(Instant now) {
      this.now = now;
    }

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public Instant instant() {
      return now;
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }
  }
}
