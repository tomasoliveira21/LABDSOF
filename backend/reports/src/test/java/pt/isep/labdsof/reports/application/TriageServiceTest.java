package pt.isep.labdsof.reports.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import pt.isep.labdsof.reports.application.TriageService.Outcome;
import pt.isep.labdsof.reports.domain.Category;
import pt.isep.labdsof.reports.domain.HelpRequest;
import pt.isep.labdsof.reports.domain.Location;
import pt.isep.labdsof.reports.domain.NotTriagedReason;
import pt.isep.labdsof.reports.domain.Priority;
import pt.isep.labdsof.reports.domain.TriageState;
import pt.isep.labdsof.reports.domain.TriageSuggestion;

/** The triage worker (spec US-34 D1, D5 to D8, D12), with in-memory ports and a fake model. */
class TriageServiceTest {

  static final Instant SUBMITTED = Instant.parse("2027-05-08T23:14:05Z");
  static final UUID EVENT = UUID.randomUUID();
  static final String DESCRIPTION = "O meu amigo desmaiou junto ao palco";
  static final Map<String, Object> VALID = Map.of("category", "Health", "priority", "High",
      "reason", "Desmaio relatado; requer a equipa médica.", "abstain", false);

  /** A clock the fake model can move, as a real call takes time. */
  static final class MovingClock extends Clock {
    Instant now = SUBMITTED;

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  /** A model double: records what it was given and answers, fails or takes time as told. */
  static final class FakeModel implements TriageModel {
    boolean configured = true;
    Object reply = VALID;
    NotTriagedReason failure;
    Duration takes = Duration.ofSeconds(10);
    final List<String> descriptions = new ArrayList<>();
    final List<Duration> budgets = new ArrayList<>();
    MovingClock clock;

    @Override
    public boolean configured() {
      return configured;
    }

    @Override
    public Object suggest(String description, Duration budget) throws TriageModelException {
      descriptions.add(description);
      budgets.add(budget);
      clock.now = clock.now.plus(takes);
      if (failure != null) {
        throw new TriageModelException(failure, "fake " + failure);
      }
      return reply;
    }
  }

  /** A circuit double (US-36): open or closed as told; records what the service reported. */
  static final class FakeCircuit implements TriageCircuit {
    boolean open;
    NotTriagedReason lastFailure;
    final List<String> calls = new ArrayList<>();

    @Override
    public boolean tryCall() {
      calls.add("try");
      return !open;
    }

    @Override
    public void succeeded(Duration took) {
      calls.add("succeeded " + took.toSeconds());
    }

    @Override
    public void failed(NotTriagedReason reason, Duration took) {
      calls.add("failed " + reason + " " + took.toSeconds());
    }

    @Override
    public void released() {
      calls.add("released");
    }

    @Override
    public boolean closed() {
      return !open;
    }

    @Override
    public NotTriagedReason lastFailure() {
      return lastFailure;
    }
  }

  /** The counters, as "suggested" or the fallback reason. */
  static final class FakeMetrics implements TriageMetrics {
    final List<String> counted = new ArrayList<>();

    @Override
    public void suggested() {
      counted.add("suggested");
    }

    @Override
    public void fallback(NotTriagedReason reason) {
      counted.add(reason.name());
    }

    @Override
    public void decided(TriageState decision) {
      counted.add("decided " + decision);
    }
  }

  final InMemoryPorts ports = new InMemoryPorts();
  final MovingClock clock = new MovingClock();
  final FakeModel model = new FakeModel();
  final FakeCircuit circuit = new FakeCircuit();
  final FakeMetrics metrics = new FakeMetrics();

  TriageService service() {
    model.clock = clock;
    return new TriageService(ports, model, circuit, metrics, clock);
  }

  HelpRequest submitted() {
    var request = HelpRequest.submit(UUID.randomUUID(), EVENT, "ana", UUID.randomUUID(), DESCRIPTION,
        new Location(3, 0.42, 0.77), SUBMITTED);
    ports.insertIfNew(request);
    return request;
  }

  HelpRequest stored(HelpRequest request) {
    return ports.find(EVENT, request.id()).orElseThrow();
  }

