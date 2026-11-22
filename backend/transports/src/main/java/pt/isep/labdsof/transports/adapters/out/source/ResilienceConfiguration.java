package pt.isep.labdsof.transports.adapters.out.source;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The retry and the circuit breaker of the bus source, named {@value #NAME}, with the values of J1 from
 * {@link TransportSourceProperties} (spec US-40 D5). Resilience4j's core modules, configured in code.
 */
@Configuration
public class ResilienceConfiguration {

  public static final String NAME = "transport-source";

  @Bean
  CircuitBreakerRegistry transportSourceCircuitBreakers(TransportSourceProperties properties) {
    return circuitBreakers(properties);
  }

  @Bean
  RetryRegistry transportSourceRetries(TransportSourceProperties properties) {
    return retries(properties);
  }

  static CircuitBreakerRegistry circuitBreakers(TransportSourceProperties properties) {
    var values = properties.circuitBreaker();
    return CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
        .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
        .slidingWindowSize(values.slidingWindowSize())
        .minimumNumberOfCalls(values.minimumNumberOfCalls())
        .failureRateThreshold(values.failureRateThreshold())
        .waitDurationInOpenState(values.waitInOpenState())
        .permittedNumberOfCallsInHalfOpenState(values.permittedCallsInHalfOpenState())
        .build());
  }

  static RetryRegistry retries(TransportSourceProperties properties) {
    var values = properties.retry();
    return RetryRegistry.of(RetryConfig.custom()
        .maxAttempts(values.maxAttempts())
        .intervalFunction(IntervalFunction.ofExponentialBackoff(values.firstWait(), values.multiplier()))
        .retryOnException(e -> e instanceof SourceFailure failure && failure.retryable())
        .build());
  }
}
