package pt.isep.labdsof.reports.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * L-2 of ADR-013, enforced by Reports (spec US-23 D1 to D6): an account may have at most {@code max} help requests
 * submitted in the last {@code window}; the values are settings.
 */
public record SubmitLimit(int max, Duration window) {

  public SubmitLimit {
    Objects.requireNonNull(window, "window");
    if (max < 1) {
      throw new IllegalArgumentException("the limit must be at least 1");
    }
    if (window.isZero() || window.isNegative()) {
      throw new IllegalArgumentException("the window must be positive");
    }
  }

  /** A request submitted strictly after this time still counts at {@code now} (D4). */
  public Instant since(Instant now) {
    return now.minus(window);
  }

  /**
   * Empty when one more request may be submitted at {@code now}; else how long to wait, in whole seconds rounded up
   * and at least one: until enough of the counted requests leave the window (D4).
   */
  public Optional<Duration> retryAfter(List<Instant> submitted, Instant now) {
    var from = since(now);
    var counted = submitted.stream().filter(t -> t.isAfter(from)).sorted().toList();
    if (counted.size() < max) {
      return Optional.empty();
    }
    var wait = Duration.between(now, counted.get(counted.size() - max).plus(window));
    return Optional.of(Duration.ofSeconds(Math.max(1, wait.getSeconds() + (wait.getNano() > 0 ? 1 : 0))));
  }
}
