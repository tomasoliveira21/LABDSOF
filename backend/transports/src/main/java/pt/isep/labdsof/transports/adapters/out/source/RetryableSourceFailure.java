package pt.isep.labdsof.transports.adapters.out.source;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Which failures of the bus source are tried again (spec US-40 D5): a {@link SourceFailure} worth another attempt,
 * while the circuit breaker still lets calls through. Once a failure opens the circuit, the run stops at once instead
 * of waiting 1 or 2 s to be refused. The circuit breaker refusing a call is never retried.
 */
final class RetryableSourceFailure implements Predicate<Throwable> {

  private final Supplier<CircuitBreaker> breaker;

  RetryableSourceFailure(Supplier<CircuitBreaker> breaker) {
    this.breaker = breaker;
  }

  @Override
  public boolean test(Throwable failure) {
    if (!(failure instanceof SourceFailure sourceFailure) || !sourceFailure.retryable()) {
      return false;
    }
    var state = breaker.get().getState();
    return state != CircuitBreaker.State.OPEN && state != CircuitBreaker.State.FORCED_OPEN;
  }
}
