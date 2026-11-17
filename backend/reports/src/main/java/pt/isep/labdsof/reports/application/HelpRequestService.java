package pt.isep.labdsof.reports.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.isep.labdsof.reports.domain.EventAccess;
import pt.isep.labdsof.reports.domain.HelpRequest;
import pt.isep.labdsof.reports.domain.Location;
import pt.isep.labdsof.reports.domain.Permission;

/** Use cases on help requests: submit (persistence and outbox), read one (#194), the live list (US-26). */
@Service
public class HelpRequestService {

  private final HelpRequests helpRequests;
  private final LocalCopies copies;
  private final Outbox outbox;
  private final Clock clock;

  public HelpRequestService(HelpRequests helpRequests, LocalCopies copies, Outbox outbox, Clock clock) {
    this.helpRequests = helpRequests;
    this.copies = copies;
    this.outbox = outbox;
    this.clock = clock;
  }

  /** The request, and whether this call created it (false: a retry with the same clientRequestId). */
  public record Submitted(HelpRequest request, boolean created) {}

  /** The request as the caller sees it, and whether they get the control-room view. */
  public record Read(HelpRequest request, boolean controlRoom) {}

  /** The requests of one poll, and the serverTime to send as updatedSince next time. */
  public record Changes(List<HelpRequest> items, Instant serverTime) {}

  /**
   * Consecutive polls overlap by this much: updated_at is set before the commit, so a request committed just
   * after a poll read the table is still listed by the next one (spec US-26 D3).
   */
  static final Duration POLL_OVERLAP = Duration.ofSeconds(5);

  /** Saves the request and its HelpRequestSubmitted in one transaction (ADR-004 outbox). */
  @Transactional
  public Submitted submit(UUID eventId, String userId, UUID clientRequestId, String description,
      Location location) {
    access(eventId, userId);
    var request = HelpRequest.submit(UUID.randomUUID(), eventId, userId, clientRequestId, description,
        location, clock.instant());
    if (!helpRequests.insertIfNew(request)) {
      var saved = helpRequests.findByClientRequestId(eventId, userId, clientRequestId)
          .orElseThrow(() -> new IllegalStateException("conflicting submit not visible"));
      // A reused id with other content is a client bug: refuse it rather than drop the new request.
      if (!saved.description().equals(description) || !saved.location().equals(location)) {
        throw new ClientRequestConflictException();
      }
      return new Submitted(saved, false);
    }
    outbox.add(OutgoingEvent.helpRequestSubmitted(UUID.randomUUID(), request));
    return new Submitted(request, true);
  }

  /** The owner gets their request; HelpRequests.Triage gets any request of the event; others get 404. */
  @Transactional(readOnly = true)
  public Read get(UUID eventId, String userId, UUID requestId) {
    var access = access(eventId, userId);
    var request = helpRequests.find(eventId, requestId).orElseThrow(NotFoundException::new)
        .asSeenAt(clock.instant());
    if (access.has(Permission.HELP_REQUESTS_TRIAGE)) {
      return new Read(request, true);
    }
    if (request.requesterId().equals(userId)) {
      return new Read(request, false);
    }
    throw new NotFoundException();
  }

  /**
   * The live list of the control room (US-26): every open request on the first load ({@code since} null),
   * then what changed after {@code since}. Only HelpRequests.Triage (403); a non-member gets 404.
   */
  @Transactional(readOnly = true)
  public Changes list(UUID eventId, String userId, Instant since) {
    if (!access(eventId, userId).has(Permission.HELP_REQUESTS_TRIAGE)) {
      throw new ForbiddenException();
    }
    // To the microsecond, as the database keeps times, so the next updatedSince compares exactly
    var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
    var items = since == null ? helpRequests.open(eventId)
        : helpRequests.candidatesSince(eventId, since).stream().filter(r -> r.changedAfter(since, now)).toList();
    // ponytail: a write slower than POLL_OVERLAP between updated_at and commit is missed; a commit-ordered
    // cursor (snapshot xmin) if one ever is
    return new Changes(items.stream().map(r -> r.asSeenAt(now)).toList(), now.minus(POLL_OVERLAP));
  }

  private EventAccess access(UUID eventId, String userId) {
    var membership = copies.membership(eventId, userId);
    var teams = membership.map(m -> copies.teams(eventId, m.teamIds())).orElse(List.of());
    return EventAccess.of(membership.orElse(null), teams).orElseThrow(NotFoundException::new);
  }
}
