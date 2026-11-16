package pt.isep.labdsof.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import pt.isep.labdsof.reports.application.HelpRequestService;
import pt.isep.labdsof.reports.application.HelpRequests;
import pt.isep.labdsof.reports.application.LocalCopies;
import pt.isep.labdsof.reports.domain.Category;
import pt.isep.labdsof.reports.domain.HelpRequest;
import pt.isep.labdsof.reports.domain.HelpRequestStatus;
import pt.isep.labdsof.reports.domain.Location;
import pt.isep.labdsof.reports.domain.Membership;
import pt.isep.labdsof.reports.domain.NotTriagedReason;
import pt.isep.labdsof.reports.domain.Priority;
import pt.isep.labdsof.reports.domain.TriageState;
import pt.isep.labdsof.reports.domain.TriageSuggestion;

/** AC-1: help requests and their triage live in PostgreSQL, created by the Flyway migrations. */
class PersistenceIT extends IntegrationTest {

  static final Location POINT = new Location(3, 0.42, 0.77);

  @Autowired HelpRequestService service;

  /** A fresh Spring context on the same database: what a container restart does to the service. */
  static ConfigurableApplicationContext start() {
    return Infrastructure.startReports(Map.of());
  }

  @Test
  @DisplayName("194 AC-1 a saved request survives a restart")
  void survivesRestart() {
    var event = UUID.randomUUID();
    UUID id;
    try (var before = start()) {
      before.getBean(LocalCopies.class).upsertIfNewer(
          new Membership(event, "ana", Membership.Role.RegularUser, Membership.Status.Active, Set.of(), 1));
      id = before.getBean(HelpRequestService.class)
          .submit(event, "ana", UUID.randomUUID(), "Friend fainted", POINT).request().id();
    }
    try (var after = start()) {
      var saved = after.getBean(HelpRequests.class).find(event, id).orElseThrow();
      assertThat(saved.description()).isEqualTo("Friend fainted");
      assertThat(saved.requesterId()).isEqualTo("ana");
      assertThat(saved.location()).isEqualTo(POINT);
    }
  }

  @Test
  @DisplayName("194 AC-1 the request read back is the request saved")
  void roundTrip() {
    var event = UUID.randomUUID();
    member(event, "ana", Membership.Role.RegularUser);
    var saved = service.submit(event, "ana", UUID.randomUUID(), "help", POINT).request();
    assertThat(service.get(event, "ana", saved.id()).request()).isEqualTo(saved);
  }

  @Test
  @DisplayName("194 AC-1 every field of a request and its triage suggestion is stored and read back")
  void everyField(@Autowired HelpRequests requests) {
    var submitted = HelpRequest.submit(UUID.randomUUID(), UUID.randomUUID(), "ana", UUID.randomUUID(),
        "Friend fainted", POINT, java.time.Instant.now());
    var triaged = new HelpRequest(submitted.id(), submitted.eventId(), "ana",
        submitted.clientRequestId(), submitted.description(), POINT, Category.Health, Priority.High,
        HelpRequestStatus.OnTheWay, UUID.randomUUID(), UUID.randomUUID(),
        new TriageSuggestion(TriageState.Changed, Category.Safety, Priority.Critical, "Fainting reported",
            NotTriagedReason.ProviderError),
        submitted.submittedAt(), submitted.updatedAt(), 4);
    assertThat(requests.insertIfNew(triaged)).isTrue();
    assertThat(requests.insertIfNew(triaged)).isFalse();
    assertThat(requests.find(triaged.eventId(), triaged.id())).contains(triaged);
    assertThat(requests.findByClientRequestId(triaged.eventId(), "ana", triaged.clientRequestId()))
        .contains(triaged);
    assertThat(requests.find(UUID.randomUUID(), triaged.id())).isEmpty();
  }

  @Test
  @DisplayName("194 AC-1 the database refuses states outside decisions 6 and 8")
  void checkConstraints() {
    var event = UUID.randomUUID();
    member(event, "ana", Membership.Role.RegularUser);
    var id = service.submit(event, "ana", UUID.randomUUID(), "help", POINT).request().id();
    for (var update : Map.of(
        "UPDATE help_request SET status = 'Triaged' WHERE id = :id", "status",
        "UPDATE help_request SET status = 'Dispatched' WHERE id = :id", "status",
        "UPDATE help_request SET category = 'Theft' WHERE id = :id", "category",
        "UPDATE help_request SET priority = 'Urgent' WHERE id = :id", "priority",
        "UPDATE triage_suggestion SET state = 'NotTriaged' WHERE request_id = :id", "stored NotTriaged",
        "UPDATE help_request SET description = '   ' WHERE id = :id", "blank description").entrySet()) {
      assertThatThrownBy(() -> jdbc.sql(update.getKey()).param("id", id).update())
          .as(update.getValue()).isInstanceOf(DataIntegrityViolationException.class);
    }
    for (var category : new String[] {"Health", "Safety", "LostPerson", "Infrastructure", "Other"}) {
      assertThat(jdbc.sql("UPDATE help_request SET category = :c WHERE id = :id").param("c", category)
          .param("id", id).update()).isEqualTo(1);
    }
    for (var status : new String[] {"Submitted", "Seen", "OnTheWay", "Resolved", "Closed"}) {
      assertThat(jdbc.sql("UPDATE help_request SET status = :s WHERE id = :id").param("s", status)
          .param("id", id).update()).isEqualTo(1);
    }
  }

  @Test
  @DisplayName("194 AC-1 concurrent identical submits keep one request and one event")
  void concurrentSubmits() throws Exception {
    var event = UUID.randomUUID();
    var client = UUID.randomUUID();
    member(event, "ana", Membership.Role.RegularUser);
    Callable<HelpRequestService.Submitted> submit =
        () -> service.submit(event, "ana", client, "help", POINT);
    try (var pool = Executors.newFixedThreadPool(4)) {
      var results = pool.invokeAll(java.util.Collections.nCopies(4, submit));
      var ids = new java.util.HashSet<UUID>();
      int created = 0;
      for (var result : results) {
        ids.add(result.get().request().id());
        created += result.get().created() ? 1 : 0;
      }
      assertThat(ids).hasSize(1);
      assertThat(created).isEqualTo(1);
    }
    assertThat(jdbc.sql("SELECT count(*) FROM help_request WHERE event_id = :e").param("e", event)
        .query(Long.class).single()).isEqualTo(1);
    assertThat(jdbc.sql("SELECT count(*) FROM outbox WHERE body->>'eventId' = :e")
        .param("e", event.toString()).query(Long.class).single()).isEqualTo(1);
  }

  @Test
  @DisplayName("194 AC-1 the schema comes from the Flyway migrations")
  void flyway() {
    assertThat(jdbc.sql("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank")
        .query(String.class).list()).startsWith("1");
  }
}
