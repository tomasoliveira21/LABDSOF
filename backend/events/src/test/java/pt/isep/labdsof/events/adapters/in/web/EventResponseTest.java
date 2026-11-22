package pt.isep.labdsof.events.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import pt.isep.labdsof.events.application.EventView;
import pt.isep.labdsof.events.domain.DateRange;
import pt.isep.labdsof.events.domain.EventStatus;

class EventResponseTest {

  private static final UUID EVENT = UUID.fromString("11111111-2222-3333-4444-555555555555");

  private static EventView view(EventStatus status, String description, String joinCode) {
    return new EventView(EVENT, "Queima das Fitas 2027", "FAP",
        new DateRange(LocalDate.parse("2027-05-02"), LocalDate.parse("2027-05-09")), description, status, joinCode);
  }

  @ParameterizedTest(name = "[{index}] description \"{0}\"")
  @ValueSource(strings = {"Semana academica", ""})
  @DisplayName("192 AC-5 the response carries every field of the event view")
  void everyFieldIsCarried(String description) {
    var event = EventResponse.of(view(EventStatus.Draft, description, null));

    assertThat(event.eventId()).isEqualTo(EVENT);
    assertThat(event.name()).isEqualTo("Queima das Fitas 2027");
    assertThat(event.organiserName()).isEqualTo("FAP");
    assertThat(event.dates().start()).isEqualTo(LocalDate.parse("2027-05-02"));
    assertThat(event.dates().end()).isEqualTo(LocalDate.parse("2027-05-09"));
    assertThat(event.description()).isEqualTo(description);
  }

  @ParameterizedTest(name = "[{index}] join code {0}")
  @NullSource
  @ValueSource(strings = {"abc123"})
  @DisplayName("192 AC-5 the join link is given only when the view has a join code")
  void joinLinkOnlyWithACode(String code) {
    var event = EventResponse.of(view(EventStatus.Published, "x", code));

    assertThat(event.joinLink()).isEqualTo(code == null ? null : "/join/" + EVENT + "?code=abc123");
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @EnumSource(EventStatus.class)
  @DisplayName("192 AC-5 every event status maps to the contract's value")
  void everyStatusMaps(EventStatus status) {
    assertThat(EventResponse.of(view(status, "x", null)).status().getValue()).isEqualTo(status.name());
  }

  @Test
  @DisplayName("192 AC-5 the join code is URL-encoded in the join link")
  void joinCodeIsEncoded() {
    var event = EventResponse.of(view(EventStatus.Published, "x", "a b&c=d"));

    assertThat(event.joinLink()).isEqualTo("/join/" + EVENT + "?code=a+b%26c%3Dd");
  }
}
