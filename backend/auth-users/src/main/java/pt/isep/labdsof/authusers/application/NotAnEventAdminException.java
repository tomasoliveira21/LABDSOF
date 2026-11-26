package pt.isep.labdsof.authusers.application;

/** A member of the event who is not an EventAdmin: {@code 403}, because the event itself is theirs to see (D2). */
public class NotAnEventAdminException extends RuntimeException {

  public NotAnEventAdminException() {
    super("Not an EventAdmin of this event");
  }
}
