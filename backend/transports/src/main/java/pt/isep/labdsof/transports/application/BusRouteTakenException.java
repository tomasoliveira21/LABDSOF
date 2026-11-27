package pt.isep.labdsof.transports.application;

/** The route is already on the event (spec US-41 D4): 409. */
public class BusRouteTakenException extends RuntimeException {

  public BusRouteTakenException() {
    super(null, null, false, false);
  }
}