  @Test
  @DisplayName("229 AC-1 the model's suggestion is stored and the request's own values stay empty")
  void suggestionStored() {
    var request = submitted();
    clock.now = SUBMITTED.plusSeconds(1);
    var result = service().triage(EVENT, request.id());

    assertThat(result.outcome()).isEqualTo(Outcome.SAVED);
    assertThat(result.providerMs()).isEqualTo(10_000);
    var saved = stored(request);
    assertThat(saved.triage()).isEqualTo(TriageSuggestion.suggested(Category.Health, Priority.High,
        "Desmaio relatado; requer a equipa médica."));
    assertThat(saved.category()).isNull();
    assertThat(saved.priority()).isNull();
    assertThat(saved.updatedAt()).isEqualTo(SUBMITTED.plusSeconds(11));
    assertThat(saved.asSeenAt(clock.now).triage().state()).isEqualTo(TriageState.Suggested);
  }

  @Test
  @DisplayName("229 AC-2 only the description is passed to the model, with the time left before the deadline")
  void onlyDescription() {
    var request = submitted();
    clock.now = SUBMITTED.plusSeconds(2);
    service().triage(EVENT, request.id());
    assertThat(model.descriptions).containsExactly(DESCRIPTION);
    assertThat(model.budgets).containsExactly(Duration.ofSeconds(28));
  }

  @Test
  @DisplayName("229 AC-3 a model failure is stored with its reason")
  void failureStored() {
    for (var reason : List.of(NotTriagedReason.ProviderError, NotTriagedReason.Timeout, NotTriagedReason.InvalidOutput)) {
      var request = submitted();
      clock.now = SUBMITTED;
      model.failure = reason;
      var result = service().triage(EVENT, request.id());
      assertThat(result.outcome()).isEqualTo(Outcome.SAVED);
      assertThat(result.reason()).isEqualTo(reason);
      assertThat(stored(request).triage()).isEqualTo(TriageSuggestion.notTriaged(reason));
    }
  }

  @Test
  @DisplayName("229 AC-3 an invalid or abstained output is stored with its reason")
  void invalidOrAbstained() {
    var request = submitted();
    model.reply = Map.of("category", "Theft");
    assertThat(service().triage(EVENT, request.id()).reason()).isEqualTo(NotTriagedReason.InvalidOutput);
    var other = submitted();
    clock.now = SUBMITTED;
    var abstain = new java.util.HashMap<String, Object>();
    abstain.put("category", null);
    abstain.put("priority", null);
    abstain.put("reason", "Sem descrição.");
    abstain.put("abstain", true);
    model.reply = abstain;
    service().triage(EVENT, other.id());
    assertThat(stored(other).triage()).isEqualTo(TriageSuggestion.notTriaged(NotTriagedReason.Abstained));
  }

  @Test
  @DisplayName("229 AC-3 without a configured AI the request is Unavailable")
  void notConfigured() {
    var request = submitted();
    model.configured = false;
    var result = service().triage(EVENT, request.id());
    assertThat(result.outcome()).isEqualTo(Outcome.SAVED);
    assertThat(stored(request).triage()).isEqualTo(TriageSuggestion.notTriaged(NotTriagedReason.Unavailable));
    assertThat(model.descriptions).isEmpty();
  }

  @Test
  @DisplayName("229 AC-3 a request that cannot start in time is Unavailable")
  void admission() {
    var onTime = submitted();
    clock.now = SUBMITTED.plusSeconds(7);
    model.takes = Duration.ZERO;
    service().triage(EVENT, onTime.id());
    assertThat(stored(onTime).triage().state()).isEqualTo(TriageState.Suggested);

    var late = submitted();
    clock.now = SUBMITTED.plusSeconds(7).plusNanos(1000);
    service().triage(EVENT, late.id());
    assertThat(stored(late).triage()).isEqualTo(TriageSuggestion.notTriaged(NotTriagedReason.Unavailable));
    assertThat(model.descriptions).hasSize(1);
  }

  @Test
  @DisplayName("229 AC-3 a late suggestion is discarded")
  void late() {
    var atDeadline = submitted();
    model.takes = Duration.ofSeconds(30);
    assertThat(service().triage(EVENT, atDeadline.id()).outcome()).isEqualTo(Outcome.SAVED);

    var late = submitted();
    clock.now = SUBMITTED;
    model.takes = Duration.ofSeconds(30).plusNanos(1000);
    var result = service().triage(EVENT, late.id());
    assertThat(result.outcome()).isEqualTo(Outcome.LATE);
    assertThat(stored(late).triage()).isEqualTo(TriageSuggestion.pending());
    assertThat(stored(late).asSeenAt(clock.now).triage().notTriagedReason()).isEqualTo(NotTriagedReason.Timeout);
  }

