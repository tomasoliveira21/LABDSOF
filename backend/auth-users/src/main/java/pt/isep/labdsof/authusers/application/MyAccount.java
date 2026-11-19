package pt.isep.labdsof.authusers.application;

import java.util.Set;
import pt.isep.labdsof.authusers.domain.GlobalRole;
import pt.isep.labdsof.authusers.domain.UserAccount;

public record MyAccount(UserAccount account, Set<GlobalRole> globalRoles) {

  public MyAccount {
    globalRoles = Set.copyOf(globalRoles);
  }
}
