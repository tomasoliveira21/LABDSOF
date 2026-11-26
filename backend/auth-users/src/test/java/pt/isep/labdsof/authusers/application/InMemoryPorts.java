package pt.isep.labdsof.authusers.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import pt.isep.labdsof.authusers.domain.EventMembership;
import pt.isep.labdsof.authusers.domain.GlobalRole;
import pt.isep.labdsof.authusers.domain.UserAccount;

/** Fakes of every port, for the use-case tests. */
final class InMemoryPorts implements UserAccounts, Tombstones, GlobalRoleGrants, Outbox, EventMemberships,
    TeamCopies, MemberAudit, MyMemberships {

  /** What a test asserts about a team assignment that was stored (US-18 D8). */
  record Assignment(UUID eventId, UUID userId, UUID teamId, UUID actorUserId, Instant at, boolean added) {}

  /** What a test asserts about a refusal that was recorded (US-14). */
  record Refused(UUID actorUserId, UUID eventId, Refusal refusal, Instant at) {}

  final Map<UUID, UserAccount> accounts = new HashMap<>();
  final Set<UUID> tombstones = new HashSet<>();
  final Map<UUID, EnumSet<GlobalRole>> roles = new HashMap<>();
  final List<OutgoingEvent> outbox = new ArrayList<>();
  final List<String> releasedEmails = new ArrayList<>();
  /** The memberships of every event, by event and user, as the database holds them. */
  final Map<List<UUID>, EventMembership> memberships = new HashMap<>();
  /** The live teams of each event (the copies of #241); a deleted team is simply not here. */
  final Map<UUID, UUID> liveTeams = new HashMap<>();
  /** What the repository stored, and what the audit logged: one list each, so a test can tell them apart. */
  final List<Assignment> assignments = new ArrayList<>();
  final List<Assignment> audited = new ArrayList<>();
  final List<Refused> refusals = new ArrayList<>();
  final List<UUID> locked = new ArrayList<>();
  int emailUpdates;
  int insertAttempts;
  /** When set, the next insert loses the race: this account is stored as if another call just committed it. */
  UserAccount concurrentWinner;
  /** When set, the next insert is refused but stores nothing: the conflict was on the e-mail, not the subject. */
  boolean insertConflictsWithoutAnAccount;

  @Override
  public Optional<UserAccount> findById(UUID userId) {
    return Optional.ofNullable(accounts.get(userId));
  }

  @Override
  public boolean insertIfAbsent(UserAccount account) {
    insertAttempts++;
    if (insertConflictsWithoutAnAccount) {
      return false;
    }
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
  public int releaseVerifiedEmail(String email, UUID keep, Instant at) {
    releasedEmails.add(email);
    var released = accounts.values().stream()
        .filter(account -> account.emailVerified() && !account.userId().equals(keep)
            && account.email().equalsIgnoreCase(email))
        .toList();
    released.forEach(account -> accounts.put(account.userId(), account.withEmail(account.email(), false, at)));
    return released.size();
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

  /** What {@code GET /me/memberships} answers, whoever asks (US-01). */
  final List<Entry> myMemberships = new ArrayList<>();

  @Override
  public List<Entry> findForUser(UUID userId) {
    return List.copyOf(myMemberships);
  }

  // US-18

  void member(EventMembership membership) {
    memberships.put(List.of(membership.eventId(), membership.userId()), membership);
  }

  void liveTeam(UUID eventId, UUID teamId) {
    liveTeams.put(teamId, eventId);
  }

  @Override
  public Optional<EventMembership> find(UUID eventId, UUID userId) {
    return Optional.ofNullable(memberships.get(List.of(eventId, userId)));
  }

  @Override
  public Optional<EventMembership> lockForUpdate(UUID eventId, UUID userId) {
    locked.add(userId);
    return find(eventId, userId);
  }

  @Override
  public void addToTeam(EventMembership updated, UUID teamId, UUID actorUserId, Instant at) {
    member(updated);
    assignments.add(new Assignment(updated.eventId(), updated.userId(), teamId, actorUserId, at, true));
  }

  @Override
  public void removeFromTeam(EventMembership updated, UUID teamId, UUID actorUserId, Instant at) {
    member(updated);
    assignments.add(new Assignment(updated.eventId(), updated.userId(), teamId, actorUserId, at, false));
  }

  @Override
  public List<Member> list(UUID eventId, pt.isep.labdsof.authusers.domain.EventRole role, UUID teamId) {
    return memberships.values().stream()
        .filter(membership -> membership.eventId().equals(eventId))
        .filter(membership -> role == null || membership.role() == role)
        .filter(membership -> teamId == null || membership.isIn(teamId))
        .map(membership -> new Member(membership.userId(),
            accounts.containsKey(membership.userId()) ? accounts.get(membership.userId()).displayName() : "?",
            membership.role(), membership.teamIds()))
        .sorted(Comparator.comparing(Member::displayName).thenComparing(Member::userId))
        .toList();
  }

  @Override
  public boolean isLiveTeamOf(UUID eventId, UUID teamId) {
    return eventId.equals(liveTeams.get(teamId));
  }

  @Override
  public void teamAssigned(UUID actorUserId, UUID eventId, UUID userId, UUID teamId, Instant at) {
    audited.add(new Assignment(eventId, userId, teamId, actorUserId, at, true));
  }

  @Override
  public void teamUnassigned(UUID actorUserId, UUID eventId, UUID userId, UUID teamId, Instant at) {
    audited.add(new Assignment(eventId, userId, teamId, actorUserId, at, false));
  }

  @Override
  public void refused(UUID actorUserId, UUID eventId, Refusal refusal, Instant at) {
    refusals.add(new Refused(actorUserId, eventId, refusal, at));
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
