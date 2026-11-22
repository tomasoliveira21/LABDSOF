package pt.isep.labdsof.events.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import pt.isep.labdsof.events.application.AccessAudit.Refusal;
import pt.isep.labdsof.events.application.InMemoryPorts.Refused;
import pt.isep.labdsof.events.application.InMemoryPorts.Write;
import pt.isep.labdsof.events.domain.DateRange;
import pt.isep.labdsof.events.domain.Event;
import pt.isep.labdsof.events.domain.EventStatus;
import pt.isep.labdsof.events.domain.HelpCategory;
import pt.isep.labdsof.events.domain.InvalidTeamException;
import pt.isep.labdsof.events.domain.Membership;
import pt.isep.labdsof.events.domain.Membership.Role;
import pt.isep.labdsof.events.domain.Membership.Status;
import pt.isep.labdsof.events.domain.Permission;
import pt.isep.labdsof.events.domain.ReportType;
import pt.isep.labdsof.events.domain.Team;
import pt.isep.labdsof.events.domain.TeamPermission;

class TeamServiceTest {

  private static final Instant NOW = Instant.parse("2027-05-08T23:14:05Z");

  private final InMemoryPorts ports = new InMemoryPorts();
  private final TeamService service = new TeamService(ports, ports, ports, ports, ports, ports,
      Clock.fixed(NOW, ZoneOffset.UTC));
  private final UUID user = UUID.randomUUID();
  private final UUID eventId = UUID.randomUUID();

  private void event(EventStatus status) {
    ports.events.put(eventId, new Event(eventId, "Queima das Fitas 2027", "FAP",
        new DateRange(LocalDate.of(2027, 5, 2), LocalDate.of(2027, 5, 9)), "Semana academica", status, null, 1));
  }

  private long membershipVersion = 1;

  /** Each call is a newer version of the copy, so a test can change the caller's teams. */
  private void member(Role role, Status status, UUID... teamIds) {
    ports.upsertIfNewer(new Membership(eventId, user, role, status, Set.of(teamIds), membershipVersion++));
  }

  private Team team(String name, Permission... permissions) {
    var team = new Team(UUID.randomUUID(), eventId, name,
        List.of(permissions).stream().map(TeamPermission::of).toList(), Set.of(), false, 1);
    ports.teams.put(team.teamId(), team);
    return team;
  }

  private static List<String> payloadNames(List<OutgoingEvent> published) {
    return published.stream().map(event -> (String) event.payload().get("name")).toList();
  }

  @Test
  @DisplayName("US-16 AC-1 an EventAdmin creates a team with no permissions and no categories, and it is published")
  void createTeam() {
    event(EventStatus.Draft);
    member(Role.EventAdmin, Status.Active);

    var created = service.create(user, eventId, "  Nursing  ");

    assertThat(created.name()).isEqualTo("Nursing");
    assertThat(created.permissions()).isEmpty();
    assertThat(created.handledCategories()).isEmpty();
    assertThat(created.version()).isEqualTo(1);
    assertThat(service.list(user, eventId)).containsExactly(created);
    assertThat(ports.writes).containsExactly(new Write(created.teamId(), user, NOW, 1));
    assertThat(ports.published).hasSize(1);
    var published = ports.published.getFirst();
    assertThat(published.type()).isEqualTo("TeamChanged");
    assertThat(published.eventId()).isEqualTo(eventId);
    assertThat(published.actorUserId()).isEqualTo(user.toString());
    assertThat(published.occurredAt()).isEqualTo(NOW);
    assertThat(published.payload()).containsEntry("name", "Nursing").containsEntry("deleted", false)
        .containsEntry("version", 1L);
  }

  @Test
  @DisplayName("US-16 AC-2 a duplicate name is refused and nothing is published")
  void duplicateNameIsRefused() {
    event(EventStatus.Published);
    member(Role.EventAdmin, Status.Active);
    service.create(user, eventId, "Control room");
    ports.published.clear();

    assertThatThrownBy(() -> service.create(user, eventId, "control ROOM"))
        .isInstanceOf(TeamNameTakenException.class);
    assertThatThrownBy(() -> service.create(user, eventId, " Control room "))
        .isInstanceOf(TeamNameTakenException.class);

    assertThat(service.list(user, eventId)).hasSize(1);
    assertThat(ports.published).isEmpty();
  }

