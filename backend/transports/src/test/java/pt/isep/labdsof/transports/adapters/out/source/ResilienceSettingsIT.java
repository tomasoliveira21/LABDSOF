package pt.isep.labdsof.transports.adapters.out.source;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.functions.Either;
import io.github.resilience4j.retry.RetryRegistry;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.context.ConfigurableApplicationContext;
import pt.isep.labdsof.transports.Infrastructure;

/**
 * The retry and the circuit breaker of the bus source as application.yaml sets them through Resilience4j's Spring Boot
 * 4 starter (spec US-40 D5, architecture 9.1, J1): a key the starter does not know is ignored without a word, so the
 * values are read back from the registries. And the circuit breaker in the health check.
 */
class ResilienceSettingsIT {

  private static ConfigurableApplicationContext transports;

  @BeforeAll
  static void start() {
    transports = Infrastructure.startTransports(Map.of());
  }

  @AfterAll
  static void stop() {
    transports.close();
  }

  private static CircuitBreaker breaker() {
    return transports.getBean(CircuitBreakerRegistry.class).circuitBreaker("transport-source");
  }

  @Test
  @DisplayName("US-40 D5 the circuit breaker of J1: the last 6 calls, at least 3, opens at 50 % failed, open 60 s, "
      + "then 1 trial call")
  void circuitBreakerOfJ1() {
    var config = breaker().getCircuitBreakerConfig();

    assertThat(config.getSlidingWindowType()).isEqualTo(SlidingWindowType.COUNT_BASED);
    assertThat(config.getSlidingWindowSize()).isEqualTo(6);
    assertThat(config.getMinimumNumberOfCalls()).isEqualTo(3);
    assertThat(config.getFailureRateThreshold()).isEqualTo(50f);
    assertThat(config.getWaitIntervalFunctionInOpenState().apply(1)).isEqualTo(60_000L);
    assertThat(config.getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(1);
  }

  @Test
  @DisplayName("US-40 D5 the retry of J1: 3 attempts, 1 s then 2 s apart, only after a failure worth asking again")
  void retryOfJ1() {
    var config = transports.getBean(RetryRegistry.class).retry("transport-source").getRetryConfig();
    Either<Throwable, Object> failed = Either.left(new SourceFailure("HTTP 503", true));

    assertThat(config.getMaxAttempts()).isEqualTo(3);
    assertThat(config.getIntervalBiFunction().apply(1, failed)).isEqualTo(1_000L);
    assertThat(config.getIntervalBiFunction().apply(2, failed)).isEqualTo(2_000L);
    assertThat(config.getExceptionPredicate().test(new SourceFailure("HTTP 503", true))).isTrue();
    assertThat(config.getExceptionPredicate().test(new SourceFailure("HTTP 404", false))).isFalse();
  }

  @Test
  @DisplayName("US-40 D5 the circuit breaker's state is in the health check, and an open circuit does not make "
      + "Transports unhealthy: the source is outside it")
  void circuitBreakerInTheHealthCheck() {
    var health = transports.getBean(HealthEndpoint.class);
    var before = health.health().getStatus();
    breaker().transitionToOpenState();
    try {
      var circuitBreakers = transports.getBean("circuitBreakersHealthIndicator", HealthIndicator.class).health();
      var transportSource = (Health) circuitBreakers.getDetails().get("transport-source");

      assertThat(String.valueOf(transportSource.getDetails().get("state"))).isEqualTo("OPEN");
      assertThat(transportSource.getStatus()).isNotEqualTo(Status.DOWN);
      assertThat(health.health().getStatus()).isEqualTo(before).isEqualTo(Status.UP);
    } finally {
      breaker().reset();
    }
  }
}