  @Test
  @DisplayName("229 AC-1 a request already triaged is not triaged again")
  void alreadyTriaged() {
    var request = submitted();
    service().triage(EVENT, request.id());
    var first = stored(request);
    model.reply = Map.of("category", "Other", "priority", "Low", "reason", "r", "abstain", false);
    clock.now = SUBMITTED;
    assertThat(service().triage(EVENT, request.id()).outcome()).isEqualTo(Outcome.ALREADY_TRIAGED);
    assertThat(stored(request)).isEqualTo(first);
    assertThat(model.descriptions).hasSize(1);
  }

  @Test
  @DisplayName("US-30 AC-1 a closed request is not triaged, and a result after the close is not saved")
  void closedRequest() {
    var actor = new pt.isep.labdsof.reports.domain.Actor("miguel", null);
    // Closed before the worker reads it: the model is not called
    var early = submitted();
    var closing = early.close(SUBMITTED.plusSeconds(1));
    ports.saveDecision(closing.request(), actor, closing.changes());
    assertThat(service().triage(EVENT, early.id()).outcome()).isEqualTo(Outcome.ALREADY_TRIAGED);
    assertThat(model.descriptions).isEmpty();

    // Closed while the model answered: the result is not saved, nor counted
    var late = submitted();
    var service = new TriageService(ports, new TriageModel() {
      @Override
      public boolean configured() {
        return true;
      }

      @Override
      public Object suggest(String description, Duration budget) {
        var closed = stored(late).close(SUBMITTED.plusSeconds(2));
        ports.saveDecision(closed.request(), actor, closed.changes());
        return VALID;
      }
    }, circuit, metrics, clock);
    assertThat(service.triage(EVENT, late.id()).outcome()).isEqualTo(Outcome.ALREADY_TRIAGED);
    assertThat(stored(late).triage()).isEqualTo(TriageSuggestion.pending());
    assertThat(stored(late).status()).isEqualTo(pt.isep.labdsof.reports.domain.HelpRequestStatus.Closed);
    assertThat(metrics.counted).isEmpty();
  }

  @Test
  @DisplayName("229 AC-1 a triage written while the model answered is not overwritten")
  void concurrentWrite() {
    var request = submitted();
    model.clock = clock;
    var service = new TriageService(ports, new TriageModel() {
      @Override
      public boolean configured() {
        return true;
      }

      @Override
      public Object suggest(String description, Duration budget) {
        // another consumer saved first (redelivery while this call ran)
        ports.saveTriage(stored(request).withTriage(TriageSuggestion.notTriaged(NotTriagedReason.Timeout), SUBMITTED));
        return VALID;
      }
    }, circuit, metrics, clock);
    assertThat(service.triage(EVENT, request.id()).outcome()).isEqualTo(Outcome.ALREADY_TRIAGED);
    assertThat(stored(request).triage()).isEqualTo(TriageSuggestion.notTriaged(NotTriagedReason.Timeout));
  }

  @Test
  @DisplayName("229 AC-1 an unknown request is ignored")
  void unknown() {
    submitted();
    assertThat(service().triage(EVENT, UUID.randomUUID()).outcome()).isEqualTo(Outcome.NOT_FOUND);
    assertThat(service().triage(UUID.randomUUID(), UUID.randomUUID()).outcome()).isEqualTo(Outcome.NOT_FOUND);
    assertThat(model.descriptions).isEmpty();
  }

  @Test
  @DisplayName("229 AC-2 the log has no description or reason")
  void logs() {
    var logger = (Logger) LoggerFactory.getLogger(TriageService.class);
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);
    try {
      var request = submitted();
      service().triage(EVENT, request.id());
      model.failure = NotTriagedReason.ProviderError;
      var failed = submitted();
      clock.now = SUBMITTED;
      service().triage(EVENT, failed.id());
      assertThat(appender.list).isNotEmpty();
      assertThat(appender.list).allSatisfy(e -> assertThat(e.getFormattedMessage())
          .doesNotContain(DESCRIPTION).doesNotContain("Desmaio").doesNotContain("fake"));
      assertThat(appender.list.get(0).getFormattedMessage()).contains(request.id().toString()).contains("SAVED Suggested");
      assertThat(appender.list.get(1).getFormattedMessage()).contains("SAVED NotTriaged ProviderError");
      // a late failure keeps its reason in the log; an outcome that saved nothing never reads "Suggested"
      var late = submitted();
      clock.now = SUBMITTED;
      model.failure = NotTriagedReason.Timeout;
      model.takes = Duration.ofSeconds(31);
      service().triage(EVENT, late.id());
      assertThat(appender.list.get(2).getFormattedMessage()).contains("LATE NotTriaged Timeout");
      service().triage(EVENT, UUID.randomUUID());
      assertThat(appender.list.get(3).getFormattedMessage()).contains("NOT_FOUND").doesNotContain("Suggested");
      var lateSuggestion = submitted();
      clock.now = SUBMITTED;
      model.failure = null;
      service().triage(EVENT, lateSuggestion.id());
      assertThat(appender.list.get(4).getFormattedMessage()).contains("LATE Suggested");
    } finally {
      logger.detachAppender(appender);
    }
  }

