package pt.isep.labdsof.reports.application;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import pt.isep.labdsof.reports.domain.FieldTask;

/** Port: the field tasks of Reports' own database (domain model FieldTask; US-29 D4). */
public interface FieldTasks {

  /** Dispatches an Assist task, Open, of the request to the team; a request has at most one live task. */
  void dispatch(UUID taskId, UUID eventId, UUID requestId, UUID teamId, Instant at);

  /** Cancels the request's live task (Open or Accepted), if it has one (US-29 D2, D6). */
  void cancelLive(UUID eventId, UUID requestId, Instant at);

  /** The live tasks (Open, Accepted) of these teams of the event, with their requests, oldest dispatch first. */
  List<FieldTask> live(UUID eventId, Collection<UUID> teamIds);
}
