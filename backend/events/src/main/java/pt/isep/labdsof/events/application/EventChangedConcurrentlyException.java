package pt.isep.labdsof.events.application;

/**
 * Another request changed the event between this one's read and its write (US-11 D4), so nothing was written or
 * published: 409. The caller reads the event again and decides.
 */
public class EventChangedConcurrentlyException extends RuntimeException {

  public EventChangedConcurrentlyException() {
    super("event changed concurrently");
  }
}
