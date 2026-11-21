package pt.isep.labdsof.events.application;

import java.time.Duration;

/** The retry policy of the outbox: 2^attempts seconds, at most 60 s (#214 D5, spec D1). */
public final class OutboxBackoff {

  public static final Duration MAX = Duration.ofSeconds(60);

  private OutboxBackoff() {}

  /** How long a row waits after its {@code attempts}-th failed attempt (counting from 1). */
  public static Duration after(int attempts) {
    if (attempts < 1) {
      throw new IllegalArgumentException("attempts must be at least 1: " + attempts);
    }
    return attempts >= 6 ? MAX : Duration.ofSeconds(1L << attempts);
  }
}
