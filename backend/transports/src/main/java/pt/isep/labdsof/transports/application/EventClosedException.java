package pt.isep.labdsof.transports.application;

/** A closed event no longer changes its transport (spec US-41 D3): 409. */
public class EventClosedException extends RuntimeException {

  public EventClosedException() {
    super(null, null, false, false);
  }
}