  @Test
  @DisplayName("US-16 AC-2 the name of a deleted team is free again")
  void nameOfDeletedTeamIsFree() {
    event(EventStatus.Published);
    member(Role.EventAdmin, Status.Active);
    var first = service.create(user, eventId, "Logistics");
    service.delete(user, eventId, first.teamId());

    var second = service.create(user, eventId, "logistics");

    assertThat(second.teamId()).isNotEqualTo(first.teamId());
    assertThat(service.list(user, eventId)).containsExactly(second);
  }

  @Test
  @DisplayName("US-16 AC-1 a rename and the handled categories are one change, with the next version")
  void renameAndCategories() {
    event(EventStatus.Published);
    member(Role.EventAdmin, Status.Active);
    var created = service.create(user, eventId, "Nursing");
    ports.published.clear();

    var updated = service.update(user, eventId, created.teamId(), "Medical",
        List.of(HelpCategory.Safety, HelpCategory.Health));

    assertThat(updated.name()).isEqualTo("Medical");
    assertThat(updated.handledCategories()).containsExactly(HelpCategory.Health, HelpCategory.Safety);
    assertThat(updated.version()).isEqualTo(2);
    assertThat(service.list(user, eventId)).containsExactly(updated);
    assertThat(ports.writes).contains(new Write(created.teamId(), user, NOW, 2));
    assertThat(ports.published).hasSize(1);
    assertThat(ports.published.getFirst().payload())
        .containsEntry("handledCategories", List.of("Health", "Safety"))
        .containsEntry("version", 2L);
  }

  @Test
  @DisplayName("US-16 AC-1 a rename to the same name still publishes one change with the next version")
  void renameToTheSameName() {
    event(EventStatus.Published);
    member(Role.EventAdmin, Status.Active);
    var created = service.create(user, eventId, "Security");
    ports.published.clear();

    var updated = service.update(user, eventId, created.teamId(), "Security", List.of());

    assertThat(updated.version()).isEqualTo(2);
    assertThat(payloadNames(ports.published)).containsExactly("Security");
  }

  @Test
  @DisplayName("US-16 AC-2 a rename to a name already taken is refused and nothing is published")
  void renameToATakenName() {
    event(EventStatus.Published);
    member(Role.EventAdmin, Status.Active);
    var nursing = service.create(user, eventId, "Nursing");
    service.create(user, eventId, "Security");
    ports.published.clear();

    assertThatThrownBy(() -> service.update(user, eventId, nursing.teamId(), "SECURITY", List.of()))
        .isInstanceOf(TeamNameTakenException.class);

    assertThat(ports.published).isEmpty();
    assertThat(service.list(user, eventId)).extracting(Team::name).containsExactly("Nursing", "Security");
  }

  @Test
  @DisplayName("US-16 AC-3 a deleted team leaves the list and is published as deleted with a higher version")
  void deleteTeam() {
    event(EventStatus.Published);
    member(Role.EventAdmin, Status.Active);
    var created = service.create(user, eventId, "Nursing");
    ports.published.clear();

    service.delete(user, eventId, created.teamId());

    assertThat(service.list(user, eventId)).isEmpty();
    assertThat(ports.published).hasSize(1);
    assertThat(ports.published.getFirst().payload()).containsEntry("deleted", true).containsEntry("version", 2L);
    assertThat(ports.writes).contains(new Write(created.teamId(), user, NOW, 2));
  }

  @Test
  @DisplayName("US-16 AC-3 deleting a team that is already deleted is not found")
  void deleteTwice() {
    event(EventStatus.Published);
    member(Role.EventAdmin, Status.Active);
    var created = service.create(user, eventId, "Nursing");
    service.delete(user, eventId, created.teamId());
    ports.published.clear();

    assertThatThrownBy(() -> service.delete(user, eventId, created.teamId()))
        .isInstanceOf(TeamNotFoundException.class);

    assertThat(ports.published).isEmpty();
    assertThat(ports.refusals).contains(new Refused(user, eventId, NOW, Refusal.NO_SUCH_TEAM));
  }

