package pt.isep.labdsof.authusers.application;

/**
 * The same {@code 404} for an unknown event, an event the caller is not a member of, a user who is not a member of
 * it and a team that is not a live team of it (spec D2, D4, API outline §1.7): nothing tells an outsider which of
 * the four it was.
 */
public class EventNotFoundException extends RuntimeException {

  public EventNotFoundException() {
    super("Event, member or team not found in this event");
  }
}
