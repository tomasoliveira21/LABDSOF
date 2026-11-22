package pt.isep.labdsof.transports;

import static org.assertj.core.api.Assertions.assertThat;
import static pt.isep.labdsof.transports.Fixtures.eventChanged;
import static pt.isep.labdsof.transports.Fixtures.eventPayload;
import static pt.isep.labdsof.transports.Fixtures.membershipChanged;
import static pt.isep.labdsof.transports.Fixtures.membershipPayload;
import static pt.isep.labdsof.transports.Fixtures.teamChanged;
import static pt.isep.labdsof.transports.Fixtures.teamPayload;

import com.networknt.schema.InputFormat;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * SDD rule R3: the messages the consumer tests publish are the messages the producers publish, as
 * contracts/events/consumed describes them. Needs no Spring context; an *IT because it reads the contract files.
 */
class ConsumedContractIT {

  private static final Path CONSUMED = Path.of("contracts", "events", "consumed");
  private static final String MEMBERSHIP = "membership.changed.schema.json";
  private static final String EVENT = "event.changed.schema.json";
  private static final String TEAM = "team.changed.schema.json";
  private static final UUID EVENT_ID = UUID.randomUUID();

  static JsonSchema schema(String file) {
    var config = SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build();
    return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
        .getSchema(SchemaLocation.of(CONSUMED.resolve(file).toAbsolutePath().toUri().toString()), config);
  }

  private static Stream<Arguments> fixtures() {
    var user = UUID.randomUUID();
    var team = UUID.randomUUID();
    return Stream.of(
        Arguments.of("membership, active, with teams", MEMBERSHIP, membershipChanged(UUID.randomUUID(), EVENT_ID,
            membershipPayload(user, "EventAdmin", "Active", 2, UUID.randomUUID(), UUID.randomUUID()))),
        Arguments.of("membership, removed", MEMBERSHIP, membershipChanged(UUID.randomUUID(), EVENT_ID,
            membershipPayload(user, "RegularUser", "Removed", 3))),
        Arguments.of("membership, without status", MEMBERSHIP, membershipChanged(UUID.randomUUID(), EVENT_ID,
            membershipPayload(user, "RegularUser", null, 1))),
        Arguments.of("event, Draft", EVENT, eventChanged(UUID.randomUUID(), EVENT_ID, eventPayload("Draft", 1))),
        Arguments.of("event, Published", EVENT, eventChanged(UUID.randomUUID(), EVENT_ID,
            eventPayload("Published", 2))),
        Arguments.of("event, Suspended", EVENT, eventChanged(UUID.randomUUID(), EVENT_ID,
            eventPayload("Suspended", 3))),
        Arguments.of("event, Closed", EVENT, eventChanged(UUID.randomUUID(), EVENT_ID, eventPayload("Closed", 4))),
        Arguments.of("team, no permission", TEAM, teamChanged(UUID.randomUUID(), EVENT_ID,
            teamPayload(team, false, 1))),
        Arguments.of("team, Transport.Edit and Map.Edit", TEAM, teamChanged(UUID.randomUUID(), EVENT_ID,
            teamPayload(team, false, 2, "Transport.Edit", "Map.Edit"))),
        Arguments.of("team, deleted", TEAM, teamChanged(UUID.randomUUID(), EVENT_ID, teamPayload(team, true, 3))));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("fixtures")
  @DisplayName("193 AC-5 every fixture the consumer tests publish matches the consumed schemas")
  void fixturesMatchTheConsumedSchemas(String name, String schemaFile, String message) {
    assertThat(schema(schemaFile).validate(message, InputFormat.JSON)).isEmpty();
  }

  @Test
  @DisplayName("193 AC-5 the consumed schemas refuse what the consumer dead-letters (negative control)")
  void schemasRefuseInvalidMessages() {
    assertThat(schema(MEMBERSHIP).validate(membershipChanged(UUID.randomUUID(), EVENT_ID,
        membershipPayload(UUID.randomUUID(), "Owner", "Active", 1)), InputFormat.JSON)).isNotEmpty();
    assertThat(schema(EVENT).validate(eventChanged(UUID.randomUUID(), EVENT_ID, eventPayload("Open", 1)),
        InputFormat.JSON)).isNotEmpty();
    assertThat(schema(TEAM).validate(teamChanged(UUID.randomUUID(), EVENT_ID,
        teamPayload(UUID.randomUUID(), false, 1, "Bus.Drive")), InputFormat.JSON)).isNotEmpty();
  }
}
