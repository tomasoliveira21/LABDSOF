package pt.isep.labdsof.events.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class OutboxBackoffTest {

  @ParameterizedTest
  @CsvSource({"1,2", "2,4", "5,32"})
  @DisplayName("192 AC-7 the outbox backoff is 2^attempts seconds")
  void doubles(int attempts, long seconds) {
    assertThat(OutboxBackoff.after(attempts)).isEqualTo(Duration.ofSeconds(seconds));
  }

  @ParameterizedTest
  @ValueSource(ints = {6, 7, 40, Integer.MAX_VALUE})
  @DisplayName("192 AC-7 the outbox backoff is capped at 60 s from the sixth attempt on")
  void capped(int attempts) {
    assertThat(OutboxBackoff.after(attempts)).isEqualTo(Duration.ofSeconds(60));
    assertThat(OutboxBackoff.MAX).isEqualTo(Duration.ofSeconds(60));
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1})
  @DisplayName("192 AC-7 an outbox backoff needs at least one attempt")
  void needsAnAttempt(int attempts) {
    assertThatThrownBy(() -> OutboxBackoff.after(attempts)).isInstanceOf(IllegalArgumentException.class);
  }
}
