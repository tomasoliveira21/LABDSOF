package pt.isep.labdsof.authusers.application;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Component;
import pt.isep.labdsof.authusers.application.MemberAudit.Refusal;
import pt.isep.labdsof.authusers.domain.EventMembership;

/**
 * Who may manage an event's members, in one place (spec D2): the caller is an active member of the event and an
 * EventAdmin of it. A caller who is not a member gets the same {@code 404} as an unknown event and a member who is
 * not an EventAdmin gets {@code 403}; both are recorded (US-14).
 *
 * <p>The role is read from this service's own tables, never from the token (ADR-006).
 */
@Component
class EventAdmins {

  private final EventMemberships memberships;
  private final MemberAudit audit;
  private final Clock clock;

  EventAdmins(EventMemberships memberships, MemberAudit audit, Clock clock) {
    this.memberships = memberships;
    this.audit = audit;
    this.clock = clock;
  }

  /** Nothing is returned: the caller's own membership is not what the operation changes, only what allows it. */
  void require(UUID actorUserId, UUID eventId) {
    var membership = memberships.find(eventId, actorUserId).orElseThrow(() -> {
      audit.refused(actorUserId, eventId, Refusal.NOT_A_MEMBER, now());
      return new EventNotFoundException();
    });
    if (!membership.isEventAdmin()) {
      audit.refused(actorUserId, eventId, Refusal.NOT_AN_EVENT_ADMIN, now());
      throw new NotAnEventAdminException();
    }
  }

  private Instant now() {
    return clock.instant().truncatedTo(ChronoUnit.MICROS);
  }
}
