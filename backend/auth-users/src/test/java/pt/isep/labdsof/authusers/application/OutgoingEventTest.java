package pt.isep.labdsof.authusers.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pt.isep.labdsof.authusers.domain.GlobalRole;
import pt.isep.labdsof.authusers.domain.UserAccount;

class OutgoingEventTest {

  private static final UUID MESSAGE = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
  private static final UUID USER = UUID.fromString("11111111-2222-3333-4444-555555555555");
  private static final Instant AT = Instant.parse("2026-11-17T10:00:00.123456Z");

  private final UserAccount account = UserAccount.provision(USER, "ana@farol.test", "Ana", AT);

  @Test
  @DisplayName("191 AC-5 the envelope has the fields of API outline §7.1, producer auth-users and no eventId")
  void envelopeHasTheFieldsOfSection71() {
    var envelope = OutgoingEvent.userProfileChanged(MESSAGE, account, USER.toString()).envelope();

    assertThat(envelope.keySet()).containsExactly("messageId", "type", "schemaVersion", "occurredAt", "producer",
        "eventId", "actorUserId", "payload");
    assertThat(envelope).containsEntry("messageId", MESSAGE.toString())
        .containsEntry("type", "UserProfileChanged")
        .containsEntry("schemaVersion", 1)
        .containsEntry("occurredAt", "2026-11-17T10:00:00.123456Z")
        .containsEntry("producer", "auth-users")
        .containsEntry("eventId", null)
        .containsEntry("actorUserId", USER.toString());
    assertThat(envelope.get("payload")).isEqualTo(
        Map.of("userId", USER.toString(), "displayName", "Ana", "version", 1L));
  }

  @Test
  @DisplayName("191 AC-5 the envelope carries the eventId when the event belongs to an event")
  void envelopeCarriesEventIdWhenPresent() {
    var event = new OutgoingEvent(MESSAGE, OutgoingEvent.EXCHANGE, "x.y", "XY", 1, AT, USER, "", Map.of());

    assertThat(event.envelope()).containsEntry("eventId", USER.toString());
  }

  @Test
  @DisplayName("191 AC-5 UserProfileChanged goes to farol.auth-users as user-profile.changed with userId, "
      + "displayName and version")
  void userProfileChangedGoesToItsExchange() {
    var event = OutgoingEvent.userProfileChanged(MESSAGE, account, USER.toString());

    assertThat(event.exchange()).isEqualTo("farol.auth-users");
    assertThat(event.routingKey()).isEqualTo("user-profile.changed");
    assertThat(event.type()).isEqualTo("UserProfileChanged");
    assertThat(event.schemaVersion()).isEqualTo(1);
    assertThat(event.occurredAt()).isEqualTo(account.updatedAt());
    assertThat(event.eventId()).isNull();
    assertThat(event.actorUserId()).isEqualTo(USER.toString());
    assertThat(event.payload()).containsOnlyKeys("userId", "displayName", "version")
        .containsEntry("userId", USER.toString())
        .containsEntry("displayName", "Ana")
        .containsEntry("version", 1L);
  }

  @Test
  @DisplayName("191 AC-5 an event without message id or actor is refused")
  void eventNeedsMessageIdAndActor() {
    assertThatThrownBy(() -> OutgoingEvent.userProfileChanged(null, account, USER.toString()))
        .isInstanceOf(NullPointerException.class).hasMessage("messageId");
    assertThatThrownBy(() -> OutgoingEvent.userProfileChanged(MESSAGE, account, null))
        .isInstanceOf(NullPointerException.class).hasMessage("actorUserId");
    assertThat(OutgoingEvent.userProfileChanged(MESSAGE, account, "").actorUserId()).isEmpty();
  }

  @Test
  @DisplayName("191 AC-6 GlobalRoleChanged goes to farol.auth-users as global-role.changed with userId, role, "
      + "granted and version")
  void globalRoleChangedGoesToItsExchange() {
    var event = OutgoingEvent.globalRoleChanged(MESSAGE, USER, GlobalRole.FarolAdmin, true, 3, AT, "");

    assertThat(event.exchange()).isEqualTo("farol.auth-users");
    assertThat(event.routingKey()).isEqualTo("global-role.changed");
    assertThat(event.type()).isEqualTo("GlobalRoleChanged");
    assertThat(event.schemaVersion()).isEqualTo(1);
    assertThat(event.occurredAt()).isEqualTo(AT);
    assertThat(event.eventId()).isNull();
    assertThat(event.actorUserId()).isEmpty();
    assertThat(event.payload()).containsOnlyKeys("userId", "role", "granted", "version")
        .containsEntry("userId", USER.toString())
        .containsEntry("role", "FarolAdmin")
        .containsEntry("granted", true)
        .containsEntry("version", 3L);
    assertThat(event.envelope()).containsEntry("type", "GlobalRoleChanged").containsEntry("actorUserId", "");
    assertThat(List.copyOf(event.envelope().keySet())).hasSize(8);
  }
}
