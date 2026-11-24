package pt.isep.labdsof.reports.domain;

/**
 * A change the request's current state does not allow, such as accepting without a suggestion (US-35) or acknowledging
 * a request that is no longer Submitted (US-28): 409.
 */
public class RequestConflictException extends RuntimeException {
  public RequestConflictException(String message) {
    super(message);
  }
}
