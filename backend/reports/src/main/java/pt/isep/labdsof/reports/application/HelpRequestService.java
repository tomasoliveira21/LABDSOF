package pt.isep.labdsof.reports.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.isep.labdsof.reports.domain.EventAccess;
import pt.isep.labdsof.reports.domain.HelpRequest;
import pt.isep.labdsof.reports.domain.Location;
import pt.isep.labdsof.reports.domain.Permission;

/** Use cases on help requests that #194 needs: submit (persistence and outbox) and read one. */
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

  private EventAccess access(UUID eventId, String userId) {
    var membership = copies.membership(eventId, userId);
    var teams = membership.map(m -> copies.teams(eventId, m.teamIds())).orElse(List.of());
    return EventAccess.of(membership.orElse(null), teams).orElseThrow(NotFoundException::new);
  }
}
