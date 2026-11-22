package pt.isep.labdsof.transports.domain;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Local copy of TeamChanged (Events): only what Transports decides with, the permissions (for
 * {@code Transport.Edit}) and whether the team was deleted (API outline 7.4).
 */
public record Team(UUID teamId, UUID eventId, Set<Permission> permissions, boolean deleted, long version) {

  public Team {
    Objects.requireNonNull(teamId, "teamId");
    Objects.requireNonNull(eventId, "eventId");
    if (version < 1) {
      throw new IllegalArgumentException("version must be at least 1");
    }
    permissions = Set.copyOf(permissions); // NPE for a null set or a null element: an invalid message
  }

  /** A deleted team grants nothing. */
  public boolean grants(Permission permission) {
    return !deleted && permissions.contains(permission);
  }
}
