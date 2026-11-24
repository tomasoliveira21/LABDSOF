package pt.isep.labdsof.reports.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SubmitLimitTest {

  static final Instant NOW = Instant.parse("2027-05-08T23:14:05Z");
  static final SubmitLimit LIMIT = new SubmitLimit(5, Duration.ofMinutes(10));

  static List<Instant> minutesAgo(long... minutes) {
    return java.util.Arrays.stream(minutes).mapToObj(m -> NOW.minus(Duration.ofMinutes(m))).toList();
  }

  @Test
  @DisplayName("US-23 AC-4 the 5th request passes and the 6th is refused")
  void fifthAndSixth() {
    assertThat(LIMIT.retryAfter(List.of(), NOW)).isEmpty();
    assertThat(LIMIT.retryAfter(minutesAgo(9, 7, 5, 1), NOW)).isEmpty();
    assertThat(LIMIT.retryAfter(minutesAgo(9, 7, 5, 1, 0), NOW)).isPresent();
  }

  @Test
  @DisplayName("US-23 AC-4 a request leaves the window after exactly 10 minutes")
  void window() {
    assertThat(LIMIT.retryAfter(minutesAgo(10, 7, 5, 1, 0), NOW)).isEmpty();
    var justInside = Stream.concat(Stream.of(NOW.minus(Duration.ofMinutes(10)).plusNanos(1000)),
        minutesAgo(7, 5, 1, 0).stream()).toList();
    assertThat(LIMIT.retryAfter(justInside, NOW)).isPresent();
  }

  @Test
  @DisplayName("US-23 AC-4 Retry-After is the time until the oldest counted request leaves the window")
  void retryAfter() {
    // In any order: the oldest of the 5 is 9 min ago, so 1 min to wait
    assertThat(LIMIT.retryAfter(minutesAgo(0, 9, 1, 7, 5), NOW)).contains(Duration.ofSeconds(60));
    // A part of a second rounds up
    var times = Stream.concat(Stream.of(NOW.minus(Duration.ofMinutes(9)).minusMillis(500)),
        minutesAgo(7, 5, 1, 0).stream()).toList();
    assertThat(LIMIT.retryAfter(times, NOW)).contains(Duration.ofSeconds(60));
    // Never less than a second
    var edge = Stream.concat(Stream.of(NOW.minus(Duration.ofMinutes(10)).plusNanos(1000)),
        minutesAgo(7, 5, 1, 0).stream()).toList();
    assertThat(LIMIT.retryAfter(edge, NOW)).contains(Duration.ofSeconds(1));
    // More than the limit (a lower setting after a restart): wait for enough of them to leave
    assertThat(LIMIT.retryAfter(minutesAgo(9, 8, 7, 5, 1, 0), NOW)).contains(Duration.ofMinutes(2));
  }

  @Test
  @DisplayName("US-23 AC-4 a limit below 1 or a window that is not positive is refused")
  void settings() {
    var tenMinutes = Duration.ofMinutes(10);
    var negative = Duration.ofSeconds(-1);
    assertThatThrownBy(() -> new SubmitLimit(0, tenMinutes)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new SubmitLimit(5, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new SubmitLimit(5, negative)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new SubmitLimit(5, null)).isInstanceOf(NullPointerException.class);
    var one = new SubmitLimit(1, Duration.ofSeconds(1));
    assertThat(one.retryAfter(List.of(NOW), NOW)).contains(Duration.ofSeconds(1));
    assertThat(one.since(NOW)).isEqualTo(NOW.minusSeconds(1));
  }
}
