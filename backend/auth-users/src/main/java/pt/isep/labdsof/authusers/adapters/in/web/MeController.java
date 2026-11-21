package pt.isep.labdsof.authusers.adapters.in.web;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import pt.isep.labdsof.authusers.adapters.in.web.generated.api.MeApi;
import pt.isep.labdsof.authusers.adapters.in.web.generated.model.EventRole;
import pt.isep.labdsof.authusers.adapters.in.web.generated.model.EventStatus;
import pt.isep.labdsof.authusers.adapters.in.web.generated.model.GlobalRole;
import pt.isep.labdsof.authusers.adapters.in.web.generated.model.Me;
import pt.isep.labdsof.authusers.adapters.in.web.generated.model.Membership;
import pt.isep.labdsof.authusers.application.MeService;
import pt.isep.labdsof.authusers.application.MyMembershipService;

@RestController
class MeController implements MeApi {

  private static final Logger log = LoggerFactory.getLogger(MeController.class);

  private final MeService service;

  private final MyMembershipService memberships;

  MeController(MeService service, MyMembershipService memberships) {
    this.service = service;
    this.memberships = memberships;
  }

  @Override
  public ResponseEntity<Me> getMe() {
    var identity = TokenIdentity.current();
    // Only the user id: never the e-mail or the name (M-10)
    log.info("GET /me by userId={}", identity.userId());
    var mine = service.me(identity);
    var account = mine.account();
    return ResponseEntity.ok(new Me(account.userId(), account.email(), account.displayName(),
        mine.globalRoles().stream().map(role -> GlobalRole.fromValue(role.name())).sorted().toList()));
  }

  @Override
  public ResponseEntity<List<Membership>> getMyMemberships() {
    var userId = TokenIdentity.current().userId();
    log.info("GET /me/memberships by userId={}", userId);
    return ResponseEntity.ok(memberships.list(userId).stream()
        .map(entry -> new Membership(entry.eventId(), entry.eventName(), EventStatus.fromValue(entry.eventStatus().name()),
            EventRole.fromValue(entry.role()), entry.teamIds()))
        .toList());
  }
}
