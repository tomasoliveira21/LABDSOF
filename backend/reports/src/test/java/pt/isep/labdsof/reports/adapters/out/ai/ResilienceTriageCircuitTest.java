package pt.isep.labdsof.reports.adapters.out.ai;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.resilience4j.circuitbreaker.CircuitBreaker.State;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import pt.isep.labdsof.reports.domain.NotTriagedReason;

/** The circuit in front of the AI (J1, architecture.md §9.1; spec US-36 D1, D2, D4). */
class ResilienceTriageCircuitTest {

  static final Duration FAST = Duration.ofSeconds(1);
  static final Duration SLOW = Duration.ofSeconds(17);

  /** The J1 values, as application.properties sets them (checked by the first test). */
  static final CircuitBreakerConfig J1 = CircuitBreakerConfig.custom()
      .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED).slidingWindowSize(10)
      .minimumNumberOfCalls(4).failureRateThreshold(50).slowCallRateThreshold(50)
      .slowCallDurationThreshold(Duration.ofSeconds(16)).waitDurationInOpenState(Duration.ofSeconds(30))
      .permittedNumberOfCallsInHalfOpenState(1).build();

  final ResilienceTriageCircuit circuit = new ResilienceTriageCircuit(CircuitBreakerRegistry.of(J1));

  @Test
  @DisplayName("US-36 AC-2 the circuit has the J1 values")
  void j1Values() throws Exception {
    var properties = new Properties();
    try (var in = new ClassPathResource("application.properties").getInputStream()) {
      properties.load(in);
    }
    var prefix = "resilience4j.circuitbreaker.instances." + ResilienceTriageCircuit.NAME + ".";
    assertThat(properties).containsEntry(prefix + "sliding-window-type", "COUNT_BASED")
        .containsEntry(prefix + "sliding-window-size", "10")
        .containsEntry(prefix + "minimum-number-of-calls", "4")
        .containsEntry(prefix + "failure-rate-threshold", "50")
        .containsEntry(prefix + "slow-call-rate-threshold", "50")
        .containsEntry(prefix + "slow-call-duration-threshold", "16s")
        .containsEntry(prefix + "wait-duration-in-open-state", "30s")
        .containsEntry(prefix + "permitted-number-of-calls-in-half-open-state", "1");
  }

  @Test
  @DisplayName("US-36 AC-2 the circuit opens after 4 calls with half failed and closes after a successful probe")
  void opensAndCloses() {
    assertThat(circuit.closed()).isTrue();
    assertThat(circuit.lastFailure()).isNull();
    for (var reason : new NotTriagedReason[] {NotTriagedReason.Timeout, null, NotTriagedReason.InvalidOutput}) {
      assertThat(circuit.tryCall()).isTrue();
      if (reason == null) {
        circuit.succeeded(FAST);
      } else {
        circuit.failed(reason, FAST);
      }
    }
    assertThat(circuit.closed()).as("fewer than 4 calls: no rate yet").isTrue();
    assertThat(circuit.tryCall()).isTrue();
    circuit.failed(NotTriagedReason.ProviderError, FAST);

    assertThat(circuit.closed()).isFalse();
    assertThat(circuit.tryCall()).isFalse();
    assertThat(circuit.lastFailure()).isEqualTo(NotTriagedReason.ProviderError);

    circuit.breaker().transitionToHalfOpenState();
    assertThat(circuit.closed()).as("half-open is not available yet").isFalse();
    assertThat(circuit.tryCall()).isTrue();
    assertThat(circuit.tryCall()).as("one probe only").isFalse();
    circuit.succeeded(FAST);
    assertThat(circuit.closed()).isTrue();
  }

  @Test
  @DisplayName("US-36 AC-2 slow calls open the circuit")
  void slowCalls() {
    circuit.tryCall();
    circuit.succeeded(FAST);
    circuit.tryCall();
    circuit.succeeded(FAST);
    circuit.tryCall();
    circuit.succeeded(SLOW);
    assertThat(circuit.closed()).isTrue();
    circuit.tryCall();
    circuit.succeeded(SLOW);
    assertThat(circuit.breaker().getState()).isEqualTo(State.OPEN);
    assertThat(circuit.lastFailure()).isNull();
  }

  @Test
  @DisplayName("US-36 AC-2 an interrupted call releases the circuit")
  void released() {
    circuit.breaker().transitionToOpenState();
    circuit.breaker().transitionToHalfOpenState();
    assertThat(circuit.tryCall()).isTrue();
    circuit.released();
    assertThat(circuit.tryCall()).as("the probe is given back").isTrue();
    assertThat(circuit.breaker().getMetrics().getNumberOfBufferedCalls()).isZero();
  }
}