  @Test
  @DisplayName("US-16 AC-3 a team deleted between the read and the write is not found, and nothing is published")
  void teamVanishesBetweenReadAndWrite() {
    event(EventStatus.Published);
    member(Role.EventAdmin, Status.Active);
    var created = service.create(user, eventId, "Nursing");
    ports.published.clear();
    ports.teamVanishesOnWrite = true;

    assertThatThrownBy(() -> service.delete(user, eventId, created.teamId()))
        .isInstanceOf(TeamNotFoundException.class);
    assertThatThrownBy(() -> service.update(user, eventId, created.teamId(), "Medical", List.of()))
        .isInstanceOf(TeamNotFoundException.class);

    assertThat(ports.published).isEmpty();
    assertThat(ports.refusals).hasSize(2)
        .allMatch(refused -> refused.refusal() == Refusal.NO_SUCH_TEAM && refused.at().equals(NOW));
  }

  @Test
  @DisplayName("US-16 AC-3 a team changed between the read and the write is a conflict, and nothing is published")
  void teamChangesBetweenReadAndWrite() {
    event(EventStatus.Published);
    member(Role.EventAdmin, Status.Active);
    var created = service.create(user, eventId, "Nursing");
    ports.published.clear();
    ports.teamChangesOnWrite = true;

    assertThatThrownBy(() -> service.delete(user, eventId, created.teamId()))
        .isInstanceOf(TeamChangedConcurrentlyException.class);
    assertThatThrownBy(() -> service.update(user, eventId, created.teamId(), "Medical", List.of()))
        .isInstanceOf(TeamChangedConcurrentlyException.class);

    // The point of the guard: a refused write publishes nothing, so no version is ever carried by two messages.
    assertThat(ports.published).isEmpty();
  }

  @Test
  @DisplayName("US-16 AC-1 an unknown team, or a team of another event, is not found and the refusal is recorded")
  void unknownTeam() {
    event(EventStatus.Published);
    member(Role.EventAdmin, Status.Active);
    var otherEvent = UUID.randomUUID();
    var elsewhere = new Team(UUID.randomUUID(), otherEvent, "Nursing", List.of(), Set.of(), false, 1);
    ports.teams.put(elsewhere.teamId(), elsewhere);

    assertThatThrownBy(() -> service.update(user, eventId, UUID.randomUUID(), "X", List.of()))
        .isInstanceOf(TeamNotFoundException.class);
    assertThatThrownBy(() -> service.update(user, eventId, elsewhere.teamId(), "X", List.of()))
        .isInstanceOf(TeamNotFoundException.class);
    assertThatThrownBy(() -> service.delete(user, eventId, elsewhere.teamId()))
        .isInstanceOf(TeamNotFoundException.class);

    assertThat(ports.refusals).hasSize(3)
        .allMatch(refused -> refused.refusal() == Refusal.NO_SUCH_TEAM && refused.at().equals(NOW));
    assertThat(ports.published).isEmpty();
  }

