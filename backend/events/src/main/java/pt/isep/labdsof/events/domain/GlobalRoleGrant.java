package pt.isep.labdsof.events.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * One user's hold on a platform role, as {@code GlobalRoleChanged} carries it: full state with a version (API
 * outline §7.3), so a replayed or reordered message is decided by the version alone. A revoke is
 * {@code granted = false} and keeps the row, so a later message carrying an older {@code granted = true} is
 * still rejected by the version (US-06 D2).
 */
public record GlobalRoleGrant(UUID userId, GlobalRole role, boolean granted, long version) {

  public GlobalRoleGrant {
    Objects.requireNonNull(userId, "userId");
    Objects.requireNonNull(role, "role");
    if (version < 1) {
      throw new IllegalArgumentException("version must be at least 1");
    }
  }
}
