package pt.isep.labdsof.events.application;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.isep.labdsof.events.application.AccessAudit.Refusal;
import pt.isep.labdsof.events.domain.Membership;

/** GET /events/{eventId}: spec D5 visibility, every refusal recorded (spec D8). */
@Service
public class EventQueryService {

  private final Events events;
  private final LocalCopies copies;
  private final AccessAudit audit;
  private final Clock clock;

  public EventQueryService(Events events, LocalCopies copies, AccessAudit audit, Clock clock) {
    this.events = events;
    this.copies = copies;
    this.audit = audit;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public EventView get(UUID userId, UUID eventId) {
    var event = events.findById(eventId);
    if (event.isEmpty()) {
      throw refused(userId, eventId, Refusal.NO_SUCH_EVENT);
    }
    var membership = copies.membership(eventId, userId).filter(Membership::isActive);
    if (membership.isEmpty()) {
      throw refused(userId, eventId, Refusal.NOT_A_MEMBER);
    }
    if (!membership.get().canSee(event.get().status())) {
      throw refused(userId, eventId, Refusal.NOT_VISIBLE);
    }
    return EventView.of(event.get());
  }

  private EventNotFoundException refused(UUID userId, UUID eventId, Refusal refusal) {
    audit.refused(userId, eventId, clock.instant(), refusal);
    return new EventNotFoundException();
  }
}
