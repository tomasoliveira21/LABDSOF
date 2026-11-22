package pt.isep.labdsof.transports.adapters.out.source;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import java.time.Duration;

/**
 * The source's retry and circuit breaker for the tests that build the adapter by hand: the J1 values of
 * application.yaml (ResilienceSettingsIT checks those), with a 10 ms first wait.
 */
final class TestResilience {

  private TestResilience() {}

  static CircuitBreakerRegistry breakers() {
    return CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
        .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
        .slidingWindowSize(6)
        .minimumNumberOfCalls(3)
        .failureRateThreshold(50)
        .waitDurationInOpenState(Duration.ofSeconds(60))
        .permittedNumberOfCallsInHalfOpenState(1)
        .build());
  }

  static RetryRegistry retries() {
    return RetryRegistry.of(RetryConfig.custom()
        .maxAttempts(3)
        .intervalFunction(IntervalFunction.ofExponentialBackoff(Duration.ofMillis(10), 2))
        .retryOnException(new RetryableSourceFailure())
        .build());
  }
}
