package pt.isep.labdsof.reports.application;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pt.isep.labdsof.reports.domain.NotTriagedReason;
import pt.isep.labdsof.reports.domain.TriageSuggestion;

/**
 * The triage worker (US-34; spec US-34 D1, D5 to D8): asks the model for a suggestion for one new request and stores
 * it, or the reason there is none, so the operator triages it by hand (scenario D1). Not transactional: the model
 * call must not hold a database connection; the save is one conditional statement.
 */
@Service
public class TriageService {

  private static final Logger log = LoggerFactory.getLogger(TriageService.class);

  /** Admission: inference starts only with at least this much of the 30 s left (runtime contract). */
  static final Duration MIN_TIME_LEFT = Duration.ofSeconds(23);

  public enum Outcome { SAVED, LATE, ALREADY_TRIAGED, NOT_FOUND }

  /** What happened, for the log and the tests; {@code reason} is null for a suggestion. */
  public record Result(Outcome outcome, NotTriagedReason reason, long providerMs) {}

  private final HelpRequests helpRequests;
  private final TriageModel model;
  private final Clock clock;

  public TriageService(HelpRequests helpRequests, TriageModel model, Clock clock) {
    this.helpRequests = helpRequests;
    this.model = model;
    this.clock = clock;
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
    if (!model.configured() || left.compareTo(MIN_TIME_LEFT) < 0) {
      triage = TriageSuggestion.notTriaged(NotTriagedReason.Unavailable);
    } else {
      try {
        triage = TriageSuggestion.fromModel(model.suggest(request.description(), left));
      } catch (TriageModelException e) {
        triage = TriageSuggestion.notTriaged(e.reason());
      }
    }
    var done = clock.instant();
    var providerMs = Duration.between(start, done).toMillis();
    if (done.isAfter(deadline)) {
      // Completion: a late result is discarded; the request reads NotTriaged/Timeout (runtime contract)
      return logged(requestId, new Result(Outcome.LATE, null, providerMs));
    }
    var saved = helpRequests.saveTriage(request.withTriage(triage, done));
    return logged(requestId, new Result(saved ? Outcome.SAVED : Outcome.ALREADY_TRIAGED,
        triage.notTriagedReason(), providerMs));
  }

  /** Metadata only, never the description or the model's text (ADR-005 decision 5). */
  private static Result logged(UUID requestId, Result result) {
    log.info("Triage of request {}: {} {} in {} ms", requestId, result.outcome(),
        result.reason() == null ? "Suggested" : "NotTriaged " + result.reason(), result.providerMs());
    return result;
  }
}
