package pt.isep.labdsof.transports.adapters.out.source;

/**
 * A call to the source that failed (spec US-40 D5): I/O errors, timeouts, HTTP {@code 5xx} and {@code 429} are worth
 * another attempt; any other HTTP {@code 4xx} is not. Every one counts for the circuit breaker. The message is the
 * reason recorded for the run.
 */
class SourceFailure extends RuntimeException {

  private final boolean retryable;

  SourceFailure(String reason, boolean retryable) {
    super(reason, null, false, false);
    this.retryable = retryable;
  }

  boolean retryable() {
    return retryable;
  }
}
