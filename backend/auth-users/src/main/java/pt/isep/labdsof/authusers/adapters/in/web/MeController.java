package pt.isep.labdsof.authusers.adapters.in.web;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import pt.isep.labdsof.authusers.adapters.in.web.generated.api.MeApi;
import pt.isep.labdsof.authusers.adapters.in.web.generated.model.GlobalRole;
import pt.isep.labdsof.authusers.adapters.in.web.generated.model.Me;
import pt.isep.labdsof.authusers.adapters.in.web.generated.model.Membership;
import pt.isep.labdsof.authusers.application.MeService;

@RestController
class MeController implements MeApi {

  private static final Logger log = LoggerFactory.getLogger(MeController.class);

  private final MeService service;

  MeController(MeService service) {
    this.service = service;
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
    throw new NotYetImplementedException("US-01");
  }
}
