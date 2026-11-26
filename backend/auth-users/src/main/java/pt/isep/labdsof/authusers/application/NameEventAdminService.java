package pt.isep.labdsof.authusers.application;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.isep.labdsof.authusers.application.MemberAudit.Refusal;
import pt.isep.labdsof.authusers.domain.AccountStatus;
import pt.isep.labdsof.authusers.domain.EventMembership;
import pt.isep.labdsof.authusers.domain.EventRole;
import pt.isep.labdsof.authusers.domain.GlobalRole;

/**
 * Naming a user EventAdmin of an event (US-06): the step that follows {@code POST /events} in Events, and the
 * second half of this story's one acceptance criterion. The FarolAdmin creates the event there and names its
 * first EventAdmin here (API outline D3).
 *
 * <p>The event must be in this service's copy of {@code EventChanged} before the call can succeed, which is what
 * the create now publishes (US-06 D5). Until it arrives the answer is {@code 404} and the back office retries,
 * exactly as the API outline says — so the two steps are eventually consistent rather than coordinated.
 *
 * <p>Naming the same user again changes nothing and publishes nothing, and still answers {@code 201}: the
 * contract declares one success code, and a back office retrying a timed-out call must not get an error for work
 * already done (D6).
 */
@Service
public class NameEventAdminService {

  /** The membership as it now reads, and whether this call changed anything (nothing is published if not). */
  public record Named(EventMembership membership, String displayName, boolean changed) {}

  private final UserAccounts accounts;
  private final Tombstones tombstones;
  private final GlobalRoleGrants roles;
  private final EventCopies events;
  private final EventMemberships memberships;
  private final Outbox outbox;
  private final MemberAudit audit;
  private final Clock clock;

  NameEventAdminService(UserAccounts accounts, Tombstones tombstones, GlobalRoleGrants roles, EventCopies events,
      EventMemberships memberships, Outbox outbox, MemberAudit audit, Clock clock) {
    this.accounts = accounts;
    this.tombstones = tombstones;
    this.roles = roles;
    this.events = events;
    this.memberships = memberships;
    this.outbox = outbox;
    this.audit = audit;
    this.clock = clock;
  }

  @Transactional
  public Named name(UUID actorUserId, UUID eventId, UUID userId) {
    var at = now();
    farolAdmin(actorUserId, eventId, at);
    // An event with no copy yet and an unknown event are the same 404, on purpose (US-12 D2)
    events.find(eventId).orElseThrow(() -> {
      audit.refused(actorUserId, eventId, Refusal.NO_SUCH_EVENT_COPY, at);
      return new EventNotFoundException();
    });
    var account = namedAccount(actorUserId, eventId, userId, at);

    // The lock of US-18 D7: two name calls for one user queue instead of both writing the same version
    var existing = memberships.lockForUpdate(eventId, userId);
    if (existing.isEmpty()) {
      return firstAdmin(actorUserId, eventId, userId, account.displayName(), at);
    }
    if (existing.get().role() == EventRole.EventAdmin) {
      // Already an EventAdmin: nothing changes, nothing is published, and the answer is still a 201 (D6)
      return new Named(existing.get(), account.displayName(), false);
    }
    // The row exists and is locked, so the version it carries cannot move under this write (US-18 D7)
    var promoted = new EventMembership(eventId, userId, EventRole.EventAdmin, existing.get().teamIds(),
        existing.get().version() + 1);
    memberships.upsertRole(promoted, actorUserId, at);
    return published(actorUserId, eventId, userId, promoted, account.displayName(), at);
  }

  /**
   * The first EventAdmin of an event that has no membership for this user yet. There is <em>no row to lock</em>
   * here, so {@link EventMemberships#lockForUpdate} cannot serialise two calls arriving together: the key does,
   * through an insert that stores nothing when the row is already there (the same reasoning as US-12 D5).
   *
   * <p>Without this, two name calls at once both inserted version 1 and both published a
   * {@code MembershipChanged} carrying it — two messages with one version, which decision 3 of 21 November 2026
   * exists to prevent, and which a consumer applying only a strictly newer version would silently halve. Found by
   * {@code NameEventAdminIT · two name calls for one user at once leave one version}.
   *
   * <p>The loser writes nothing and publishes nothing: it answers the row the winner left, which is already the
   * EventAdmin the caller asked for, so {@code changed} is false (D6).
   */
  private Named firstAdmin(UUID actorUserId, UUID eventId, UUID userId, String displayName, Instant at) {
    var membership = new EventMembership(eventId, userId, EventRole.EventAdmin, List.of(), 1);
    if (!memberships.insertIfAbsent(membership, actorUserId, at)) {
      var held = memberships.find(eventId, userId).orElseThrow(EventNotFoundException::new);
      return new Named(held, displayName, false);
    }
    return published(actorUserId, eventId, userId, membership, displayName, at);
  }

  /** One {@code MembershipChanged} and one record for a write that happened, in the same transaction (D5). */
  private Named published(UUID actorUserId, UUID eventId, UUID userId, EventMembership membership,
      String displayName, Instant at) {
    outbox.add(OutgoingEvent.membershipChanged(UUID.randomUUID(), membership, at, actorUserId.toString()));
    audit.namedEventAdmin(actorUserId, eventId, userId, at);
    return new Named(membership, displayName, true);
  }

  /**
   * The platform role, owned by this service so never stale (D1). Being the EventAdmin of some event is not it:
   * naming the <em>first</em> EventAdmin of a new event is the platform's job, not an event's.
   */
  private void farolAdmin(UUID actorUserId, UUID eventId, Instant at) {
    if (!roles.rolesOf(actorUserId).contains(GlobalRole.FarolAdmin)) {
      audit.refused(actorUserId, eventId, Refusal.NOT_A_FAROL_ADMIN, at);
      throw new NotAFarolAdminException();
    }
  }

  /**
   * The account being named must exist and be usable. A user with no account, a deleted one and one whose
   * deletion is pending are the same {@code 404} as an unknown event: the FarolAdmin learns nothing about who
   * does or does not have an account here beyond what the e-mail lookup already told them (D7).
   */
  private pt.isep.labdsof.authusers.domain.UserAccount namedAccount(UUID actorUserId, UUID eventId, UUID userId,
      Instant at) {
    if (tombstones.exists(userId)) {
      audit.refused(actorUserId, eventId, Refusal.NO_SUCH_MEMBER, at);
      throw new EventNotFoundException();
    }
    var account = accounts.findById(userId).orElseThrow(() -> {
      audit.refused(actorUserId, eventId, Refusal.NO_SUCH_MEMBER, at);
      return new EventNotFoundException();
    });
    if (account.status() != AccountStatus.Active) {
      audit.refused(actorUserId, eventId, Refusal.NO_SUCH_MEMBER, at);
      throw new EventNotFoundException();
    }
    return account;
  }

  /** To the microsecond, as the database stores it and the events carry it. */
  private Instant now() {
    return clock.instant().truncatedTo(ChronoUnit.MICROS);
  }
}
