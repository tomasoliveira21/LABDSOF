package pt.isep.labdsof.reports.application;

import pt.isep.labdsof.reports.domain.NotTriagedReason;

/** The model gave no usable reply: a timeout, a provider error or a reply that is not JSON. */
public class TriageModelException extends Exception {

  private final transient NotTriagedReason reason;

  public TriageModelException(NotTriagedReason reason, String message) {
    super(message);
    this.reason = reason;
  }

  public NotTriagedReason reason() {
    return reason;
  }
}
