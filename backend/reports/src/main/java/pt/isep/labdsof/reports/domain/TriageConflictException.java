package pt.isep.labdsof.reports.domain;

/** A triage decision the current triage state does not allow, such as accepting without a suggestion: 409. */
public class TriageConflictException extends RuntimeException {
  public TriageConflictException(String message) {
    super(message);
  }
}
