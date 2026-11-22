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

  @Test
  @DisplayName("229 AC-3 a request with a stored reason is not listed again at its deadline")
  void storedReasonNotListedAgain() {
    var request = submit("Friend fainted near the bar");
    var failed = request.withTriage(TriageSuggestion.notTriaged(NotTriagedReason.ProviderError), NOW);
    var deadline = NOW.plus(TriageSuggestion.TRIAGE_DEADLINE);
    assertThat(failed.changedAfter(NOW, deadline)).isFalse();
    assertThat(request.changedAfter(NOW, deadline)).isTrue();
  }

  @Test
  @DisplayName("229 AC-1 a triage write keeps the request's own fields and moves updatedAt only")
  void withTriage() {
    var request = submit("Friend fainted near the bar");
    var later = NOW.plusNanos(1_234_567_891);
    var suggested = request.withTriage(TriageSuggestion.suggested(Category.Health, Priority.High, "r"), later);
    assertThat(suggested.triage().state()).isEqualTo(TriageState.Suggested);
    assertThat(suggested.category()).isNull();
    assertThat(suggested.priority()).isNull();
    assertThat(suggested.updatedAt()).isEqualTo(later.truncatedTo(java.time.temporal.ChronoUnit.MICROS));
    assertThat(suggested.version()).isEqualTo(request.version());
    assertThat(suggested.submittedAt()).isEqualTo(request.submittedAt());
    assertThat(suggested).usingRecursiveComparison().ignoringFields("triage", "updatedAt").isEqualTo(request);
  }

  static HelpRequest withTriage(TriageSuggestion triage) {
    return submit("Friend fainted near the bar").withTriage(triage, NOW.plusSeconds(4));
  }

  static final TriageSuggestion SUGGESTED = TriageSuggestion.suggested(Category.Health, Priority.High, "fainted");
  static final Instant LATER = NOW.plusSeconds(60).plusNanos(1_234_567_891);

  @Test
  @DisplayName("US-35 AC-1 accepting takes the suggested values and records each change")
  void accept() {
    var request = withTriage(SUGGESTED);
    var decision = request.acceptSuggestion(LATER);
    var accepted = decision.request();

    assertThat(accepted.category()).isEqualTo(Category.Health);
    assertThat(accepted.priority()).isEqualTo(Priority.High);
    assertThat(accepted.triage()).isEqualTo(
        new TriageSuggestion(TriageState.Accepted, Category.Health, Priority.High, "fainted", null));
    assertThat(accepted.version()).isEqualTo(request.version() + 1);
    assertThat(accepted.updatedAt()).isEqualTo(LATER.truncatedTo(java.time.temporal.ChronoUnit.MICROS));
    assertThat(accepted).usingRecursiveComparison()
        .ignoringFields("category", "priority", "triage", "updatedAt", "version").isEqualTo(request);
    assertThat(decision.changes()).containsExactly(
        new HelpRequestChange("category", null, "Health"),
        new HelpRequestChange("priority", null, "High"),
        new HelpRequestChange("triage", "Suggested", "Accepted"));
  }

  @Test
  @DisplayName("US-35 AC-1 only a suggestion can be accepted")
  void acceptNeedsASuggestion() {
    var pending = submit("help");
    var stored = withTriage(TriageSuggestion.notTriaged(NotTriagedReason.Abstained));
    var accepted = withTriage(SUGGESTED).acceptSuggestion(LATER).request();
    var changed = withTriage(SUGGESTED).change(Category.Safety, null, LATER).request();
    for (var request : java.util.List.of(pending, stored, accepted, changed)) {
      assertThatThrownBy(() -> request.acceptSuggestion(LATER)).as(request.triage().toString())
          .isInstanceOf(RequestConflictException.class);
    }
    // Pending past its deadline reads NotTriaged/Timeout: nothing to accept either
    assertThatThrownBy(() -> pending.acceptSuggestion(NOW.plusSeconds(31)))
        .isInstanceOf(RequestConflictException.class);
  }

  @Test
  @DisplayName("US-35 AC-2 changing records Changed and only the fields that changed")
  void change() {
    var request = withTriage(SUGGESTED);
    var decision = request.change(null, Priority.Critical, LATER);
    var changed = decision.request();

    assertThat(changed.category()).isNull();
    assertThat(changed.priority()).isEqualTo(Priority.Critical);
    // The suggestion stays visible next to the member's values
    assertThat(changed.triage()).isEqualTo(
        new TriageSuggestion(TriageState.Changed, Category.Health, Priority.High, "fainted", null));
    assertThat(changed.version()).isEqualTo(request.version() + 1);
    assertThat(changed.updatedAt()).isEqualTo(LATER.truncatedTo(java.time.temporal.ChronoUnit.MICROS));
    assertThat(decision.changes()).containsExactly(
        new HelpRequestChange("priority", null, "Critical"),
        new HelpRequestChange("triage", "Suggested", "Changed"));

    var again = changed.change(Category.Safety, Priority.Critical, LATER.plusSeconds(1));
    assertThat(again.changes()).containsExactly(new HelpRequestChange("category", null, "Safety"));
    assertThat(again.request().triage().state()).isEqualTo(TriageState.Changed);
    assertThat(again.request().version()).isEqualTo(changed.version() + 1);
  }

  @Test
  @DisplayName("US-35 AC-2 a member can set the values from any triage state")
  void changeFromAnyState() {
    var pending = submit("help").change(Category.Other, Priority.Low, NOW.plusSeconds(1));
    assertThat(pending.request().triage()).isEqualTo(
        new TriageSuggestion(TriageState.Changed, null, null, null, null));
    assertThat(pending.changes()).contains(new HelpRequestChange("triage", "Pending", "Changed"));

    // A derived NotTriaged keeps its reason, so the read still says why there was no suggestion
    var timedOut = submit("help").change(Category.Other, null, NOW.plusSeconds(30));
    assertThat(timedOut.request().triage()).isEqualTo(
        new TriageSuggestion(TriageState.Changed, null, null, null, NotTriagedReason.Timeout));
    assertThat(timedOut.changes()).contains(new HelpRequestChange("triage", "NotTriaged", "Changed"));

    var stored = withTriage(TriageSuggestion.notTriaged(NotTriagedReason.ProviderError))
        .change(null, Priority.Medium, LATER);
    assertThat(stored.request().triage()).isEqualTo(
        new TriageSuggestion(TriageState.Changed, null, null, null, NotTriagedReason.ProviderError));
    assertThat(stored.request().asSeenAt(LATER).triage().state()).isEqualTo(TriageState.Changed);

    var accepted = withTriage(SUGGESTED).acceptSuggestion(LATER).request();
    var corrected = accepted.change(null, Priority.Critical, LATER);
    assertThat(corrected.request().triage().state()).isEqualTo(TriageState.Changed);
    assertThat(corrected.changes()).containsExactly(
        new HelpRequestChange("priority", "High", "Critical"),
        new HelpRequestChange("triage", "Accepted", "Changed"));
  }

  @Test
  @DisplayName("US-35 AC-2 a change to the same values writes nothing")
  void changeToTheSameValues() {
    var changed = withTriage(SUGGESTED).change(Category.Health, Priority.High, LATER).request();
    var same = changed.change(Category.Health, null, LATER.plusSeconds(1));
    assertThat(same.changes()).isEmpty();
    assertThat(same.request()).isEqualTo(changed);

    // An accepted request sent its own values again stays Accepted (G8 review)
    var accepted = withTriage(SUGGESTED).acceptSuggestion(LATER).request();
    var resent = accepted.change(Category.Health, Priority.High, LATER.plusSeconds(1));
    assertThat(resent.changes()).isEmpty();
    assertThat(resent.request()).isEqualTo(accepted);
  }

  @Test
  @DisplayName("US-35 AC-2 a change needs a category or a priority")
  void changeNeedsAValue() {
    var request = withTriage(SUGGESTED);
    assertThatThrownBy(() -> request.change(null, null, LATER)).isInstanceOf(InvalidHelpRequestException.class);
  }

  static HelpRequest withStatus(HelpRequestStatus status) {
    var r = submit("help");
    return new HelpRequest(r.id(), r.eventId(), r.requesterId(), r.clientRequestId(), r.description(), r.location(),
        r.category(), r.priority(), status, r.defaultTeamId(), r.routedTeamId(), r.triage(), r.submittedAt(),
        r.updatedAt(), r.version());
  }

  @Test
  @DisplayName("US-28 AC-1 acknowledging moves Submitted to Seen and records the status")
  void acknowledge() {
    var pendingPastDeadline = submit("help");
    var requests = java.util.List.of(submit("help"), withTriage(SUGGESTED), pendingPastDeadline,
        withTriage(SUGGESTED).acceptSuggestion(LATER).request(),
        withTriage(SUGGESTED).change(Category.Safety, null, LATER).request());
    for (var request : requests) {
      var decision = request.acknowledge(LATER);
      var seen = decision.request();

      assertThat(seen.status()).isEqualTo(HelpRequestStatus.Seen);
      assertThat(seen.version()).isEqualTo(request.version() + 1);
      assertThat(seen.updatedAt()).isEqualTo(LATER.truncatedTo(java.time.temporal.ChronoUnit.MICROS));
      // The triage keeps its stored state: the status and the triage are separate (#177)
      assertThat(seen).usingRecursiveComparison().ignoringFields("status", "updatedAt", "version").isEqualTo(request);
      assertThat(decision.changes()).containsExactly(new HelpRequestChange("status", "Submitted", "Seen"));
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"Seen", "OnTheWay", "Resolved", "Closed"})
  @DisplayName("US-28 AC-1 only a Submitted request can be acknowledged")
  void acknowledgeOnlySubmitted(String status) {
    var request = withStatus(HelpRequestStatus.valueOf(status));
    assertThatThrownBy(() -> request.acknowledge(LATER)).isInstanceOf(RequestConflictException.class)
        .hasMessage("The request is no longer Submitted");
  }

  @Test
  @DisplayName("US-28 AC-1 a change is made by a member with a user ID, the name may be missing")
  void actor() {
    assertThat(new Actor("operator", null).name()).isNull();
    assertThat(new Actor("operator", "Ana").userId()).isEqualTo("operator");
    for (var userId : new String[] {null, "", " "}) {
      assertThatThrownBy(() -> new Actor(userId, "Ana")).isInstanceOf(IllegalArgumentException.class);
    }
  }
}
