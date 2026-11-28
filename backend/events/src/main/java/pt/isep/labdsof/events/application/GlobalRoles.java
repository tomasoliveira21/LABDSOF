package pt.isep.labdsof.events.application;

import java.util.UUID;
import pt.isep.labdsof.events.domain.GlobalRoleGrant;

/**
 * Port: the local copy of the platform roles Auth &amp; Users publishes (ADR-004, US-06 D1). The role is read
 * from here and <em>never</em> from the token (ADR-006), so a caller whose {@code GlobalRoleChanged} has not
 * arrived yet is simply not a FarolAdmin.
 */
public interface GlobalRoles {

  /** True only while the copy says the role is held: an absent row and a revoked one both answer false. */
  boolean isFarolAdmin(UUID userId);

  /** False when the copy already has this or a newer version (US-06 D2). */
  boolean upsertIfNewer(GlobalRoleGrant grant);
}
