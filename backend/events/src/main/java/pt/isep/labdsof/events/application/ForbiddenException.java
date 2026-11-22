package pt.isep.labdsof.events.application;

/** A member of the event without the permission the operation needs: 403 (API outline §1.7). */
public class ForbiddenException extends RuntimeException {

  public ForbiddenException() {
    super("no permission in this event");
  }
}
