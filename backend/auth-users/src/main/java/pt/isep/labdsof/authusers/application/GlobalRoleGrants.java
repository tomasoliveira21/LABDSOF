package pt.isep.labdsof.authusers.application;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import pt.isep.labdsof.authusers.domain.GlobalRole;

public interface GlobalRoleGrants {

  /** Version 1, granted by the system (no granting user). */
  void grantBySystem(UUID userId, GlobalRole role, Instant at);

  Set<GlobalRole> rolesOf(UUID userId);
}
