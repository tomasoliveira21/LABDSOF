package pt.isep.labdsof.events.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.isep.labdsof.events.application.AccessAudit.Refusal;
import pt.isep.labdsof.events.domain.Event;
import pt.isep.labdsof.events.domain.EventNotDraftException;

/**
 * Publishing an event (US-11): an EventAdmin of the event turns its draft into a published event with the join
 * code its link and QR code carry, in one transaction with the EventChanged the other services read (D5) and the
 * line that records who published it and when (D7).
 */
@Service
public class EventService {

  /** A join code already in the table is retried; a second collision is already beyond belief (D1). */
  private static final int ATTEMPTS = 3;

  private final EventReader reader;
  private final Events events;
  private final JoinCodes joinCodes;
  private final Outbox outbox;
  private final EventAudit audit;
  private final Clock clock;

  EventService(EventReader reader, Events events, JoinCodes joinCodes, Outbox outbox, EventAudit audit,
      Clock clock) {
    this.reader = reader;
    this.events = events;
    this.joinCodes = joinCodes;
    this.outbox = outbox;
    this.audit = audit;
    this.clock = clock;
  }

  /**
   * Publishes the draft and answers it as its EventAdmin now sees it, join link included (D6). The domain decides
   * that only a draft publishes, so an event that is not one is refused before anything is written — at the cost
   * of one join code made and dropped, which costs nothing and keeps the rule in one place (D2).
   */
  @Transactional
  public EventView publish(UUID userId, UUID eventId) {
    var event = reader.administrator(userId, eventId).event();
    var at = clock.instant();
    for (var attempt = 0; attempt < ATTEMPTS; attempt++) {
      var published = event.published(joinCodes.next());
      var outcome = events.publish(published, userId, at);
      if (outcome != Events.Write.CODE_TAKEN) {
        return stored(outcome, published, userId, at);
      }
    }
    throw new IllegalStateException("no free join code after " + ATTEMPTS + " attempts");
  }

  /**
   * One EventChanged and one log line for a publish that happened, in the same transaction (D5, D7). A write that
   * matched no row never publishes or records anything: the event moved on and is no longer a draft, it is still a
   * draft at another version (two publishes from one read, D4), or there is no such event, which is the same 404
   * as one the caller may not see.
   */
  private EventView stored(Events.Write outcome, Event published, UUID userId, Instant at) {
    if (outcome == Events.Write.GONE) {
      throw reader.refused(userId, published.eventId(), Refusal.NO_SUCH_EVENT);
    }
    if (outcome == Events.Write.NOT_DRAFT) {
      throw new EventNotDraftException();
    }
    if (outcome == Events.Write.CONFLICT) {
      throw new EventChangedConcurrentlyException();
    }
    outbox.add(EventEvents.changed(published, userId, at));
    audit.published(userId, published.eventId(), at, published.status(), published.version());
    return EventView.of(published);
  }
}
