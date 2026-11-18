package pt.isep.labdsof.reports.application;

/** A submit reuses a clientRequestId with another description or location: answered 409, never dropped. */
public class ClientRequestConflictException extends RuntimeException {
  public ClientRequestConflictException() {
    super("clientRequestId was already used for a different help request");
  }
}
