package pt.isep.labdsof.reports.application;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import pt.isep.labdsof.reports.domain.Membership;
import pt.isep.labdsof.reports.domain.Team;

/** Port: the local copies of other services' data (ADR-004 decision 2). */
public interface LocalCopies {

  Optional<Membership> membership(UUID eventId, String userId);

  List<Team> teams(UUID eventId, Collection<UUID> teamIds);

  /** Whether a TeamChanged of the event was applied after {@code since} (spec US-34-default-team D6). */
  boolean teamsChangedSince(UUID eventId, Instant since);

  /** Every team of the event, deleted ones included. */
  List<Team> teams(UUID eventId);

  /** Inserts or replaces the copy; false when the copy already has this or a newer version. */
  boolean upsertIfNewer(Membership membership);

  /** Inserts or replaces the copy; false when the copy already has this or a newer version. */
  boolean upsertIfNewer(Team team);
}
