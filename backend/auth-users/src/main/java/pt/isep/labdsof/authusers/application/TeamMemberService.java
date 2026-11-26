package pt.isep.labdsof.authusers.application;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.isep.labdsof.authusers.application.MemberAudit.Refusal;
import pt.isep.labdsof.authusers.domain.EventMembership;

/**
 * Adding a member of an event to one of its teams, and removing them (US-18). The teams are Events'
 * (US-16, #65) and reach this service as copies; the assignment, the membership's version and the
 * {@code MembershipChanged} that gives the member the team's permissions everywhere are this service's (spec D4,
 * D6, API outline §2).
 */
@Service
public class TeamMemberService {

  private final EventAdmins admins;
  private final EventMemberships memberships;
  private final TeamCopies teams;
  private final Outbox outbox;
  private final MemberAudit audit;
  private final Clock clock;

  TeamMemberService(EventAdmins admins, EventMemberships memberships, TeamCopies teams, Outbox outbox,
      MemberAudit audit, Clock clock) {
    this.admins = admins;
    this.memberships = memberships;
    this.teams = teams;
    this.outbox = outbox;
    this.audit = audit;
    this.clock = clock;
  }

  /**
   * Puts a member of the event in one of its teams. Idempotent: a member who is already in the team keeps the
   * version they have, and nothing is published or logged (D3), so the back office may retry the call.
   */
  @Transactional
  public void add(UUID actorUserId, UUID eventId, UUID userId, UUID teamId) {
    admins.require(actorUserId, eventId);
    if (!teams.isLiveTeamOf(eventId, teamId)) {
      audit.refused(actorUserId, eventId, Refusal.NO_SUCH_TEAM, now());
      throw new EventNotFoundException();
    }
    var membership = locked(actorUserId, eventId, userId);
    var updated = membership.withTeam(teamId);
    if (updated == membership) {
      return;
    }
    var at = now();
    memberships.addToTeam(updated, teamId, actorUserId, at);
    published(updated, at, actorUserId);
    audit.teamAssigned(actorUserId, eventId, userId, teamId, at);
  }

  /** Takes a member out of a team. Idempotent in the same way: a member who is not in it keeps their version. */
  @Transactional
  public void remove(UUID actorUserId, UUID eventId, UUID userId, UUID teamId) {
    admins.require(actorUserId, eventId);
    var membership = locked(actorUserId, eventId, userId);
    var updated = membership.withoutTeam(teamId);
    if (updated == membership) {
      // Nothing the member has changes, so nothing is published (D3). A team Events deleted is already out of
      // their teams, and this is what clears the row it left behind (review of #255)
      memberships.clearAssignment(eventId, userId, teamId);
      return;
    }
    var at = now();
    memberships.removeFromTeam(updated, teamId, actorUserId, at);
    published(updated, at, actorUserId);
    audit.teamUnassigned(actorUserId, eventId, userId, teamId, at);
  }

  /**
   * The membership this call changes, locked for the transaction (D7). A user who is not a member of the event is
   * the {@code 404} of AC-2 — the same one an unknown event gives.
   */
  private EventMembership locked(UUID actorUserId, UUID eventId, UUID userId) {
    return memberships.lockForUpdate(eventId, userId).orElseThrow(() -> {
      audit.refused(actorUserId, eventId, Refusal.NO_SUCH_MEMBER, now());
      return new EventNotFoundException();
    });
  }

  /** The full state of the membership, in the same transaction as the write (ADR-004, D6). */
  private void published(EventMembership updated, Instant at, UUID actorUserId) {
    outbox.add(OutgoingEvent.membershipChanged(UUID.randomUUID(), updated, at, actorUserId.toString()));
  }

  /** To the microsecond, as the database stores it and the events carry it. */
  private Instant now() {
    return clock.instant().truncatedTo(ChronoUnit.MICROS);
  }
}
