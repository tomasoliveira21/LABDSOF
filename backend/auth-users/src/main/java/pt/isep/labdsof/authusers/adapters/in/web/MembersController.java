package pt.isep.labdsof.authusers.adapters.in.web;

import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import pt.isep.labdsof.authusers.adapters.in.web.generated.api.MembersApi;
import pt.isep.labdsof.authusers.adapters.in.web.generated.model.EventRole;
import pt.isep.labdsof.authusers.adapters.in.web.generated.model.EventStatus;
import pt.isep.labdsof.authusers.adapters.in.web.generated.model.Member;
import pt.isep.labdsof.authusers.adapters.in.web.generated.model.Membership;
import pt.isep.labdsof.authusers.adapters.in.web.generated.model.NameEventAdmin;
import pt.isep.labdsof.authusers.application.JoinEventService;
import pt.isep.labdsof.authusers.application.MemberQueryService;
import pt.isep.labdsof.authusers.application.NameEventAdminService;
import pt.isep.labdsof.authusers.application.TeamMemberService;

@RestController
class MembersController implements MembersApi {

  private static final Logger log = LoggerFactory.getLogger(MembersController.class);

  private final MemberQueryService members;
  private final TeamMemberService teams;
  private final JoinEventService join;
  private final NameEventAdminService name;

  MembersController(MemberQueryService members, TeamMemberService teams, JoinEventService join,
      NameEventAdminService name) {
    this.members = members;
    this.teams = teams;
    this.join = join;
    this.name = name;
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

  /**
   * US-06: {@code 201} whether the call changed the membership or not, because the contract declares one success
   * code and a retried call must not read as an error (D6).
   */
  @Override
  public ResponseEntity<Member> nameEventAdmin(UUID eventId, NameEventAdmin nameEventAdmin) {
    var actor = TokenIdentity.current().userId();
    log.info("POST /events/{}/members by userId={}", eventId, actor);
    var named = name.name(actor, eventId, nameEventAdmin.userId());
    var membership = named.membership();
    return ResponseEntity.status(HttpStatus.CREATED).body(new Member(membership.userId(), named.displayName(),
        EventRole.fromValue(membership.role().name()), membership.teamIds()));
  }

  /** US-12: {@code 201} when this call made the caller a member, {@code 200} when they already were (D3). */
  @Override
  public ResponseEntity<Membership> joinEvent(UUID eventId) {
    var userId = TokenIdentity.current().userId();
    log.info("POST /events/{}/members/me by userId={}", eventId, userId);
    var joined = join.join(userId, eventId);
    var membership = joined.membership();
    var body = new Membership(membership.eventId(), joined.eventName(),
        EventStatus.fromValue(joined.eventStatus().name()), EventRole.fromValue(membership.role().name()),
        membership.teamIds());
    return ResponseEntity.status(joined.created() ? HttpStatus.CREATED : HttpStatus.OK).body(body);
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
