package pt.isep.labdsof.events.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.isep.labdsof.events.application.AccessAudit.Refusal;
import pt.isep.labdsof.events.domain.DateRange;
import pt.isep.labdsof.events.domain.Event;
import pt.isep.labdsof.events.domain.EventNotDraftException;
import pt.isep.labdsof.events.domain.InvalidEventException;

/**
 * The writes and the platform reads of an event. US-06 creates the draft and US-07 lists every event with its
 * basic details, both for the FarolAdmin and both gated on the local copy of the role (US-06 D1); US-11 turns a
 * draft into a published event with the join code its link and QR code carry, for its EventAdmin. Every one of
 * them writes its EventChanged in the same transaction (D5) and the line that records who acted and when (D7).
 */
@Service
public class EventService {

  private final EventReader reader;
  private final Events events;
  private final GlobalRoles globalRoles;
  private final JoinCodes joinCodes;
  private final Outbox outbox;
  private final EventAudit audit;
  private final Clock clock;

  EventService(EventReader reader, Events events, GlobalRoles globalRoles, JoinCodes joinCodes, Outbox outbox,
      EventAudit audit, Clock clock) {
    this.reader = reader;
    this.events = events;
    this.globalRoles = globalRoles;
    this.joinCodes = joinCodes;
    this.outbox = outbox;
    this.audit = audit;
    this.clock = clock;
  }

  /**
   * Creates the draft the FarolAdmin asked for (US-06), with the first {@code EventChanged} any event ever gets
   * in the same transaction (D5) — which is what lets Auth &amp; Users stop answering {@code 404} to the call
   * that names its first EventAdmin.
   *
   * <p>The creator is <em>not</em> made a member (D3): the FarolAdmin administers the platform and reads this
   * event back only through {@link #list}, never through the event itself. The role comes from the local copy,
   * never from the token (D1).
   */
  @Transactional
  public EventView create(UUID userId, String name, String organiserName, LocalDate start, LocalDate end,
      String description) {
    var at = clock.instant();
    platformAdmin(userId, at, "createEvent");
    var event = Event.created(UUID.randomUUID(), name, organiserName, dateRange(start, end), description);
    events.insert(event, userId, at);
    outbox.add(EventEvents.changed(event, userId, at));
    audit.created(userId, event.eventId(), at);
    return EventView.of(event);
  }

  /**
   * Every event with the basic details of {@code features.md} §4.1 (US-07): the FarolAdmin's whole view of the
   * platform, and the only one. Opening any of these events is still the {@code 404} of US-14.
   */
  @Transactional(readOnly = true)
  public List<EventSummaryView> list(UUID userId) {
    platformAdmin(userId, clock.instant(), "listEvents");
    return events.summaries();
  }

  /**
   * The one rule of the body the generated bean validation cannot express (D8): a range that ends before it
   * starts. {@code DateRange} refuses it with an {@code IllegalArgumentException}, which would leave the request
   * as a {@code 500}; the caller described the event wrongly, so it is an {@link InvalidEventException} and a
   * {@code 400}.
   */
  private static DateRange dateRange(LocalDate start, LocalDate end) {
    if (start == null || end == null) {
      throw new InvalidEventException("dates are required");
    }
    if (end.isBefore(start)) {
      throw new InvalidEventException("an event cannot end before it starts");
    }
    return new DateRange(start, end);
  }

  /** The gate of D1, recorded when it refuses, as every refusal of this service is (US-14, #192 D8). */
  private void platformAdmin(UUID userId, Instant at, String operation) {
    if (!globalRoles.isFarolAdmin(userId)) {
      audit.notPlatformAdmin(userId, at, operation);
      throw new NotPlatformAdminException();
    }
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
    var published = event.published(joinCodes.next());
    return stored(events.publish(published, userId, at), published, userId, at);
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
