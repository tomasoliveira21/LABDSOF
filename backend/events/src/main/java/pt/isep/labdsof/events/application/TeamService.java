package pt.isep.labdsof.events.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.isep.labdsof.events.application.AccessAudit.Refusal;
import pt.isep.labdsof.events.domain.EventStatus;
import pt.isep.labdsof.events.domain.HelpCategory;
import pt.isep.labdsof.events.domain.Membership;
import pt.isep.labdsof.events.domain.Team;
import pt.isep.labdsof.events.domain.TeamPermission;

/**
 * The teams of an event (US-16): reading them needs EventAdmin or a team with HelpRequests.Triage or
 * Reports.Review (D4); changing them needs EventAdmin and an event that is still Draft or Published (D2). Every
 * change writes TeamChanged to the outbox in the same transaction (D6), and every refusal that hides an event or
 * a team is recorded (spec #192 D8).
 */
@Service
public class TeamService {

  private final EventReader reader;
  private final Teams teams;
  private final Outbox outbox;
  private final AccessAudit audit;
  private final TeamAudit teamAudit;
  private final Clock clock;

  TeamService(EventReader reader, Teams teams, Outbox outbox, AccessAudit audit, TeamAudit teamAudit,
      Clock clock) {
    this.reader = reader;
    this.teams = teams;
    this.outbox = outbox;
    this.audit = audit;
    this.teamAudit = teamAudit;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public List<Team> list(UUID userId, UUID eventId) {
    var membership = reader.of(userId, eventId).membership();
    var live = teams.liveOfEvent(eventId);
    if (!membership.isEventAdmin() && live.stream().noneMatch(team -> reads(membership, team))) {
      throw new ForbiddenException();
    }
    return live;
  }

  @Transactional
  public Team create(UUID userId, UUID eventId, String name) {
    administrator(userId, eventId);
    var at = clock.instant();
    var team = Team.created(UUID.randomUUID(), eventId, name);
    publish(teams.add(team, userId, at), team, userId, at);
    return team;
  }

  /** Rename and set the help categories: one operation, as the contract and the team form have it (D1). */
  @Transactional
  public Team update(UUID userId, UUID eventId, UUID teamId, String name, List<HelpCategory> categories) {
    administrator(userId, eventId);
    var at = clock.instant();
    var renamed = live(userId, eventId, teamId).renamed(name, Team.categoriesOf(categories));
    publish(teams.replace(renamed, userId, at), renamed, userId, at);
    return renamed;
  }

  /**
   * Soft deletes the team (D5). Its members lose its permissions, and Reports returns the help requests routed to
   * it to the control-room queue and cancels its field tasks when it consumes TeamChanged (AC-3).
   */
  @Transactional
  public void delete(UUID userId, UUID eventId, UUID teamId) {
    administrator(userId, eventId);
    var at = clock.instant();
    var deleted = live(userId, eventId, teamId).asDeleted();
    publish(teams.delete(deleted, userId, at), deleted, userId, at);
  }

  /**
   * Replaces the team's permissions, from the fixed list of features.md §4.2 (US-17): the whole set at once, one
   * version later, recorded with who made it and when (AC-4, D1, D6). The members of the team lose a removed
   * permission on their next action, because nothing is cached here (D8).
   */
  @Transactional
  public Team replacePermissions(UUID userId, UUID eventId, UUID teamId, List<TeamPermission> permissions) {
    administrator(userId, eventId);
    var at = clock.instant();
    var team = live(userId, eventId, teamId).withPermissions(Team.permissionsOf(permissions));
    publish(teams.replacePermissions(team, userId, at), team, userId, at);
    teamAudit.permissionsReplaced(userId, eventId, teamId, at, team.permissions());
    return team;
  }

  /**
   * One TeamChanged for a write that happened, in the same transaction (D6). A write that found no live row any
   * more lost a race with a delete, and is the same 404 as an unknown team; one that found the team at another
   * version lost a race with a change that is still live, and is a 409. Neither publishes anything, so no two
   * TeamChanged can carry the same version (D3).
   */
  private void publish(Teams.Write outcome, Team team, UUID userId, Instant at) {
    if (outcome == Teams.Write.NAME_TAKEN) {
      throw new TeamNameTakenException();
    }
    if (outcome == Teams.Write.GONE) {
      throw teamNotFound(userId, team.eventId(), at);
    }
    if (outcome == Teams.Write.CONFLICT) {
      throw new TeamChangedConcurrentlyException();
    }
    outbox.add(TeamEvents.changed(team, userId, at));
  }

  private static boolean reads(Membership membership, Team team) {
    return membership.teamIds().contains(team.teamId()) && team.grantsTeamReading();
  }

  /** An EventAdmin of an event that still accepts changes (D2); who may see it at all is {@link EventReader}. */
  private void administrator(UUID userId, UUID eventId) {
    if (!open(reader.administrator(userId, eventId).event().status())) {
      throw new EventNotOpenException();
    }
  }

  private static boolean open(EventStatus status) {
    return status == EventStatus.Draft || status == EventStatus.Published;
  }

  private Team live(UUID userId, UUID eventId, UUID teamId) {
    return teams.liveOfEvent(eventId, teamId)
        .orElseThrow(() -> teamNotFound(userId, eventId, clock.instant()));
  }

  private TeamNotFoundException teamNotFound(UUID userId, UUID eventId, Instant at) {
    audit.refused(userId, eventId, at, Refusal.NO_SUCH_TEAM);
    return new TeamNotFoundException();
  }
}
