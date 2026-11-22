package pt.isep.labdsof.transports.application;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.isep.labdsof.transports.domain.EventCopy;
import pt.isep.labdsof.transports.domain.Membership;
import pt.isep.labdsof.transports.domain.Team;

/** Applies the consumed events to the local copies: idempotent by messageId and by version (ADR-004, spec D3). */
@Service
public class LocalCopyService {

  /** {@code OTHER_EVENT}: a TeamChanged for a team stored under another event (#214 D7), a producer bug. */
  public enum Outcome { APPLIED, STALE, DUPLICATE, OTHER_EVENT }

  private final ProcessedMessages processed;
  private final LocalCopies copies;

  public LocalCopyService(ProcessedMessages processed, LocalCopies copies) {
    this.processed = processed;
    this.copies = copies;
  }

  @Transactional
  public Outcome apply(UUID messageId, Membership membership) {
    if (!processed.markProcessed(messageId)) {
      return Outcome.DUPLICATE;
    }
    return copies.upsertIfNewer(membership) ? Outcome.APPLIED : Outcome.STALE;
  }

  @Transactional
  public Outcome apply(UUID messageId, EventCopy event) {
    if (!processed.markProcessed(messageId)) {
      return Outcome.DUPLICATE;
    }
    return copies.upsertIfNewer(event) ? Outcome.APPLIED : Outcome.STALE;
  }

  @Transactional
  public Outcome apply(UUID messageId, Team team) {
    if (!processed.markProcessed(messageId)) {
      return Outcome.DUPLICATE;
    }
    var stored = copies.team(team.teamId());
    if (stored.isPresent() && !stored.get().eventId().equals(team.eventId())) {
      return Outcome.OTHER_EVENT;
    }
    return copies.upsertIfNewer(team) ? Outcome.APPLIED : Outcome.STALE;
  }
}