  static Map<String, Object> abstention() {
    var output = new HashMap<String, Object>();
    output.put("category", null);
    output.put("priority", null);
    output.put("reason", "Sem descrição útil.");
    output.put("abstain", true);
    return output;
  }

  @Test
  @DisplayName("US-36 AC-2 while the circuit is open the AI is not called")
  void circuitOpen() {
    circuit.open = true;
    var request = submitted();
    var result = service().triage(EVENT, request.id());

    assertThat(result.outcome()).isEqualTo(Outcome.SAVED);
    assertThat(result.reason()).isEqualTo(NotTriagedReason.Unavailable);
    assertThat(model.descriptions).isEmpty();
    assertThat(circuit.calls).containsExactly("try");
    assertThat(stored(request).triage()).isEqualTo(TriageSuggestion.notTriaged(NotTriagedReason.Unavailable));
  }

  @Test
  @DisplayName("US-36 AC-2 the circuit records each call's outcome and duration")
  void circuitRecords() {
    service().triage(EVENT, submitted().id());
    model.reply = Map.of("category", "Other", "reason", "x", "abstain", false);
    clock.now = SUBMITTED;
    service().triage(EVENT, submitted().id());
    model.reply = abstention();
    clock.now = SUBMITTED;
    model.takes = Duration.ofSeconds(17);
    service().triage(EVENT, submitted().id());
    for (var failure : List.of(NotTriagedReason.Timeout, NotTriagedReason.ProviderError)) {
      model.failure = failure;
      model.takes = Duration.ofSeconds(3);
      clock.now = SUBMITTED;
      service().triage(EVENT, submitted().id());
    }
    // not sent: no AI configured, or too late to start
    model.configured = false;
    service().triage(EVENT, submitted().id());
    model.configured = true;
    clock.now = SUBMITTED.plusSeconds(8);
    service().triage(EVENT, submitted().id());

    assertThat(circuit.calls).containsExactly("try", "succeeded 10", "try", "failed InvalidOutput 10",
        "try", "succeeded 17", "try", "failed Timeout 3", "try", "failed ProviderError 3");
  }

  @Test
  @DisplayName("US-36 AC-2 an interrupted call releases the circuit")
  void interrupted() {
    var request = submitted();
    var service = new TriageService(ports, new TriageModel() {
      @Override
      public boolean configured() {
        return true;
      }

      @Override
      public Object suggest(String description, Duration budget) {
        throw new IllegalStateException("interrupted while waiting for the AI");
      }
    }, circuit, metrics, clock);

    assertThatThrownBy(() -> service.triage(EVENT, request.id())).isInstanceOf(IllegalStateException.class);
    assertThat(circuit.calls).containsExactly("try", "released");
    assertThat(metrics.counted).isEmpty();
    assertThat(stored(request).triage().awaitsModel()).isTrue();

    // an Error (out of memory, stack overflow) gives the permission back too, or a half-open circuit would stay
    // without its probe until a restart (G8 review)
    circuit.calls.clear();
    var broken = new TriageService(ports, new TriageModel() {
      @Override
      public boolean configured() {
        return true;
      }

      @Override
      public Object suggest(String description, Duration budget) {
        throw new StackOverflowError();
      }
    }, circuit, metrics, clock);
    assertThatThrownBy(() -> broken.triage(EVENT, request.id())).isInstanceOf(StackOverflowError.class);
    assertThat(circuit.calls).containsExactly("try", "released");
  }

