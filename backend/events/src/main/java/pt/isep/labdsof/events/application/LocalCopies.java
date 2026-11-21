package pt.isep.labdsof.events.application;

import java.util.Optional;
import java.util.UUID;
import pt.isep.labdsof.events.domain.Membership;

/** Port: the local copy of the memberships published by Auth &amp; Users (ADR-004). */
public interface LocalCopies {

  /** Always both keys: event and user (ADR-003 decision 3). */
  Optional<Membership> membership(UUID eventId, UUID userId);

  /** False when the copy already has this or a newer version. */
  boolean upsertIfNewer(Membership membership);
}
