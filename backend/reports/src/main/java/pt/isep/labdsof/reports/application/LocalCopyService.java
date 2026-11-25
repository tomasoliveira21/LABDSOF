package pt.isep.labdsof.reports.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.isep.labdsof.reports.domain.Membership;
import pt.isep.labdsof.reports.domain.Team;

/**
 * Applies MembershipChanged and TeamChanged to the local copies: idempotent and by version (ADR-004). A team's deletion
 * also sends the requests routed to it back to the control room's queue and cancels their tasks (US-29 D6, US-16).
 */
@Service
public class LocalCopyService {

  public enum Outcome { APPLIED, STALE, DUPLICATE }

  private final ProcessedMessages processed;
  private final LocalCopies copies;
  private final HelpRequests helpRequests;
  private final FieldTasks fieldTasks;
  private final Clock clock;

  public LocalCopyService(ProcessedMessages processed, LocalCopies copies, HelpRequests helpRequests,
      FieldTasks fieldTasks, Clock clock) {
    this.processed = processed;
    this.copies = copies;
    this.helpRequests = helpRequests;
    this.fieldTasks = fieldTasks;
    this.clock = clock;
  }

  @Transactional
  public Outcome apply(UUID messageId, Membership membership) {
    if (!processed.markProcessed(messageId)) {
      return Outcome.DUPLICATE;
    }
    return copies.upsertIfNewer(membership) ? Outcome.APPLIED : Outcome.STALE;
  }

  @Transactional
  public Outcome apply(UUID messageId, Team team) {
    if (!processed.markProcessed(messageId)) {
      return Outcome.DUPLICATE;
    }
    if (!copies.upsertIfNewer(team)) {
      return Outcome.STALE;
    }
    if (team.deleted()) {
      returnToQueue(team);
    }
    return Outcome.APPLIED;
  }

  /**
   * After the team's copy, which the upsert keeps locked: each request routed to it, locked as a member's decision
   * locks it, then its task (the route's order, D5). No history entry: a system change (D6).
   */
  private void returnToQueue(Team team) {
    var now = clock.instant();
    for (var id : helpRequests.routedTo(team.eventId(), team.teamId())) {
      var request = helpRequests.findForUpdate(team.eventId(), id).orElseThrow();
      var returned = request.returnFrom(team.teamId(), now);
      if (returned != request) {
        helpRequests.saveDecision(returned, null, List.of());
        fieldTasks.cancelLive(team.eventId(), id, returned.updatedAt());
      }
    }
  }
}
