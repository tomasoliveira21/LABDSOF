package pt.isep.labdsof.reports.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pt.isep.labdsof.reports.domain.NotTriagedReason;
import pt.isep.labdsof.reports.domain.TriageSuggestion;

/**
 * The triage worker (US-34; spec US-34 D1, D5 to D8): asks the model for a suggestion for one new request and stores
 * it, or the reason there is none, so the operator triages it by hand (scenario D1, US-36). Not transactional: the
 * model call must not hold a database connection; the save is one conditional statement. The circuit breaker decides
 * whether the model is called, and every fallback is counted and logged (spec US-36 D2, D3, D7, D9).
 */
@Service
public class TriageService {

  private static final Logger log = LoggerFactory.getLogger(TriageService.class);

  /** Admission: inference starts only with at least this much of the 30 s left (runtime contract). */
  static final Duration MIN_TIME_LEFT = Duration.ofSeconds(23);

  public enum Outcome { SAVED, LATE, ALREADY_TRIAGED, NOT_FOUND }

  /** What happened, for the log and the tests; {@code reason} is null for a suggestion. */
  public record Result(Outcome outcome, NotTriagedReason reason, long providerMs) {}

  /** Whether a new request would be sent to the AI, and why not (GET …/triage-status, spec US-36 D4). */
  public record Status(boolean available, NotTriagedReason reason, Instant checkedAt) {}

  private final HelpRequests helpRequests;
  private final TriageModel model;
  private final TriageCircuit circuit;
  private final TriageMetrics metrics;
  private final Clock clock;

  public TriageService(HelpRequests helpRequests, TriageModel model, TriageCircuit circuit, TriageMetrics metrics,
      Clock clock) {
    this.helpRequests = helpRequests;
    this.model = model;
    this.circuit = circuit;
    this.metrics = metrics;
    this.clock = clock;
  }

  public Status status() {
    var now = clock.instant();
    if (!model.configured()) {
      return new Status(false, NotTriagedReason.Unavailable, now);
    }
    return circuit.closed() ? new Status(true, null, now) : new Status(false, circuit.lastFailure(), now);
  }

  public Result triage(UUID eventId, UUID requestId) {
    var found = helpRequests.find(eventId, requestId);
    if (found.isEmpty()) {
      return logged(requestId, new Result(Outcome.NOT_FOUND, null, 0));
    }
    var request = found.get();
    if (!request.triage().awaitsModel()) {
      return logged(requestId, new Result(Outcome.ALREADY_TRIAGED, null, 0));
    }
    var start = clock.instant();
    var deadline = request.submittedAt().plus(TriageSuggestion.TRIAGE_DEADLINE);
    var left = Duration.between(start, deadline);
    TriageSuggestion triage;
    if (!model.configured() || left.compareTo(MIN_TIME_LEFT) < 0 || !circuit.tryCall()) {
      triage = TriageSuggestion.notTriaged(NotTriagedReason.Unavailable);
    } else {
      triage = called(request.description(), left);
      record(triage.notTriagedReason(), Duration.between(start, clock.instant()));
    }
    var done = clock.instant();
    var providerMs = Duration.between(start, done).toMillis();
    if (done.isAfter(deadline)) {
      // Completion: a late result is discarded; the request reads NotTriaged/Timeout (runtime contract)
      metrics.fallback(NotTriagedReason.Timeout);
      return logged(requestId, new Result(Outcome.LATE, triage.notTriagedReason(), providerMs));
    }
    var saved = helpRequests.saveTriage(request.withTriage(triage, done));
    if (saved) {
      count(triage.notTriagedReason());
    }
    return logged(requestId, new Result(saved ? Outcome.SAVED : Outcome.ALREADY_TRIAGED,
        triage.notTriagedReason(), providerMs));
  }

  private TriageSuggestion called(String description, Duration left) {
    try {
      return TriageSuggestion.fromModel(model.suggest(description, left));
    } catch (TriageModelException e) {
      return TriageSuggestion.notTriaged(e.reason());
    } catch (RuntimeException e) {
      circuit.released();
      throw e;
    }
  }

  /** A suggestion or an abstention is a working AI; a timeout, an error or an invalid output is not (D2). */
  private void record(NotTriagedReason reason, Duration took) {
    if (reason == null || reason == NotTriagedReason.Abstained) {
      circuit.succeeded(took);
    } else {
      circuit.failed(reason, took);
    }
  }

  private void count(NotTriagedReason reason) {
    if (reason == null) {
      metrics.suggested();
    } else {
      metrics.fallback(reason);
    }
  }

  /** Metadata only, never the description or the model's text (ADR-005 decision 5); a fallback at WARN (D9). */
  private static Result logged(UUID requestId, Result result) {
    var triage = result.reason() != null ? "NotTriaged " + result.reason()
        : result.outcome() == Outcome.SAVED || result.outcome() == Outcome.LATE ? "Suggested" : "-";
    var fallback = result.outcome() == Outcome.LATE ? NotTriagedReason.Timeout
        : result.outcome() == Outcome.SAVED ? result.reason() : null;
    if (fallback == null) {
      log.info("Triage of request {}: {} {} in {} ms", requestId, result.outcome(), triage, result.providerMs());
    } else {
      log.warn("Triage of request {}: {} {} in {} ms, fallback {}", requestId, result.outcome(), triage,
          result.providerMs(), fallback);
    }
    return result;
  }
}
