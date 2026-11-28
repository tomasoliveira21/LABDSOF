package pt.isep.labdsof.authusers.adapters.in.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import pt.isep.labdsof.authusers.adapters.in.web.generated.api.AccountsApi;
import pt.isep.labdsof.authusers.adapters.in.web.generated.model.AccountSummary;
import pt.isep.labdsof.authusers.application.AccountLookupService;

@RestController
class AccountsController implements AccountsApi {

  private static final Logger log = LoggerFactory.getLogger(AccountsController.class);

  private final AccountLookupService accounts;

  AccountsController(AccountLookupService accounts) {
    this.accounts = accounts;
  }

  /**
   * US-06: the account to name EventAdmin. The address is <em>not</em> in this line and is never echoed in the
   * answer; the audit records a hash of it instead (US-06 D7, M-10).
   */
  @Override
  public ResponseEntity<AccountSummary> findAccountByEmail(String email) {
    var actor = TokenIdentity.current().userId();
    log.info("GET /accounts?email=<redacted> by userId={}", actor);
    var found = accounts.byEmail(actor, email);
    return ResponseEntity.ok(new AccountSummary(found.userId(), found.displayName()));
  }
}
