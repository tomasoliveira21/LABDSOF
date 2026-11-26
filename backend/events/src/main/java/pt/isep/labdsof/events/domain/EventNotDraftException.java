package pt.isep.labdsof.events.domain;

/**
 * The event is not a draft, so it does not publish (US-11 D2): 409. A second publish of the same event lands
 * here too, because publishing is not idempotent — the first answer already carried the event and its join link.
 */
public class EventNotDraftException extends RuntimeException {

  public EventNotDraftException() {
    super("event is not a draft");
  }
}
