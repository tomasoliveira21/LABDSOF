package pt.isep.labdsof.transports.application;

/** A member of the event without the permission the request needs (spec US-41 D3): 403. */
public class ForbiddenException extends RuntimeException {

  public ForbiddenException() {
    super(null, null, false, false);
  }
}