  @Test
  @DisplayName("US-36 AC-2 the status says whether the AI can be called and why not")
  void status() {
    var now = SUBMITTED.plusSeconds(3);
    clock.now = now;
    assertThat(service().status()).isEqualTo(new TriageService.Status(true, null, now));
    circuit.open = true;
    circuit.lastFailure = NotTriagedReason.Timeout;
    assertThat(service().status()).isEqualTo(new TriageService.Status(false, NotTriagedReason.Timeout, now));
    model.configured = false;
    circuit.open = false;
    assertThat(service().status()).isEqualTo(new TriageService.Status(false, NotTriagedReason.Unavailable, now));
    // closed again after a recovery: the old failure is not shown
    model.configured = true;
    assertThat(service().status()).isEqualTo(new TriageService.Status(true, null, now));
    // opened by slow calls that all answered: no failure to tell, but never a null reason (G8 review)
    circuit.open = true;
    circuit.lastFailure = null;
    assertThat(service().status()).isEqualTo(new TriageService.Status(false, NotTriagedReason.Unavailable, now));
  }

  @Test
  @DisplayName("US-36 AC-3 every fallback is counted once with its reason")
  void counted() {
    service().triage(EVENT, submitted().id());
    model.reply = Map.of("category", "Other");
    clock.now = SUBMITTED;
    service().triage(EVENT, submitted().id());
    model.reply = abstention();
    clock.now = SUBMITTED;
    service().triage(EVENT, submitted().id());
    model.failure = NotTriagedReason.ProviderError;
    clock.now = SUBMITTED;
    service().triage(EVENT, submitted().id());
    model.configured = false;
    service().triage(EVENT, submitted().id());
    model.configured = true;
    // a late suggestion and a late failure both read Timeout
    model.failure = null;
    model.reply = VALID;
    model.takes = Duration.ofSeconds(31);
    clock.now = SUBMITTED;
    service().triage(EVENT, submitted().id());
    model.failure = NotTriagedReason.ProviderError;
    clock.now = SUBMITTED;
    service().triage(EVENT, submitted().id());
    // a redelivered or unknown request counts nothing
    var request = submitted();
    model.failure = null;
    model.takes = Duration.ofSeconds(1);
    clock.now = SUBMITTED;
    service().triage(EVENT, request.id());
    service().triage(EVENT, request.id());
    service().triage(EVENT, UUID.randomUUID());
    // a message handled only after the deadline, delivered twice: nothing can be saved and the AI is not called,
    // so it counts nothing, as a message that never arrived (spec US-36 D7; G8 review)
    var backlog = submitted();
    clock.now = SUBMITTED.plusSeconds(31);
    service().triage(EVENT, backlog.id());
    service().triage(EVENT, backlog.id());

    assertThat(metrics.counted).containsExactly("suggested", "InvalidOutput", "Abstained", "ProviderError",
        "Unavailable", "Timeout", "Timeout", "suggested");
  }

  @Test
  @DisplayName("US-36 AC-3 each fallback is logged with its reason and without the description")
  void fallbackLogged() {
    var logger = (Logger) LoggerFactory.getLogger(TriageService.class);
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);
    try {
      service().triage(EVENT, submitted().id());
      model.failure = NotTriagedReason.Timeout;
      var failed = submitted();
      clock.now = SUBMITTED;
      service().triage(EVENT, failed.id());
      model.failure = null;
      model.takes = Duration.ofSeconds(31);
      var late = submitted();
      clock.now = SUBMITTED;
      service().triage(EVENT, late.id());
      service().triage(EVENT, UUID.randomUUID());

      assertThat(appender.list).hasSize(4);
      assertThat(appender.list).extracting(ILoggingEvent::getLevel)
          .containsExactly(Level.INFO, Level.WARN, Level.WARN, Level.INFO);
      assertThat(appender.list.get(0).getFormattedMessage()).doesNotContain("fallback");
      assertThat(appender.list.get(1).getFormattedMessage()).contains(failed.id().toString())
          .contains("fallback Timeout").contains("in 10000 ms");
      assertThat(appender.list.get(2).getFormattedMessage()).contains(late.id().toString())
          .contains("fallback Timeout");
      assertThat(appender.list.get(3).getFormattedMessage()).doesNotContain("fallback");
      assertThat(appender.list).allSatisfy(e -> assertThat(e.getFormattedMessage()).doesNotContain(DESCRIPTION));
    } finally {
      logger.detachAppender(appender);
    }
  }
}
