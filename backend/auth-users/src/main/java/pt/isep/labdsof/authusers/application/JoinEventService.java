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

/**
 * Joining an event by its QR code or link (US-12): the caller becomes a {@code RegularUser} of it, which is what
 * every other service then reads from its copy of {@code MembershipChanged} (US-18 D6). Only a published event can
 * be joined, read from this service's copy of {@code EventChanged} and never from a call to Events (ADR-006).
 *
 * <p>The join carries no code: the contract takes no body (spec D1). The link's secrecy therefore protects
 * nothing today — the known limit this story records.
 */
@Service
public class JoinEventService {

  /**
   * The membership as the caller now has it, and whether this call is what created it: {@code 201} when it did,
   * {@code 200} when they were already a member (D3).
   */
  public record Joined(EventMembership membership, String eventName, MyMemberships.Status eventStatus,
      boolean created) {}

  private final UserAccounts accounts;
  private final Tombstones tombstones;
  private final EventCopies events;
  private final EventMemberships memberships;
  private final Outbox outbox;
  private final MemberAudit audit;
  private final Clock clock;

  JoinEventService(UserAccounts accounts, Tombstones tombstones, EventCopies events, EventMemberships memberships,
      Outbox outbox, MemberAudit audit, Clock clock) {
    this.accounts = accounts;
    this.tombstones = tombstones;
    this.events = events;
    this.memberships = memberships;
    this.outbox = outbox;
    this.audit = audit;
    this.clock = clock;
  }

  /**
   * Makes the caller a member of the event, or answers the membership they already have without changing it
   * (D3, D4). A draft, suspended or closed event, an event with no copy and an unknown event are the same
   * {@code 404} (D2).
   */
  @Transactional
  public Joined join(UUID userId, UUID eventId) {
    available(userId);
    var event = events.find(eventId).filter(EventCopies.Copy::isPublished).orElseThrow(() -> {
      audit.refused(userId, eventId, Refusal.NOT_JOINABLE, now());
      return new EventNotFoundException();
    });
    var existing = memberships.find(eventId, userId);
    if (existing.isPresent()) {
      return new Joined(existing.get(), event.name(), event.status(), false);
    }
    var membership = new EventMembership(eventId, userId, EventRole.RegularUser, List.of(), 1);
    var at = now();
    if (!memberships.insertIfAbsent(membership, userId, at)) {
      // Another scan of the same code won the race: that one published the event, this one only answers (D5)
      return new Joined(memberships.find(eventId, userId).orElseThrow(EventNotFoundException::new), event.name(),
          event.status(), false);
    }
    outbox.add(OutgoingEvent.membershipChanged(UUID.randomUUID(), membership, at, userId.toString()));
    audit.joined(userId, eventId, at);
    return new Joined(membership, event.name(), event.status(), true);
  }

  /** The same account rules as every other operation of this service (#191 D5 to D7, spec D6). */
  private void available(UUID userId) {
    if (tombstones.exists(userId)) {
      throw new AccountUnavailableException();
    }
    var account = accounts.findById(userId).orElseThrow(AccountUnavailableException::new);
    if (account.status() == AccountStatus.Disabled) {
      throw new AccountDisabledException();
    }
    if (account.status() == AccountStatus.DeletionPending) {
      throw new AccountUnavailableException();
    }
  }

  /** To the microsecond, as the database stores it and the events carry it. */
  private Instant now() {
    return clock.instant().truncatedTo(ChronoUnit.MICROS);
  }
}
