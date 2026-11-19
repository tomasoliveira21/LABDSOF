package pt.isep.labdsof.reports.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HelpRequestTest {

  static final Instant NOW = Instant.parse("2027-05-08T23:14:05Z");
  static final Location POINT = new Location(3, 0.42, 0.77);

  static HelpRequest submit(String description) {
    return HelpRequest.submit(UUID.randomUUID(), UUID.randomUUID(), "user-1", UUID.randomUUID(),
        description, POINT, NOW);
  }

  @Test
  @DisplayName("194 AC-1 a new request is Submitted, triage Pending, with no category or priority")
  void newRequest() {
    var request = submit("Friend fainted near the bar");
    assertThat(request.status()).isEqualTo(HelpRequestStatus.Submitted);
    assertThat(request.triage()).isEqualTo(TriageSuggestion.pending());
    assertThat(request.category()).isNull();
    assertThat(request.priority()).isNull();
    assertThat(request.defaultTeamId()).isNull();
    assertThat(request.routedTeamId()).isNull();
    assertThat(request.submittedAt()).isEqualTo(NOW);
    assertThat(request.updatedAt()).isEqualTo(NOW);
    assertThat(request.version()).isEqualTo(1);
    assertThat(request.location()).isEqualTo(POINT);
    assertThat(request.requesterId()).isEqualTo("user-1");
  }

  @Test
  @DisplayName("194 AC-1 statuses and categories follow decisions 6 and 8")
  void enumsFollowTheMeetingDecisions() {
    assertThat(HelpRequestStatus.values()).extracting(Enum::name)
        .containsExactly("Submitted", "Seen", "OnTheWay", "Resolved", "Closed");
    assertThat(TriageState.values()).extracting(Enum::name)
        .containsExactly("Pending", "Suggested", "NotTriaged", "Accepted", "Changed");
    assertThat(Category.values()).extracting(Enum::name)
        .containsExactly("Health", "Safety", "LostPerson", "Infrastructure", "Other");
    assertThat(Priority.values()).extracting(Enum::name)
        .containsExactly("Low", "Medium", "High", "Critical");
  }

  @Test
  @DisplayName("194 AC-1 times are kept to the microsecond, as the database stores them")
  void microsecondPrecision() {
    var request = HelpRequest.submit(UUID.randomUUID(), UUID.randomUUID(), "user-1", UUID.randomUUID(),
        "help", POINT, NOW.plusNanos(123_456_789));
    assertThat(request.submittedAt()).isEqualTo(NOW.plusNanos(123_456_000));
    assertThat(request.updatedAt()).isEqualTo(NOW.plusNanos(123_456_000));
  }

  @Test
  @DisplayName("194 AC-4 a description of 1 and of 500 characters is accepted")
  void descriptionBounds() {
    assertThat(submit("x").description()).isEqualTo("x");
    assertThat(submit("x".repeat(500)).description()).hasSize(500);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  @DisplayName("194 AC-4 a blank description is refused")
  void blankDescription(String description) {
    assertThatThrownBy(() -> submit(description)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("194 AC-4 a missing or 501-character description is refused")
  void longOrMissingDescription() {
    var tooLong = "x".repeat(501);
    assertThatThrownBy(() -> submit(tooLong)).isInstanceOf(InvalidHelpRequestException.class);
    assertThatThrownBy(() -> submit(null)).isInstanceOf(InvalidHelpRequestException.class);
  }

  @Test
  @DisplayName("194 AC-4 the 500-character limit counts characters, not UTF-16 units, as varchar(500) does")
  void descriptionCountsCodePoints() {
    var ambulance = "🚑"; // one character, two UTF-16 units
    assertThat(submit(ambulance.repeat(500)).description()).hasSize(1000);
    var tooLong = ambulance.repeat(501);
    assertThatThrownBy(() -> submit(tooLong)).isInstanceOf(InvalidHelpRequestException.class);
  }

  @Test
  @DisplayName("194 AC-4 a description with a NUL character is refused, PostgreSQL cannot store it")
  void nulDescription() {
    assertThatThrownBy(() -> submit("help\u0000me")).isInstanceOf(IllegalArgumentException.class);
    assertThat(submit("help\u0001me").description()).isEqualTo("help\u0001me");
  }

  @Test
  @DisplayName("194 AC-4 a request without a requester is refused")
  void requesterRequired() {
    var id = UUID.randomUUID();
    var eventId = UUID.randomUUID();
    var clientRequestId = UUID.randomUUID();
    assertThatThrownBy(() -> HelpRequest.submit(id, eventId, " ", clientRequestId, "help", POINT, NOW))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> HelpRequest.submit(id, eventId, null, clientRequestId, "help", POINT, NOW))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("194 AC-4 a point outside the map is refused, its edges are accepted")
  void locationBounds() {
    assertThat(new Location(1, 0, 0)).isNotNull();
    assertThat(new Location(1, 1, 1)).isNotNull();
    assertThatThrownBy(() -> new Location(0, 0.5, 0.5)).isInstanceOf(InvalidHelpRequestException.class);
    assertThatThrownBy(() -> new Location(1, -0.01, 0.5)).isInstanceOf(InvalidHelpRequestException.class);
    assertThatThrownBy(() -> new Location(1, 1.01, 0.5)).isInstanceOf(InvalidHelpRequestException.class);
    assertThatThrownBy(() -> new Location(1, 0.5, -0.01)).isInstanceOf(InvalidHelpRequestException.class);
    assertThatThrownBy(() -> new Location(1, 0.5, 1.01)).isInstanceOf(InvalidHelpRequestException.class);
    assertThatThrownBy(() -> new Location(1, Double.NaN, 0.5))
        .isInstanceOf(InvalidHelpRequestException.class);
    assertThatThrownBy(() -> new Location(1, 0.5, Double.NaN))
        .isInstanceOf(InvalidHelpRequestException.class);
  }

  @Test
  @DisplayName("194 AC-1 a request read after the deadline shows NotTriaged and keeps the rest")
  void asSeenAt() {
    var request = submit("help");
    var seen = request.asSeenAt(NOW.plusSeconds(30));
    assertThat(seen.triage().state()).isEqualTo(TriageState.NotTriaged);
    assertThat(seen).usingRecursiveComparison().ignoringFields("triage").isEqualTo(request);
  }

  @Test
  @DisplayName("US-26 AC-1 only what changed after updatedSince is listed")
  void changedAfter() {
    var request = submit("help");
    var oneMicro = java.time.Duration.ofNanos(1_000);
    var later = NOW.plusSeconds(5);
    assertThat(request.changedAfter(NOW, later)).isFalse();
    assertThat(request.changedAfter(NOW.minus(oneMicro), later)).isTrue();
    assertThat(request.changedAfter(NOW.plusSeconds(1), later)).isFalse();
  }

  @Test
  @DisplayName("US-26 AC-1 a Pending request changes when its triage deadline passes")
  void deadlineIsAChange() {
    var request = submit("help");
    var deadline = NOW.plus(TriageSuggestion.TRIAGE_DEADLINE);
    var micro = java.time.Duration.ofNanos(1_000);
    // The deadline falls in (since, now]
    assertThat(request.changedAfter(deadline.minus(micro), deadline)).isTrue();
    assertThat(request.changedAfter(deadline.minus(micro), deadline.plusSeconds(9))).isTrue();
    // Not yet passed, or already reported by an earlier poll
    assertThat(request.changedAfter(deadline.minusSeconds(5), deadline.minus(micro))).isFalse();
    assertThat(request.changedAfter(deadline, deadline.plusSeconds(9))).isFalse();
    // A triaged request has no deadline to report
    var suggested = new HelpRequest(request.id(), request.eventId(), request.requesterId(),
        request.clientRequestId(), request.description(), request.location(), null, null,
        request.status(), null, null,
        new TriageSuggestion(TriageState.Suggested, Category.Health, Priority.High, "fainted", null),
        request.submittedAt(), request.updatedAt(), 2);
    assertThat(suggested.changedAfter(deadline.minus(micro), deadline)).isFalse();
  }
}
