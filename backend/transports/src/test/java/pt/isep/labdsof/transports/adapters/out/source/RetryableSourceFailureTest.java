package pt.isep.labdsof.transports.adapters.out.source;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RetryableSourceFailureTest {

  private final CircuitBreaker breaker = CircuitBreaker.ofDefaults("transport-source");
  private final RetryableSourceFailure retried = new RetryableSourceFailure(() -> breaker);

  private boolean retriedWhen(Consumer<CircuitBreaker> state) {
    state.accept(breaker);
    return retried.test(new SourceFailure("HTTP 503", true));
  }

  @Test
  @DisplayName("US-40 D5 a failure worth another attempt is retried while the circuit is closed or half-open")
  void retriedWhileCallsGoThrough() {
    assertThat(retried.test(new SourceFailure("HTTP 503", true))).isTrue();
    assertThat(retriedWhen(b -> {
      b.transitionToOpenState();
      b.transitionToHalfOpenState();
    })).isTrue();
  }

  @Test
  @DisplayName("US-40 D5 nothing is retried once the circuit is open, or forced open")
  void notRetriedOnceOpen() {
    assertThat(retriedWhen(CircuitBreaker::transitionToOpenState)).isFalse();
    assertThat(retriedWhen(CircuitBreaker::transitionToForcedOpenState)).isFalse();
  }

  @Test
  @DisplayName("US-40 D5 a 4xx other than 429, the circuit refusing a call and any other error are not retried")
  void otherFailuresAreNotRetried() {
    assertThat(retried.test(new SourceFailure("HTTP 404", false))).isFalse();
    assertThat(retried.test(CallNotPermittedException.createCallNotPermittedException(breaker))).isFalse();
    assertThat(retried.test(new IllegalStateException("bug"))).isFalse();
  }
}
