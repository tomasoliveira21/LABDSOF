package pt.isep.labdsof.events.application;

/**
 * The event is Suspended or Closed, so its teams are not changed (US-16 D2, from US-09 and US-10): 409.
 * Reading them is still allowed to whoever may see the event.
 */
public class EventNotOpenException extends RuntimeException {

  public EventNotOpenException() {
    super("event not open to changes");
  }
}
