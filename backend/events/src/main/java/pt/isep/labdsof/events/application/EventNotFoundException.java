package pt.isep.labdsof.events.application;

/** Unknown event, not a member, or an event the reader may not see: all look the same (API outline §1.7). */
public class EventNotFoundException extends RuntimeException {

  public EventNotFoundException() {
    super("event not found");
  }
}
