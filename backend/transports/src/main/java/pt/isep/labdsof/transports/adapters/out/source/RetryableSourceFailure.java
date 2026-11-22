package pt.isep.labdsof.transports.adapters.out.source;

import java.util.function.Predicate;

/**
 * Which failures of the bus source are tried again (spec US-40 D5): a {@link SourceFailure} worth another attempt. The
 * Resilience4j starter creates it from application.yaml ({@code retry-exception-predicate}); the circuit breaker
 * refusing a call is never retried.
 */
public class RetryableSourceFailure implements Predicate<Throwable> {

  @Override
  public boolean test(Throwable failure) {
    return failure instanceof SourceFailure sourceFailure && sourceFailure.retryable();
  }
}