  @Test
  @DisplayName("US-16 AC-1 a draft and a published event accept team changes")
  void draftAndPublishedAccept() {
    for (var status : List.of(EventStatus.Draft, EventStatus.Published)) {
      event(status);
      member(Role.EventAdmin, Status.Active);

      var created = service.create(user, eventId, "Team " + status);
      service.update(user, eventId, created.teamId(), "Team " + status, List.of(HelpCategory.Other));
      service.delete(user, eventId, created.teamId());

      assertThat(service.list(user, eventId)).isEmpty();
    }
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @EnumSource(value = EventStatus.class, names = {"Suspended", "Closed"})
  @DisplayName("US-16 AC-1 a suspended or closed event refuses every team change, and nothing is published")
  void suspendedOrClosedRefusesChanges(EventStatus status) {
    event(EventStatus.Published);
    member(Role.EventAdmin, Status.Active);
    var created = service.create(user, eventId, "Nursing");
    ports.published.clear();
    event(status);

    assertThatThrownBy(() -> service.create(user, eventId, "Security"))
        .isInstanceOf(EventNotOpenException.class);
    assertThatThrownBy(() -> service.update(user, eventId, created.teamId(), "Medical", List.of()))
        .isInstanceOf(EventNotOpenException.class);
    assertThatThrownBy(() -> service.delete(user, eventId, created.teamId()))
        .isInstanceOf(EventNotOpenException.class);

    assertThat(ports.published).isEmpty();
    assertThat(service.list(user, eventId)).containsExactly(created);
  }

  @Test
  @DisplayName("US-16 AC-1 an EventAdmin reads the teams of a suspended or closed event")
  void adminStillReadsASuspendedEvent() {
    event(EventStatus.Published);
    member(Role.EventAdmin, Status.Active);
    var created = service.create(user, eventId, "Nursing");

    event(EventStatus.Suspended);

    assertThat(service.list(user, eventId)).containsExactly(created);
  }

  @Test
  @DisplayName("US-16 AC-1 triage and review members read the list, others do not")
  void triageAndReviewRead() {
    event(EventStatus.Published);
    for (var permission : Permission.values()) {
      var team = team("Team " + permission.name(), permission);
      member(Role.RegularUser, Status.Active, team.teamId());

      if (permission.readsTeams()) {
        assertThat(service.list(user, eventId)).as(permission.code()).isNotEmpty();
      } else {
        assertThatThrownBy(() -> service.list(user, eventId)).as(permission.code())
            .isInstanceOf(ForbiddenException.class);
      }
      ports.teams.clear();
    }
  }

  @Test
  @DisplayName("US-16 AC-1 a member with no team at all is refused the list")
  void memberWithoutTeamIsRefused() {
    event(EventStatus.Published);
    member(Role.RegularUser, Status.Active);
    team("Control room", Permission.HELP_REQUESTS_TRIAGE);

    assertThatThrownBy(() -> service.list(user, eventId)).isInstanceOf(ForbiddenException.class);
    assertThat(ports.refusals).isEmpty();
  }

  @Test
  @DisplayName("US-16 AC-3 a team of another event or a deleted team does not grant the list")
  void deletedOrForeignTeamDoesNotGrant() {
    event(EventStatus.Published);
    var deleted = team("Control room", Permission.HELP_REQUESTS_TRIAGE).asDeleted();
    ports.teams.put(deleted.teamId(), deleted);
    var elsewhere = new Team(UUID.randomUUID(), UUID.randomUUID(), "Control room",
        List.of(TeamPermission.of(Permission.REPORTS_REVIEW)), Set.of(), false, 1);
    ports.teams.put(elsewhere.teamId(), elsewhere);
    member(Role.RegularUser, Status.Active, deleted.teamId(), elsewhere.teamId());

    assertThatThrownBy(() -> service.list(user, eventId)).isInstanceOf(ForbiddenException.class);
  }

  @Test
  @DisplayName("US-16 AC-1 only an EventAdmin changes teams")
  void onlyAdminChangesTeams() {
    event(EventStatus.Published);
    var control = team("Control room", Permission.HELP_REQUESTS_TRIAGE);
    member(Role.RegularUser, Status.Active, control.teamId());

    assertThatThrownBy(() -> service.create(user, eventId, "Nursing")).isInstanceOf(ForbiddenException.class);
    assertThatThrownBy(() -> service.update(user, eventId, control.teamId(), "Room", List.of()))
        .isInstanceOf(ForbiddenException.class);
    assertThatThrownBy(() -> service.delete(user, eventId, control.teamId()))
        .isInstanceOf(ForbiddenException.class);

    assertThat(ports.published).isEmpty();
    assertThat(service.list(user, eventId)).containsExactly(control);
  }

  @Test
  @DisplayName("US-16 AC-1 an event with no team lists nothing for its EventAdmin")
  void emptyList() {
    event(EventStatus.Draft);
    member(Role.EventAdmin, Status.Active);

    assertThat(service.list(user, eventId)).isEmpty();
  }

  @Test
  @DisplayName("US-16 AC-1 the list is ordered by name ignoring case and holds only this event's live teams")
  void listIsOrderedAndScoped() {
    event(EventStatus.Published);
    member(Role.EventAdmin, Status.Active);
    team("security");
    team("Nursing");
    var deleted = team("Logistics").asDeleted();
    ports.teams.put(deleted.teamId(), deleted);
    var elsewhere = new Team(UUID.randomUUID(), UUID.randomUUID(), "Aaa", List.of(), Set.of(), false, 1);
    ports.teams.put(elsewhere.teamId(), elsewhere);

    assertThat(service.list(user, eventId)).extracting(Team::name).containsExactly("Nursing", "security");
  }

  @Test
  @DisplayName("US-16 AC-1 an unknown event, a non-member and a removed member get the same refusal, recorded")
  void unknownEventAndNonMembers() {
    var unknown = UUID.randomUUID();
    assertRefused(unknown, Refusal.NO_SUCH_EVENT);

    event(EventStatus.Published);
    assertRefused(eventId, Refusal.NOT_A_MEMBER);

    member(Role.RegularUser, Status.Removed);
    assertRefused(eventId, Refusal.NOT_A_MEMBER);
  }

  @Test
  @DisplayName("US-16 AC-1 a RegularUser of a draft event does not even know it has teams")
  void regularUserOfADraftEvent() {
    event(EventStatus.Draft);
    member(Role.RegularUser, Status.Active);

    assertRefused(eventId, Refusal.NOT_VISIBLE);
  }

  private void assertRefused(UUID event, Refusal refusal) {
    ports.refusals.clear();
    assertThatThrownBy(() -> service.list(user, event)).isInstanceOf(EventNotFoundException.class);
    assertThatThrownBy(() -> service.create(user, event, "Nursing")).isInstanceOf(EventNotFoundException.class);
    assertThatThrownBy(() -> service.update(user, event, UUID.randomUUID(), "Nursing", List.of()))
        .isInstanceOf(EventNotFoundException.class);
    assertThatThrownBy(() -> service.delete(user, event, UUID.randomUUID()))
        .isInstanceOf(EventNotFoundException.class);

    assertThat(ports.refusals).hasSize(4)
        .allMatch(refused -> refused.refusal() == refusal && refused.at().equals(NOW)
            && refused.userId().equals(user) && refused.eventId().equals(event));
    assertThat(ports.published).isEmpty();
  }

  @Test
  @DisplayName("US-17 AC-1 an EventAdmin replaces the whole permission set, published with the next version")
  void replacePermissions() {
    event(EventStatus.Published);
    member(Role.EventAdmin, Status.Active);
    var created = service.create(user, eventId, "Control room");
    ports.published.clear();

    var given = service.replacePermissions(user, eventId, created.teamId(),
        List.of(TeamPermission.of(Permission.MAP_EDIT),
            new TeamPermission(Permission.REPORTS_REVIEW, Set.of(ReportType.Hazard))));

    assertThat(given.version()).isEqualTo(2);
    assertThat(given.permissions()).extracting(TeamPermission::permission)
        .containsExactly(Permission.REPORTS_REVIEW, Permission.MAP_EDIT);
    assertThat(service.list(user, eventId)).containsExactly(given);
    assertThat(ports.published).hasSize(1);
    assertThat(ports.published.getFirst().payload()).containsEntry("version", 2L)
        .containsEntry("permissions", List.of(
            Map.of("permission", "Reports.Review", "reportTypes", List.of("Hazard")),
            Map.of("permission", "Map.Edit", "reportTypes", List.of())));
  }

  @Test
  @DisplayName("US-17 AC-4 a removed permission stops granting at once, and the change is recorded with who and "
      + "when")
  void removedPermissionStopsGranting() {
    event(EventStatus.Published);
    member(Role.EventAdmin, Status.Active);
    var created = service.create(user, eventId, "Control room");
    service.replacePermissions(user, eventId, created.teamId(),
        List.of(TeamPermission.of(Permission.HELP_REQUESTS_TRIAGE)));
    var reader = UUID.randomUUID();
    ports.upsertIfNewer(new Membership(eventId, reader, Role.RegularUser, Status.Active,
        Set.of(created.teamId()), 1));
    assertThat(service.list(reader, eventId)).hasSize(1);
    ports.published.clear();

    var taken = service.replacePermissions(user, eventId, created.teamId(), List.of());

    assertThat(taken.permissions()).isEmpty();
    assertThat(taken.version()).isEqualTo(3);
    assertThatThrownBy(() -> service.list(reader, eventId)).isInstanceOf(ForbiddenException.class);
    assertThat(ports.published).hasSize(1);
    assertThat(ports.published.getFirst().payload()).containsEntry("permissions", List.of());
    assertThat(ports.published.getFirst().actorUserId()).isEqualTo(user.toString());
    assertThat(ports.audited).last().isEqualTo(
        new InMemoryPorts.Audited(user, eventId, created.teamId(), NOW, List.of()));
    assertThat(ports.writes).contains(new Write(created.teamId(), user, NOW, 3));
  }

  @Test
  @DisplayName("US-17 AC-1 the same set sent twice is recorded twice, with a new version each time")
  void sameSetTwice() {
    event(EventStatus.Published);
    member(Role.EventAdmin, Status.Active);
    var created = service.create(user, eventId, "Control room");
    var set = List.of(TeamPermission.of(Permission.ENTRANCES_UPDATE));
    ports.published.clear();

    var first = service.replacePermissions(user, eventId, created.teamId(), set);
    var second = service.replacePermissions(user, eventId, created.teamId(), set);

    assertThat(first.version()).isEqualTo(2);
    assertThat(second.version()).isEqualTo(3);
    assertThat(ports.published).hasSize(2);
    assertThat(ports.audited).hasSize(2);
  }

  @Test
  @DisplayName("US-17 AC-1 a repeated permission is refused, and nothing is published or recorded")
  void repeatedPermissionIsRefused() {
    event(EventStatus.Published);
    member(Role.EventAdmin, Status.Active);
    var created = service.create(user, eventId, "Control room");
    ports.published.clear();

    assertThatThrownBy(() -> service.replacePermissions(user, eventId, created.teamId(),
        List.of(TeamPermission.of(Permission.MAP_EDIT), TeamPermission.of(Permission.MAP_EDIT))))
        .isInstanceOf(InvalidTeamException.class);

    assertThat(ports.published).isEmpty();
    assertThat(ports.audited).isEmpty();
    assertThat(service.list(user, eventId)).containsExactly(created);
  }

  @Test
  @DisplayName("US-17 AC-1 only an EventAdmin of an open event replaces permissions, and never on another event")
  void onlyAnAdminOfAnOpenEventReplacesPermissions() {
    event(EventStatus.Published);
    member(Role.EventAdmin, Status.Active);
    var created = service.create(user, eventId, "Control room");
    var set = List.of(TeamPermission.of(Permission.MAP_EDIT));
    ports.published.clear();

    member(Role.RegularUser, Status.Active, created.teamId());
    assertThatThrownBy(() -> service.replacePermissions(user, eventId, created.teamId(), set))
        .isInstanceOf(ForbiddenException.class);

    member(Role.EventAdmin, Status.Active);
    event(EventStatus.Closed);
    assertThatThrownBy(() -> service.replacePermissions(user, eventId, created.teamId(), set))
        .isInstanceOf(EventNotOpenException.class);

    event(EventStatus.Published);
    assertThatThrownBy(() -> service.replacePermissions(user, eventId, UUID.randomUUID(), set))
        .isInstanceOf(TeamNotFoundException.class);
    assertThatThrownBy(() -> service.replacePermissions(user, UUID.randomUUID(), created.teamId(), set))
        .isInstanceOf(EventNotFoundException.class);

    assertThat(ports.published).isEmpty();
    assertThat(ports.audited).isEmpty();
  }

  @Test
  @DisplayName("US-17 AC-1 a team deleted between the read and the write is not found, and nothing is recorded")
  void permissionsOfAVanishedTeam() {
    event(EventStatus.Published);
    member(Role.EventAdmin, Status.Active);
    var created = service.create(user, eventId, "Control room");
    ports.published.clear();
    ports.teamVanishesOnWrite = true;

    assertThatThrownBy(() -> service.replacePermissions(user, eventId, created.teamId(),
        List.of(TeamPermission.of(Permission.MAP_EDIT)))).isInstanceOf(TeamNotFoundException.class);

    assertThat(ports.published).isEmpty();
    assertThat(ports.audited).isEmpty();
  }
}
