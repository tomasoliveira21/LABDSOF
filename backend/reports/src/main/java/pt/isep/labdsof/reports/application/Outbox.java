package pt.isep.labdsof.reports.application;

/** Port: the outbox table; called inside the business transaction (ADR-004 decision 3). */
public interface Outbox {

  void add(OutgoingEvent event);
}
