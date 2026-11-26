package pt.isep.labdsof.events.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class EventTest {

  @ParameterizedTest(name = "{0} -> {1}")
  @CsvSource({"Draft,false", "Published,true", "Suspended,false", "Closed,true"})
  @DisplayName("192 AC-5 participants see an event only when it is Published or Closed")
  void participantsSeePublishedAndClosed(EventStatus status, boolean visible) {
    assertThat(status.visibleToParticipants()).isEqualTo(visible);
  }

  private static Event event(EventStatus status) {
    return new Event(UUID.randomUUID(), "Queima das Fitas 2027", "FAP",
        new DateRange(LocalDate.of(2027, 5, 2), LocalDate.of(2027, 5, 9)), "Semana academica", status,
        status == EventStatus.Draft ? null : "OLD-CODE", 3);
  }

  @Test
  @DisplayName("US-11 AC-1 a published draft keeps its details and takes the join code, one version later")
  void publishedDraftTakesTheJoinCodeAndTheNextVersion() {
    var draft = event(EventStatus.Draft);

    var published = draft.published("JOIN-CODE-1");

    assertThat(published).isEqualTo(new Event(draft.eventId(), draft.name(), draft.organiserName(), draft.dates(),
        draft.description(), EventStatus.Published, "JOIN-CODE-1", 4));
    assertThat(draft.status()).isEqualTo(EventStatus.Draft);
    assertThat(draft.joinCode()).isNull();
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(value = EventStatus.class, names = {"Published", "Suspended", "Closed"})
  @DisplayName("US-11 AC-1 only a draft publishes, so an event is never published twice")
  void onlyADraftPublishes(EventStatus status) {
    var event = event(status);

    assertThatThrownBy(() -> event.published("JOIN-CODE-1")).isInstanceOf(EventNotDraftException.class)
        .hasMessage("event is not a draft");
  }

  @Test
  @DisplayName("US-11 AC-1 an event is never published without a join code, and never holds a version below 1")
  void joinCodeIsRequiredAndVersionsStartAtOne() {
    var draft = event(EventStatus.Draft);

    assertThatThrownBy(() -> draft.published(null)).isInstanceOf(NullPointerException.class)
        .hasMessage("joinCode");
    assertThatThrownBy(() -> new Event(draft.eventId(), draft.name(), draft.organiserName(), draft.dates(),
        draft.description(), EventStatus.Draft, null, 0)).isInstanceOf(IllegalArgumentException.class)
        .hasMessage("version must be at least 1");
  }

  @Test
  @DisplayName("US-06 AC-1 a created event is a draft at version 1, with no join code and the details given")
  void createdEventIsADraftAtVersionOne() {
    var eventId = UUID.randomUUID();
    var dates = new DateRange(LocalDate.of(2027, 5, 2), LocalDate.of(2027, 5, 9));

    var created = Event.created(eventId, "Queima das Fitas 2027", "FAP", dates, "Semana academica");

    assertThat(created).isEqualTo(new Event(eventId, "Queima das Fitas 2027", "FAP", dates, "Semana academica",
        EventStatus.Draft, null, 1));
    assertThat(created.status().visibleToParticipants()).isFalse(); // a draft participants cannot see
  }

  @Test
  @DisplayName("US-06 AC-1 a created event takes no description as the empty one, and trims what it is given")
  void createdEventTakesNoDescriptionAsEmptyAndTrims() {
    var dates = new DateRange(LocalDate.of(2027, 5, 2), LocalDate.of(2027, 5, 9));

    assertThat(Event.created(UUID.randomUUID(), "Queima", "FAP", dates, null).description()).isEmpty();
    assertThat(Event.created(UUID.randomUUID(), "  Queima  ", "  FAP  ", dates, "").name()).isEqualTo("Queima");
    assertThat(Event.created(UUID.randomUUID(), "  Queima  ", "  FAP  ", dates, "").organiserName())
        .isEqualTo("FAP");
  }

  @ParameterizedTest(name = "{0} characters")
  @CsvSource({"1", "100"})
  @DisplayName("US-06 AC-1 a name and an organiser name of 1 and of 100 characters are both accepted")
  void nameBoundsAreInclusive(int length) {
    var dates = new DateRange(LocalDate.of(2027, 5, 2), LocalDate.of(2027, 5, 9));
    var value = "x".repeat(length);

    assertThat(Event.created(UUID.randomUUID(), value, value, dates, null).name()).hasSize(length);
  }

  @Test
  @DisplayName("US-06 AC-1 a blank or over-long name, organiser name or description is refused in the domain")
  void boundsAreRefusedInTheDomain() {
    var eventId = UUID.randomUUID();
    var dates = new DateRange(LocalDate.of(2027, 5, 2), LocalDate.of(2027, 5, 9));
    var tooLong = "x".repeat(101);

    assertThatThrownBy(() -> Event.created(eventId, "   ", "FAP", dates, null))
        .isInstanceOf(InvalidEventException.class).hasMessage("a name has 1 to 100 characters");
    assertThatThrownBy(() -> Event.created(eventId, tooLong, "FAP", dates, null))
        .isInstanceOf(InvalidEventException.class).hasMessage("a name has 1 to 100 characters");
    assertThatThrownBy(() -> Event.created(eventId, "Queima", "", dates, null))
        .isInstanceOf(InvalidEventException.class).hasMessage("an organiser name has 1 to 100 characters");
    assertThatThrownBy(() -> Event.created(eventId, "Queima", tooLong, dates, null))
        .isInstanceOf(InvalidEventException.class).hasMessage("an organiser name has 1 to 100 characters");
    assertThatThrownBy(() -> Event.created(eventId, "Queima", "FAP", dates, "x".repeat(2001)))
        .isInstanceOf(InvalidEventException.class).hasMessage("a description has at most 2000 characters");
    assertThat(Event.created(eventId, "Queima", "FAP", dates, "x".repeat(2000)).description()).hasSize(2000);
  }

  @Test
  @DisplayName("US-06 AC-1 a created event needs an id, a name, an organiser name and dates")
  void createdEventNeedsItsParts() {
    var dates = new DateRange(LocalDate.of(2027, 5, 2), LocalDate.of(2027, 5, 9));

    assertThatThrownBy(() -> Event.created(null, "Queima", "FAP", dates, null))
        .isInstanceOf(NullPointerException.class).hasMessage("eventId");
    assertThatThrownBy(() -> Event.created(UUID.randomUUID(), null, "FAP", dates, null))
        .isInstanceOf(NullPointerException.class).hasMessage("a name");
    assertThatThrownBy(() -> Event.created(UUID.randomUUID(), "Queima", null, dates, null))
        .isInstanceOf(NullPointerException.class).hasMessage("an organiser name");
    assertThatThrownBy(() -> Event.created(UUID.randomUUID(), "Queima", "FAP", null, null))
        .isInstanceOf(NullPointerException.class).hasMessage("dates");
  }

  @Test
  @DisplayName("192 AC-5 an event's dates may be a single day but never end before they start")
  void datesMayBeOneDayButNeverEndBeforeTheyStart() {
    var day = LocalDate.of(2027, 5, 2);

    assertThat(new DateRange(day, day).end()).isEqualTo(day);
    assertThat(new DateRange(day, day.plusDays(7)).start()).isEqualTo(day);
    assertThatThrownBy(() -> new DateRange(day, day.minusDays(1)))
        .isInstanceOf(IllegalArgumentException.class).hasMessage("the event cannot end before it starts");
    assertThatThrownBy(() -> new DateRange(null, day)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new DateRange(day, null)).isInstanceOf(NullPointerException.class);
  }
}
