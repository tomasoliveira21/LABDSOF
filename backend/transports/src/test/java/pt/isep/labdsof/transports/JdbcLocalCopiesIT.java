package pt.isep.labdsof.transports;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pt.isep.labdsof.transports.application.LocalCopies;
import pt.isep.labdsof.transports.application.ProcessedMessages;
import pt.isep.labdsof.transports.domain.EventCopy;
import pt.isep.labdsof.transports.domain.EventStatus;
import pt.isep.labdsof.transports.domain.Membership;
import pt.isep.labdsof.transports.domain.Membership.Role;
import pt.isep.labdsof.transports.domain.Membership.Status;
import pt.isep.labdsof.transports.domain.Permission;
import pt.isep.labdsof.transports.domain.Team;

/** The SQL of the copies keeps the rules of the in-memory ports the use case is unit-tested with. */
class JdbcLocalCopiesIT extends IntegrationTest {

  @Autowired LocalCopies copies;
  @Autowired ProcessedMessages processed;

  @Test
  @DisplayName("193 AC-5 a messageId is processed once")
  void aMessageIdIsProcessedOnce() {
    var messageId = UUID.randomUUID();
    assertThat(processed.markProcessed(messageId)).isTrue();
    assertThat(processed.markProcessed(messageId)).isFalse();
  }

  @Test
  @DisplayName("193 AC-5 the membership copy is stored with its team ids and replaced only by a newer version")
  void membershipIsUpsertedByVersion() {
    var event = UUID.randomUUID();
    var user = UUID.randomUUID();
    var teams = Set.of(UUID.randomUUID(), UUID.randomUUID());
    var first = new Membership(event, user, Role.RegularUser, Status.Active, teams, 2);

    assertThat(copies.membership(event, user)).isEmpty();
    assertThat(copies.upsertIfNewer(first)).isTrue();
    assertThat(copies.membership(event, user)).contains(first);
    assertThat(copies.upsertIfNewer(new Membership(event, user, Role.EventAdmin, Status.Active, Set.of(), 2)))
        .isFalse();
    assertThat(copies.upsertIfNewer(new Membership(event, user, Role.EventAdmin, Status.Active, Set.of(), 1)))
        .isFalse();
    assertThat(copies.membership(event, user)).contains(first);

    var removed = new Membership(event, user, Role.EventAdmin, Status.Removed, Set.of(), 3);
    assertThat(copies.upsertIfNewer(removed)).isTrue();
    assertThat(copies.membership(event, user)).contains(removed);
    assertThat(copies.membership(UUID.randomUUID(), user)).isEmpty();
  }

  @Test
  @DisplayName("193 AC-5 the event copy is stored and replaced only by a newer version")
  void eventIsUpsertedByVersion() {
    var event = UUID.randomUUID();
    var published = new EventCopy(event, EventStatus.Published, 2);

    assertThat(copies.event(event)).isEmpty();
    assertThat(copies.upsertIfNewer(published)).isTrue();
    assertThat(copies.upsertIfNewer(new EventCopy(event, EventStatus.Draft, 2))).isFalse();
    assertThat(copies.upsertIfNewer(new EventCopy(event, EventStatus.Draft, 1))).isFalse();
    assertThat(copies.event(event)).contains(published);

    var suspended = new EventCopy(event, EventStatus.Suspended, 3);
    assertThat(copies.upsertIfNewer(suspended)).isTrue();
    assertThat(copies.event(event)).contains(suspended);
  }

  @Test
  @DisplayName("193 AC-5 the team copy keeps every permission and is replaced only by a newer version of the same "
      + "event")
  void teamIsUpsertedByVersionWithinItsEvent() {
    var teamId = UUID.randomUUID();
    var event = UUID.randomUUID();
    var all = new Team(teamId, event, EnumSet.allOf(Permission.class), false, 2);

    assertThat(copies.team(teamId)).isEmpty();
    assertThat(copies.upsertIfNewer(all)).isTrue();
    assertThat(copies.team(teamId)).contains(all);
    assertThat(copies.upsertIfNewer(new Team(teamId, event, Set.of(), false, 2))).isFalse();
    assertThat(copies.upsertIfNewer(new Team(teamId, event, Set.of(), false, 1))).isFalse();
    assertThat(copies.upsertIfNewer(new Team(teamId, UUID.randomUUID(), Set.of(), false, 9))).isFalse();
    assertThat(copies.team(teamId)).contains(all);

    var deleted = new Team(teamId, event, Set.of(), true, 3);
    assertThat(copies.upsertIfNewer(deleted)).isTrue();
    assertThat(copies.team(teamId)).contains(deleted);
  }
}
