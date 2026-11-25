package pt.isep.labdsof.reports.application;

import java.time.Duration;

/** L-2: the account already has the most help requests the window allows; answered 429 with Retry-After (US-23). */
public class TooManyHelpRequestsException extends RuntimeException {

  private final transient Duration retryAfter;

  public TooManyHelpRequestsException(Duration retryAfter) {
    super("Too many help requests: you can ask again in " + retryAfter.toSeconds() + " seconds");
    this.retryAfter = retryAfter;
  }

  public Duration retryAfter() {
    return retryAfter;
  }
}
