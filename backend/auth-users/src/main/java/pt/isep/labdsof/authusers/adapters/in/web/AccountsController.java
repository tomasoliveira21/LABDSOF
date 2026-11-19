package pt.isep.labdsof.authusers.adapters.in.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import pt.isep.labdsof.authusers.adapters.in.web.generated.api.AccountsApi;
import pt.isep.labdsof.authusers.adapters.in.web.generated.model.AccountSummary;

@RestController
class AccountsController implements AccountsApi {

  @Override
  public ResponseEntity<AccountSummary> findAccountByEmail(String email) {
    throw new NotYetImplementedException("US-06");
  }
}
