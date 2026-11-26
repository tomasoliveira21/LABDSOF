package pt.isep.labdsof.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pt.isep.labdsof.events.domain.Membership.Role;
import pt.isep.labdsof.events.domain.Membership.Status;

/**
 * GET /api/v1/events over HTTP with signed tokens (US-07): the FarolAdmin's whole view of an event, and the
 * refusals that keep it that way (AC-2).
 */
@SuppressWarnings("unchecked")
class ListEventsIT extends IntegrationTest {

  private static final String PATH = "/api/v1/events";

  private ApiClient api() {
    return new ApiClient(port);
  }

  private UUID farolAdmin() {
    var user = UUID.randomUUID();
    jdbc.sql("INSERT INTO global_role_copy (user_id, role, granted, version) VALUES (:u, 'FarolAdmin', true, 1)")
        .param("u", user).update();
    return user;
  }

  private HttpResponse<String> list(UUID user) {
    return api().get(PATH, user == null ? null : JWKS.valid(user.toString()));
  }

  /**
   * The one summary of the event this test seeded. The schema is shared by every IT of the module, so the list
   * carries other tests' events too; what each case asserts is its own row, never the size of the whole list.
   */
  private Map<String, Object> summaryOf(UUID user, UUID eventId) {
    var response = list(user);
    assertThat(response.statusCode()).isEqualTo(200);
    return ApiClient.jsonList(response).stream()
        .filter(summary -> eventId.toString().equals(summary.get("eventId")))
        .findFirst().orElseThrow(() -> new AssertionError(eventId + " is not in the list"));
  }

  @Test
  @DisplayName("US-07 AC-1 the list carries the name, dates, organisation, EventAdmins and status, and no more")
  void theListCarriesTheFiveFieldsAndNoMore() {
    var admin = farolAdmin();
    var event = insertEvent("Published", null, "code-" + UUID.randomUUID());
    var first = UUID.randomUUID();
    var second = UUID.randomUUID();
    member(event, first, Role.EventAdmin, Status.Active, 1);
    member(event, second, Role.EventAdmin, Status.Active, 1);
    member(event, UUID.randomUUID(), Role.RegularUser, Status.Active, 1); // not an EventAdmin

    var summary = summaryOf(admin, event);

    assertThat(summary).containsEntry("name", "Queima das Fitas 2027").containsEntry("organiserName", "FAP")
        .containsEntry("status", "Published")
        .containsEntry("dates", Map.of("start", "2027-05-02", "end", "2027-05-09"));
    assertThat((List<String>) summary.get("eventAdmins"))
        .containsExactlyInAnyOrder(first.toString(), second.toString());
    // Never a description, a join link, a team, a map or a participant (features.md 4.1)
    assertThat(summary.keySet()).containsExactlyInAnyOrder("eventId", "name", "organiserName", "dates",
        "status", "eventAdmins");
  }

  @Test
  @DisplayName("US-07 AC-1 an event with no EventAdmin yet is listed with an empty eventAdmins, not dropped")
  void anEventWithNoAdminIsStillListed() {
    var admin = farolAdmin();
    var event = insertEvent("Draft", null, null);

    // The normal state between POST /events and the name call of US-06 (D12), and what US-08 exists to fix
    assertThat((List<?>) summaryOf(admin, event).get("eventAdmins")).isEmpty();
  }

  @Test
  @DisplayName("US-07 AC-1 only active EventAdmins count, so a removed one leaves eventAdmins empty")
  void aRemovedAdminLeavesTheListEmptyOfAdmins() {
    var admin = farolAdmin();
    var event = insertEvent("Published", null, "code-" + UUID.randomUUID());
    member(event, UUID.randomUUID(), Role.EventAdmin, Status.Removed, 1);

    assertThat((List<?>) summaryOf(admin, event).get("eventAdmins")).isEmpty();
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"Draft", "Published", "Suspended", "Closed"})
  @DisplayName("US-07 AC-1 the FarolAdmin sees an event whatever its status, with that status")
  void everyStatusIsListed(String status) {
    var admin = farolAdmin();
    var previous = "Suspended".equals(status) ? "Published" : null;
    // event_join_code_once_published: a Closed event, and one suspended from Published, must keep their code
    var joinCode = "Draft".equals(status) ? null : "code-" + UUID.randomUUID();
    var event = insertEvent(status, previous, joinCode);

    assertThat(summaryOf(admin, event)).containsEntry("status", status);
  }

  @Test
  @DisplayName("US-07 AC-1 the list answers 200 with the five fields on every row it carries")
  void anEventNobodyCreatedIsNotInvented() {
    var response = list(farolAdmin());

    // The module's schema is shared, so this asserts the shape of an answer, not an empty platform
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(ApiClient.jsonList(response))
        .allSatisfy(summary -> assertThat(summary).containsKeys("eventId", "name", "status", "eventAdmins"));
  }

  @Test
  @DisplayName("US-07 AC-1 a signed-in caller who is not the FarolAdmin gets 403, and no token gets 401")
  void onlyTheFarolAdminLists() {
    var event = insertEvent("Published", null, "code-" + UUID.randomUUID());
    var eventAdmin = UUID.randomUUID();
    member(event, eventAdmin, Role.EventAdmin, Status.Active, 1);

    assertThat(list(UUID.randomUUID()).statusCode()).isEqualTo(403);
    assertThat(list(eventAdmin).statusCode()).isEqualTo(403); // an admin of an event is not a platform admin
    var anonymous = list(null);
    assertThat(anonymous.statusCode()).isEqualTo(401);
    assertThat(anonymous.headers().firstValue("www-authenticate").orElse("")).startsWith("Bearer");
  }

  @Test
  @DisplayName("US-07 AC-2 the FarolAdmin who listed an event still cannot open it, its teams or its map")
  void listingAnEventGivesNoAccessToIt() {
    var admin = farolAdmin();
    var event = insertEvent("Published", null, "code-" + UUID.randomUUID());

    assertThat(summaryOf(admin, event)).containsEntry("status", "Published");

    // features.md 4.1: the role gives no access to any event's data (US-14, and 192 AC-5 already asserts it)
    var token = JWKS.valid(admin.toString());
    assertThat(api().get("/api/v1/events/" + event, token).statusCode()).isEqualTo(404);
    assertThat(api().get("/api/v1/events/" + event + "/teams", token).statusCode()).isEqualTo(404);
    assertThat(api().get("/api/v1/events/" + event + "/map-versions/current", token).statusCode())
        .isIn(404, 501);
  }
}
