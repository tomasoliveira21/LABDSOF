package pt.isep.labdsof.events.application;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;
import pt.isep.labdsof.events.application.AccessAudit.Refusal;
import pt.isep.labdsof.events.domain.Event;
import pt.isep.labdsof.events.domain.Membership;
import pt.isep.labdsof.events.domain.Permission;

/**
 * Who may reach an event, in one place (spec #192 D5, US-11 D9): the event exists, the caller is an active member
 * of it, and its status is one the member may see. Anything else is the same {@code 404} as an unknown event, and
 * every refusal is recorded with the user, the event and the time (US-14, spec #192 D8).
 *
 * <p>The permission is never read from the token (ADR-006): the caller checks what the operation needs on the
 * {@link Reader} this returns.
 */
@Component
class EventReader {

  /** The event and what the membership copy says about the caller, read once per operation. */
  record Reader(Event event, Membership membership) {}

  private final Events events;
  private final Teams teams;
  private final LocalCopies copies;
  private final AccessAudit audit;
  private final Clock clock;

  EventReader(Events events, Teams teams, LocalCopies copies, AccessAudit audit, Clock clock) {
    this.events = events;
    this.teams = teams;
    this.copies = copies;
    this.audit = audit;
    this.clock = clock;
  }

  Reader of(UUID userId, UUID eventId) {
    var event = events.findById(eventId).orElseThrow(() -> refused(userId, eventId, Refusal.NO_SUCH_EVENT));
    var membership = copies.membership(eventId, userId).filter(Membership::isActive)
        .orElseThrow(() -> refused(userId, eventId, Refusal.NOT_A_MEMBER));
    if (!membership.canSee(event.status())) {
      throw refused(userId, eventId, Refusal.NOT_VISIBLE);
    }
    return new Reader(event, membership);
  }

  /**
   * An EventAdmin of the event. The visibility above is checked first, so a participant never learns that a draft
   * event exists: that is the {@code 404} of {@link #of}, not this {@code 403} (US-11 D3).
   */
  Reader administrator(UUID userId, UUID eventId) {
    var reader = of(userId, eventId);
    if (!reader.membership().isEventAdmin()) {
      throw new ForbiddenException();
    }
    return reader;
  }

  /**
   * A member who may use an operation that needs one team permission (US-19 D6): an EventAdmin of the event, who
   * has every permission in it without being in a team, or a member of a live team of theirs that grants it
   * (features.md §4.2). As {@link #administrator}, the visibility of {@link #of} is checked first, so the
   * {@code 403} only ever reaches someone who may see the event.
   */
  Reader with(UUID userId, UUID eventId, Permission permission) {
    var reader = of(userId, eventId);
    if (!reader.membership().isEventAdmin() && !granted(reader.membership(), permission)) {
      throw new ForbiddenException();
    }
    return reader;
  }

  /** Read from this service's own copies — the member's teams and the teams' rows — never from the token. */
  private boolean granted(Membership membership, Permission permission) {
    return teams.liveOfEvent(membership.eventId()).stream()
        .filter(team -> membership.teamIds().contains(team.teamId()))
        .anyMatch(team -> team.grants(permission));
  }

  /** The refusal of US-14, recorded before the caller ever sees the exception. */
  EventNotFoundException refused(UUID userId, UUID eventId, Refusal refusal) {
    audit.refused(userId, eventId, clock.instant(), refusal);
    return new EventNotFoundException();
  }
}
