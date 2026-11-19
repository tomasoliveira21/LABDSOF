package pt.isep.labdsof.authusers.application;

/** Events to publish, stored with the business change; called inside the business transaction (ADR-004). */
public interface Outbox {

  void add(OutgoingEvent event);
}
