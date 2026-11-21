package pt.isep.labdsof.events.adapters.in.web;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import pt.isep.labdsof.events.adapters.in.web.generated.api.TeamsApi;
import pt.isep.labdsof.events.adapters.in.web.generated.model.NewTeam;
import pt.isep.labdsof.events.adapters.in.web.generated.model.Team;
import pt.isep.labdsof.events.adapters.in.web.generated.model.TeamChange;
import pt.isep.labdsof.events.adapters.in.web.generated.model.TeamPermission;
import pt.isep.labdsof.events.application.TeamService;

@RestController
class TeamsController implements TeamsApi {

  private final TeamService teams;

  TeamsController(TeamService teams) {
    this.teams = teams;
  }

  @Override
  public ResponseEntity<List<Team>> listTeams(UUID eventId) {
    return ResponseEntity.ok(teams.list(TokenIdentity.currentUserId(), eventId).stream()
        .map(TeamResponse::of).toList());
  }

  @Override
  public ResponseEntity<Team> createTeam(UUID eventId, NewTeam newTeam) {
    var created = teams.create(TokenIdentity.currentUserId(), eventId, newTeam.name());
    return ResponseEntity.status(HttpStatus.CREATED).body(TeamResponse.of(created));
  }

  @Override
  public ResponseEntity<Team> updateTeam(UUID eventId, UUID teamId, TeamChange teamChange) {
    var updated = teams.update(TokenIdentity.currentUserId(), eventId, teamId, teamChange.name(),
        TeamResponse.categories(teamChange.handledCategories()));
    return ResponseEntity.ok(TeamResponse.of(updated));
  }

  @Override
  public ResponseEntity<Void> deleteTeam(UUID eventId, UUID teamId) {
    teams.delete(TokenIdentity.currentUserId(), eventId, teamId);
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<Team> replaceTeamPermissions(UUID eventId, UUID teamId, List<TeamPermission> teamPermission) {
    throw new NotYetImplementedException("US-17");
  }
}
