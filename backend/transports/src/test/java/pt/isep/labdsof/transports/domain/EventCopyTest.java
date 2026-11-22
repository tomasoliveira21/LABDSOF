package pt.isep.labdsof.transports.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class EventCopyTest {

  private static final UUID EVENT = UUID.randomUUID();

  @Test
  @DisplayName("193 AC-5 an event copy needs its id and status and a version of at least 1")
  void anEventCopyNeedsItsFields() {
    var copy = new EventCopy(EVENT, EventStatus.Published, 3);
    assertThat(copy.eventId()).isEqualTo(EVENT);
    assertThat(copy.status()).isEqualTo(EventStatus.Published);
    assertThat(copy.version()).isEqualTo(3);

    assertThatThrownBy(() -> new EventCopy(null, EventStatus.Draft, 1))
        .isInstanceOf(NullPointerException.class).hasMessage("eventId");
    assertThatThrownBy(() -> new EventCopy(EVENT, null, 1))
        .isInstanceOf(NullPointerException.class).hasMessage("status");
    assertThatThrownBy(() -> new EventCopy(EVENT, EventStatus.Draft, 0))
        .isInstanceOf(IllegalArgumentException.class).hasMessage("version must be at least 1");
    assertThat(new EventCopy(EVENT, EventStatus.Draft, 1).version()).isEqualTo(1);
  }

  @ParameterizedTest(name = "{0}: {1}")
  @CsvSource({"Draft,false", "Published,false", "Suspended,true", "Closed,false"})
  @DisplayName("193 AC-5 only a suspended event reads as suspended")
  void onlyASuspendedEventIsSuspended(EventStatus status, boolean suspended) {
    assertThat(new EventCopy(EVENT, status, 1).isSuspended()).isEqualTo(suspended);
  }
}
