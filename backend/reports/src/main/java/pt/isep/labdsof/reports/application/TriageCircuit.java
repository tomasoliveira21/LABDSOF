package pt.isep.labdsof.reports.application;

import java.time.Duration;
import pt.isep.labdsof.reports.domain.NotTriagedReason;

/**
 * Port: the circuit breaker in front of the AI (J1, architecture.md §9.1; spec US-36 D1 to D4). The service asks it
 * before each call and reports how the call ended, because an invalid output is only known after validation.
 */
public interface TriageCircuit {

  /** False while the circuit is open, or half-open with its probe taken: the AI is not called. */
  boolean tryCall();

  /** The call gave a suggestion or an abstention; {@code took} counts towards the slow-call rate. */
  void succeeded(Duration took);

  /** The call ended in a timeout, a provider error or an invalid output. */
  void failed(NotTriagedReason reason, Duration took);

  /** The call was abandoned (shutdown): nothing is recorded and the permission is given back. */
  void released();

  /** True when the circuit is closed, so a new request would be sent to the AI. */
  boolean closed();

  /** The reason of the last failed call, or null if none failed yet. */
  NotTriagedReason lastFailure();
}
