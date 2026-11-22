package pt.isep.labdsof.events.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class EventTest {

  @ParameterizedTest(name = "{0} -> {1}")
  @CsvSource({"Draft,false", "Published,true", "Suspended,false", "Closed,true"})
  @DisplayName("192 AC-5 participants see an event only when it is Published or Closed")
  void participantsSeePublishedAndClosed(EventStatus status, boolean visible) {
    assertThat(status.visibleToParticipants()).isEqualTo(visible);
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
