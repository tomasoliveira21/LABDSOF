package pt.isep.labdsof.authusers.adapters.in.web;

import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import pt.isep.labdsof.authusers.adapters.in.web.generated.api.MembersApi;
import pt.isep.labdsof.authusers.adapters.in.web.generated.model.EventRole;
import pt.isep.labdsof.authusers.adapters.in.web.generated.model.Member;
import pt.isep.labdsof.authusers.adapters.in.web.generated.model.Membership;
import pt.isep.labdsof.authusers.adapters.in.web.generated.model.NameEventAdmin;
import pt.isep.labdsof.authusers.application.MemberQueryService;
import pt.isep.labdsof.authusers.application.TeamMemberService;

@RestController
class MembersController implements MembersApi {

  private static final Logger log = LoggerFactory.getLogger(MembersController.class);

  private final MemberQueryService members;
  private final TeamMemberService teams;

  MembersController(MemberQueryService members, TeamMemberService teams) {
    this.members = members;
    this.teams = teams;
  }

  @Override
  public ResponseEntity<List<Member>> listMembers(UUID eventId, EventRole role, UUID teamId) {
    var actor = TokenIdentity.current().userId();
    log.info("GET /events/{}/members by userId={}", eventId, actor);
    var found = members.list(actor, eventId, domainRole(role), teamId);
    return ResponseEntity.ok(found.stream()
        .map(member -> new Member(member.userId(), member.displayName(),
            EventRole.fromValue(member.role().name()), member.teamIds()))
        .toList());
  }

  /** The filter as the domain knows it; the generated enum and the domain one share a name, hence the helper. */
  private static pt.isep.labdsof.authusers.domain.EventRole domainRole(EventRole role) {
    return role == null ? null : pt.isep.labdsof.authusers.domain.EventRole.valueOf(role.getValue());
  }

  @Override
  public ResponseEntity<Member> nameEventAdmin(UUID eventId, NameEventAdmin nameEventAdmin) {
    throw new NotYetImplementedException("US-06");
  }

  @Override
  public ResponseEntity<Membership> joinEvent(UUID eventId) {
    throw new NotYetImplementedException("US-12");
  }

  @Override
  public ResponseEntity<Void> addMemberToTeam(UUID eventId, UUID userId, UUID teamId) {
    var actor = TokenIdentity.current().userId();
    log.info("PUT /events/{}/members/{}/teams/{} by userId={}", eventId, userId, teamId, actor);
    teams.add(actor, eventId, userId, teamId);
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<Void> removeMemberFromTeam(UUID eventId, UUID userId, UUID teamId) {
    var actor = TokenIdentity.current().userId();
    log.info("DELETE /events/{}/members/{}/teams/{} by userId={}", eventId, userId, teamId, actor);
    teams.remove(actor, eventId, userId, teamId);
    return ResponseEntity.noContent().build();
  }
}
