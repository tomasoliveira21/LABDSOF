package pt.isep.labdsof.authusers.adapters.in.web;

import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import pt.isep.labdsof.authusers.adapters.in.web.generated.api.MembersApi;
import pt.isep.labdsof.authusers.adapters.in.web.generated.model.EventRole;
import pt.isep.labdsof.authusers.adapters.in.web.generated.model.Member;
import pt.isep.labdsof.authusers.adapters.in.web.generated.model.Membership;
import pt.isep.labdsof.authusers.adapters.in.web.generated.model.NameEventAdmin;

@RestController
class MembersController implements MembersApi {

  @Override
  public ResponseEntity<List<Member>> listMembers(UUID eventId, EventRole role, UUID teamId) {
    throw new NotYetImplementedException("US-18");
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
    throw new NotYetImplementedException("US-18");
  }

  @Override
  public ResponseEntity<Void> removeMemberFromTeam(UUID eventId, UUID userId, UUID teamId) {
    throw new NotYetImplementedException("US-18");
  }
}
