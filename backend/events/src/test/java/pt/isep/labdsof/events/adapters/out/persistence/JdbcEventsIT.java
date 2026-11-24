package pt.isep.labdsof.events.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import pt.isep.labdsof.events.IntegrationTest;
import pt.isep.labdsof.events.application.Events;
import pt.isep.labdsof.events.application.Events.Write;
import pt.isep.labdsof.events.domain.Event;
import pt.isep.labdsof.events.domain.EventStatus;

/**
 * The event table through its port. The use case reads the event before it writes it, so the outcomes of a lost
 * race (US-11 D4) can only be reached here: by publishing twice from the same read, which is what two requests
 * arriving together do.
 */
class JdbcEventsIT extends IntegrationTest {

  private static final Instant AT = Instant.parse("2027-05-08T23:14:05Z");

  @Autowired Events events;

  private static String code() {
    return "je-" + UUID.randomUUID();
  }

  private Event draft() {
    return events.findById(insertEvent("Draft", null, null)).orElseThrow();
  }

  private String storedStatusAndCode(UUID eventId) {
    return jdbc.sql("SELECT status || ' ' || coalesce(join_code, '-') FROM event WHERE event_id = :id")
        .param("id", eventId).query(String.class).single();
  }

  @Test
  @DisplayName("US-11 AC-1 publishing a draft writes the status, the join code and the next version")
  void publishingADraftWritesEverything() {
    var draft = draft();
    var actor = UUID.randomUUID();
    var joinCode = code();

    assertThat(events.publish(draft.published(joinCode), actor, AT)).isEqualTo(Write.STORED);

    var published = events.findById(draft.eventId()).orElseThrow();
    assertThat(published.status()).isEqualTo(EventStatus.Published);
    assertThat(published.joinCode()).isEqualTo(joinCode);
    assertThat(published.version()).isEqualTo(2);
    assertThat(published.name()).isEqualTo(draft.name());
    assertThat(jdbc.sql("SELECT updated_by FROM event WHERE event_id = :id").param("id", draft.eventId())
        .query(UUID.class).single()).isEqualTo(actor);
  }

  @Test
  @DisplayName("US-11 AC-1 the second publish from the same read is refused, and the first one stands")
  void secondPublishFromTheSameReadIsRefused() {
    var read = draft();
    var first = code();

    // Both publishes start from the same read, as two requests arriving together do.
    assertThat(events.publish(read.published(first), UUID.randomUUID(), AT)).isEqualTo(Write.STORED);
    assertThat(events.publish(read.published(code()), UUID.randomUUID(), AT)).isEqualTo(Write.NOT_DRAFT);

    // Without the guard the second would have stored version 2 a second time, with another join code, and every
    // consumer that applies only a strictly newer version would have kept whichever arrived first.
    assertThat(storedStatusAndCode(read.eventId())).isEqualTo("Published " + first);
    assertThat(events.findById(read.eventId()).orElseThrow().version()).isEqualTo(2);
  }

  @Test
  @DisplayName("US-11 AC-1 a publish that lands on a draft at another version is a conflict")
  void publishLandingOnAnotherVersionIsAConflict() {
    var draft = draft();

    // The row is still a draft, but at version 1, so a publish built from a read of version 2 cannot land.
    var fromALaterRead = new Event(draft.eventId(), draft.name(), draft.organiserName(), draft.dates(),
        draft.description(), EventStatus.Published, code(), 3);

    assertThat(events.publish(fromALaterRead, UUID.randomUUID(), AT)).isEqualTo(Write.CONFLICT);
    assertThat(storedStatusAndCode(draft.eventId())).isEqualTo("Draft -");
  }

  @ParameterizedTest(name = "[{index}] {0} after {1}")
  @CsvSource(value = {"Published,", "Suspended,Draft", "Suspended,Published", "Closed,"})
  @DisplayName("US-11 AC-1 a publish of an event that is not a draft writes nothing")
  void publishOfSomethingOtherThanADraftWritesNothing(String status, String previous) {
    var stored = "Published".equals(status) || "Published".equals(previous) || "Closed".equals(status)
        ? code() : null;
    var eventId = insertEvent(status, previous, stored);
    var event = events.findById(eventId).orElseThrow();
    var attempt = new Event(eventId, event.name(), event.organiserName(), event.dates(), event.description(),
        EventStatus.Published, code(), event.version() + 1);

    assertThat(events.publish(attempt, UUID.randomUUID(), AT)).isEqualTo(Write.NOT_DRAFT);
    assertThat(storedStatusAndCode(eventId)).isEqualTo(status + " " + (stored == null ? "-" : stored));
  }

  @Test
  @DisplayName("US-11 AC-1 a join code already in the table is reported as taken")
  void joinCodeAlreadyInTheTableIsTaken() {
    var taken = code();
    insertEvent("Published", null, taken);
    var draft = draft();

    assertThat(events.publish(draft.published(taken), UUID.randomUUID(), AT)).isEqualTo(Write.CODE_TAKEN);
    assertThat(storedStatusAndCode(draft.eventId())).isEqualTo("Draft -");
  }

  @Test
  @DisplayName("US-11 AC-1 a publish of an event that is not there is reported as gone")
  void publishOfAnUnknownEventIsGone() {
    var draft = draft();
    var unknown = new Event(UUID.randomUUID(), draft.name(), draft.organiserName(), draft.dates(),
        draft.description(), EventStatus.Published, code(), 2);

    assertThat(events.publish(unknown, UUID.randomUUID(), AT)).isEqualTo(Write.GONE);
  }
}
