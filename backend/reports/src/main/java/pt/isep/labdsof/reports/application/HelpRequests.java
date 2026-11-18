package pt.isep.labdsof.reports.application;

import java.util.Optional;
import java.util.UUID;
import pt.isep.labdsof.reports.domain.HelpRequest;

/** Port: the help requests of Reports' own database. Every read is filtered by event (ADR-003 decision 3). */
public interface HelpRequests {

  /** Saves a new request; false when the same requester already sent this clientRequestId to the event. */
  boolean insertIfNew(HelpRequest request);

  Optional<HelpRequest> find(UUID eventId, UUID requestId);

  Optional<HelpRequest> findByClientRequestId(UUID eventId, String requesterId, UUID clientRequestId);
}
