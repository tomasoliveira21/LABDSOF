package pt.isep.labdsof.transports.application;

/** Shuttles arrive with US-37 (spec US-41 D2): 501, once the caller may change the event's transport. */
public class ShuttleNotYetException extends RuntimeException {

  public ShuttleNotYetException() {
    super(null, null, false, false);
  }
}
