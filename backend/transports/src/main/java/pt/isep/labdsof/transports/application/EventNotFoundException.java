package pt.isep.labdsof.transports.application;

/** An unknown event, or one the caller may not see (spec US-41 AC-3): the same 404 for every case. */
public class EventNotFoundException extends RuntimeException {

  public EventNotFoundException() {
    super(null, null, false, false);
  }
}
