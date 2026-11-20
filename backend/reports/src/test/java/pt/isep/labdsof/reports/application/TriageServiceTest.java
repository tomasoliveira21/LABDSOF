package pt.isep.labdsof.reports.application;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
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

  final InMemoryPorts ports = new InMemoryPorts();
  final MovingClock clock = new MovingClock();
  final FakeModel model = new FakeModel();

  TriageService service() {
    model.clock = clock;
    return new TriageService(ports, model, clock);
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
    }, clock);
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
      assertThat(appender.list.get(0).getFormattedMessage()).contains(request.id().toString()).contains("Suggested");
    } finally {
      logger.detachAppender(appender);
    }
  }
}
