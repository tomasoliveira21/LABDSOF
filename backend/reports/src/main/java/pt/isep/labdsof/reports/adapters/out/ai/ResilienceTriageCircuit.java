package pt.isep.labdsof.reports.adapters.out.ai;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;
import pt.isep.labdsof.reports.application.TriageCircuit;
import pt.isep.labdsof.reports.domain.NotTriagedReason;

/**
 * The Resilience4j circuit {@value #NAME} (J1; values in application.properties; spec US-36 D1, D2). Used without
 * annotations: the service reports each call's outcome, because an invalid output is known only after validation.
 * Resilience4j publishes its state as {@code resilience4j.circuitbreaker.state{name=ai-provider}}.
 */
@Component
public class ResilienceTriageCircuit implements TriageCircuit {

  public static final String NAME = "ai-provider";

  /** What the circuit records for a failed call; only the reason matters, so no stack trace is filled. */
  static final class FailedCall extends Exception {
    FailedCall(NotTriagedReason reason) {
      super(reason.name(), null, false, false);
    }
  }

  private final CircuitBreaker breaker;
  private volatile NotTriagedReason lastFailure;

  public ResilienceTriageCircuit(CircuitBreakerRegistry registry) {
    this.breaker = registry.circuitBreaker(NAME);
  }

  CircuitBreaker breaker() {
    return breaker;
  }

  @Override
  public boolean tryCall() {
    return breaker.tryAcquirePermission();
  }

  @Override
  public void succeeded(Duration took) {
    breaker.onSuccess(took.toNanos(), TimeUnit.NANOSECONDS);
  }

  @Override
  public void failed(NotTriagedReason reason, Duration took) {
    lastFailure = reason;
    breaker.onError(took.toNanos(), TimeUnit.NANOSECONDS, new FailedCall(reason));
  }

  @Override
  public void released() {
    breaker.releasePermission();
  }

  @Override
  public boolean closed() {
    return breaker.getState() == CircuitBreaker.State.CLOSED;
  }

  @Override
  public NotTriagedReason lastFailure() {
    return lastFailure;
  }
}
