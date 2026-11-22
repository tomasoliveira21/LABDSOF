package pt.isep.labdsof.events.adapters.in.web;

import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import pt.isep.labdsof.events.adapters.in.web.generated.api.TeamsApi;
import pt.isep.labdsof.events.adapters.in.web.generated.model.NewTeam;
import pt.isep.labdsof.events.adapters.in.web.generated.model.Team;
import pt.isep.labdsof.events.adapters.in.web.generated.model.TeamChange;
import pt.isep.labdsof.events.adapters.in.web.generated.model.TeamPermission;

@RestController
class TeamsController implements TeamsApi {

  @Override
  public ResponseEntity<List<Team>> listTeams(UUID eventId) {
    throw new NotYetImplementedException("US-16");
  }

  @Override
  public ResponseEntity<Team> createTeam(UUID eventId, NewTeam newTeam) {
    throw new NotYetImplementedException("US-16");
  }

  @Override
  public ResponseEntity<Team> updateTeam(UUID eventId, UUID teamId, TeamChange teamChange) {
    throw new NotYetImplementedException("US-16 and US-17");
  }

  @Override
  public ResponseEntity<Void> deleteTeam(UUID eventId, UUID teamId) {
    throw new NotYetImplementedException("US-16");
  }

  @Override
  public ResponseEntity<Team> replaceTeamPermissions(UUID eventId, UUID teamId, List<TeamPermission> teamPermission) {
    throw new NotYetImplementedException("US-17");
  }
}
