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

  static Team team(UUID eventId, Category category, boolean deleted, Permission... permissions) {
    return new Team(UUID.randomUUID(), eventId, "Team " + UUID.randomUUID(),
        java.util.Arrays.stream(permissions).map(p -> new Team.TeamPermission(p, java.util.Set.of())).toList(),
        java.util.Set.of(category), deleted, 1);
  }

  static Team responder(UUID eventId, Category category) {
    return team(eventId, category, false, Permission.HELP_REQUESTS_TRIAGE, Permission.HELP_REQUESTS_RESPOND);
  }

  @Test
  @DisplayName("US-34 AC-2 the event's only eligible team for the suggested category is the default team")
  void defaultTeam() {
    var request = withTriage(SUGGESTED);
    var health = responder(request.eventId(), Category.Health);
    var safety = responder(request.eventId(), Category.Safety);

    var proposed = request.withDefaultTeam(java.util.List.of(safety, health));

    assertThat(proposed.defaultTeamId()).isEqualTo(health.teamId());
    // Only the default team is added: the request is otherwise the same, not a new version
    assertThat(proposed).isEqualTo(new HelpRequest(request.id(), request.eventId(), request.requesterId(),
        request.clientRequestId(), request.description(), request.location(), request.category(),
        request.priority(), request.status(), health.teamId(), request.routedTeamId(), request.triage(),
        request.submittedAt(), request.updatedAt(), request.version()));
  }

  @Test
  @DisplayName("US-34 AC-2 no team for the category, or several, gives no default team")
  void noDefaultTeam() {
    var request = withTriage(SUGGESTED);
    var event = request.eventId();

    assertThat(request.withDefaultTeam(java.util.List.of()).defaultTeamId()).isNull();
    assertThat(request.withDefaultTeam(java.util.List.of(responder(event, Category.Safety))).defaultTeamId())
        .isNull();
    assertThat(request.withDefaultTeam(java.util.List.of(responder(event, Category.Health),
        responder(event, Category.Health))).defaultTeamId()).isNull();
  }

  @Test
  @DisplayName("US-34 AC-2 a deleted team or a team without HelpRequests.Respond is never the default")
  void ineligibleTeams() {
    var request = withTriage(SUGGESTED);
    var event = request.eventId();
    var deleted = team(event, Category.Health, true, Permission.HELP_REQUESTS_RESPOND);
    var triageOnly = team(event, Category.Health, false, Permission.HELP_REQUESTS_TRIAGE);
    var eligible = responder(event, Category.Health);

    assertThat(request.withDefaultTeam(java.util.List.of(deleted)).defaultTeamId()).isNull();
    assertThat(request.withDefaultTeam(java.util.List.of(triageOnly)).defaultTeamId()).isNull();
    assertThat(request.withDefaultTeam(java.util.List.of(deleted, triageOnly, eligible)).defaultTeamId())
        .isEqualTo(eligible.teamId());
  }

  @Test
  @DisplayName("US-34 AC-2 a team of another event is never the default")
  void otherEventsTeam() {
    var request = withTriage(SUGGESTED);
    var elsewhere = responder(UUID.randomUUID(), Category.Health);
    var own = responder(request.eventId(), Category.Health);

    assertThat(request.withDefaultTeam(java.util.List.of(elsewhere)).defaultTeamId()).isNull();
    assertThat(request.withDefaultTeam(java.util.List.of(elsewhere, own)).defaultTeamId()).isEqualTo(own.teamId());
  }

  @Test
  @DisplayName("US-34 AC-2 without a category or a suggestion there is no default team")
  void noCategory() {
    var pending = submit("Friend fainted near the bar");
    var teams = java.util.List.of(responder(pending.eventId(), Category.Health));
    assertThat(pending.withDefaultTeam(teams).defaultTeamId()).isNull();
    for (var triage : java.util.List.of(TriageSuggestion.notTriaged(NotTriagedReason.Timeout),
        TriageSuggestion.notTriaged(NotTriagedReason.Abstained))) {
      var request = pending.withTriage(triage, NOW.plusSeconds(4));
      assertThat(request.withDefaultTeam(java.util.List.of(responder(request.eventId(), Category.Health)))
          .defaultTeamId()).isNull();
    }
  }

  @Test
  @DisplayName("US-34 AC-2 a member's category wins over the suggested one")
  void membersCategory() {
    var request = withTriage(SUGGESTED);
    var health = responder(request.eventId(), Category.Health);
    var safety = responder(request.eventId(), Category.Safety);
    var teams = java.util.List.of(health, safety);

    assertThat(request.acceptSuggestion(LATER).request().withDefaultTeam(teams).defaultTeamId())
        .isEqualTo(health.teamId());
    assertThat(request.change(Category.Safety, null, LATER).request().withDefaultTeam(teams).defaultTeamId())
        .isEqualTo(safety.teamId());
    // Only the priority set: the suggested category still gives the team
    assertThat(request.change(null, Priority.Low, LATER).request().withDefaultTeam(teams).defaultTeamId())
        .isEqualTo(health.teamId());
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
    // LATER is 60 s after NOW, past the triage deadline, so the first request reads NotTriaged when it is acknowledged
    var requests = java.util.List.of(submit("help"), withTriage(SUGGESTED),
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
    // Within the deadline: still Pending, and it stays Pending
    var pending = submit("help").acknowledge(NOW.plusSeconds(1)).request();
    assertThat(pending.status()).isEqualTo(HelpRequestStatus.Seen);
    assertThat(pending.triage()).isEqualTo(TriageSuggestion.pending());
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
    // A blank name is no name; a long one is cut at 200 characters, code points, not UTF-16 units (G8 review)
    assertThat(new Actor("operator", " \t").name()).isNull();
    assertThat(new Actor("operator", "a".repeat(201)).name()).isEqualTo("a".repeat(200));
    assertThat(new Actor("operator", "😀".repeat(201)).name()).isEqualTo("😀".repeat(200));
    for (var userId : new String[] {null, "", " "}) {
      assertThatThrownBy(() -> new Actor(userId, "Ana")).isInstanceOf(IllegalArgumentException.class);
    }
  }

  // US-30: close (spec US-30 D1, D2)

  @ParameterizedTest
  @ValueSource(strings = {"Submitted", "Seen", "OnTheWay", "Resolved"})
  @DisplayName("US-30 AC-1 closing moves any open status to Closed and records it")
  void close(String status) {
    var open = withStatus(HelpRequestStatus.valueOf(status));
    var suggested = open.withTriage(SUGGESTED, NOW.plusSeconds(4));
    // Pending (read NotTriaged at LATER, past the deadline), Suggested, Accepted, Changed and a stored NotTriaged
    for (var request : java.util.List.of(open, suggested, suggested.acceptSuggestion(LATER).request(),
        suggested.change(Category.Safety, null, LATER).request(),
        open.withTriage(TriageSuggestion.notTriaged(NotTriagedReason.Abstained), NOW.plusSeconds(4)))) {
      var decision = request.close(LATER);
      var closed = decision.request();

      assertThat(closed.status()).isEqualTo(HelpRequestStatus.Closed);
      assertThat(closed.version()).isEqualTo(request.version() + 1);
      assertThat(closed.updatedAt()).isEqualTo(LATER.truncatedTo(java.time.temporal.ChronoUnit.MICROS));
      // The triage, the values and the times of the request stay as they were (#177)
      assertThat(closed).usingRecursiveComparison().ignoringFields("status", "updatedAt", "version")
          .isEqualTo(request);
      assertThat(decision.changes()).containsExactly(new HelpRequestChange("status", status, "Closed"));
    }
  }

  @Test
  @DisplayName("US-30 AC-1 a closed request cannot be closed again")
  void closeOnce() {
    var closed = submit("help").close(LATER).request();
    assertThatThrownBy(() -> closed.close(LATER)).isInstanceOf(RequestConflictException.class)
        .hasMessage("The request is closed");
  }

  @Test
  @DisplayName("US-30 AC-1 a closed request refuses the control room's changes")
  void closedIsFinal() {
    var closed = withTriage(SUGGESTED).close(LATER).request();
    var later = LATER.plusSeconds(1);
    assertThatThrownBy(() -> closed.acceptSuggestion(later)).isInstanceOf(RequestConflictException.class)
        .hasMessage("The request is closed");
    assertThatThrownBy(() -> closed.change(Category.Safety, Priority.Low, later))
        .isInstanceOf(RequestConflictException.class).hasMessage("The request is closed");
    assertThatThrownBy(() -> closed.acknowledge(later)).isInstanceOf(RequestConflictException.class);
    // Even a change to the values it already has: the answer does not depend on the values sent
    var accepted = withTriage(SUGGESTED).acceptSuggestion(LATER).request().close(later).request();
    assertThatThrownBy(() -> accepted.change(Category.Health, Priority.High, later))
        .isInstanceOf(RequestConflictException.class).hasMessage("The request is closed");
    // A body that is invalid in itself is invalid whatever the status (G8 review)
    assertThatThrownBy(() -> closed.change(null, null, later)).isInstanceOf(InvalidHelpRequestException.class);
  }

  @Test
  @DisplayName("US-30 AC-1 a closed request awaits no triage and is not listed again at its deadline")
  void closedAwaitsNoTriage() {
    var open = submit("help");
    var closed = open.close(NOW.plusSeconds(3)).request();
    assertThat(open.awaitsModel()).isTrue();
    assertThat(closed.awaitsModel()).isFalse();
    assertThat(withTriage(SUGGESTED).awaitsModel()).isFalse();
    // A poll whose window holds the triage deadline lists a Pending open request again (US-26), never a closed one
    var since = NOW.plusSeconds(10);
    var now = NOW.plusSeconds(40);
    assertThat(open.changedAfter(since, now)).isTrue();
    assertThat(closed.changedAfter(since, now)).isFalse();
    assertThat(closed.changedAfter(NOW.plusSeconds(2), now)).isTrue();
  }
}
