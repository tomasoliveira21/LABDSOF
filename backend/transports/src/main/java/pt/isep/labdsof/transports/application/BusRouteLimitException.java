package pt.isep.labdsof.transports.application;

/** The event already holds the most routes it may (spec US-41 D4): 409. */
public class BusRouteLimitException extends RuntimeException {

  public BusRouteLimitException() {
    super(null, null, false, false);
  }
}
