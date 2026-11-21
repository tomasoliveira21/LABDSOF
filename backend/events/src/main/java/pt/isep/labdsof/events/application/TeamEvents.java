package pt.isep.labdsof.events.application;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import pt.isep.labdsof.events.domain.HelpCategory;
import pt.isep.labdsof.events.domain.ReportType;
import pt.isep.labdsof.events.domain.Team;
import pt.isep.labdsof.events.domain.TeamPermission;

/**
 * TeamChanged, as contracts/events/team.changed.schema.json describes it (API outline §7.4): the full state of
 * the team with its version, published whenever it is created, renamed, given its categories or permissions, or
 * deleted. Every permission carries reportTypes, empty unless it is Reports.Review.
 */
public final class TeamEvents {

  static final String ROUTING_KEY = "team.changed";
  static final String TYPE = "TeamChanged";
  static final int SCHEMA_VERSION = 1;

  private TeamEvents() {}

  public static OutgoingEvent changed(Team team, UUID actor, Instant at) {
    return new OutgoingEvent(UUID.randomUUID(), OutgoingEvent.EXCHANGE, ROUTING_KEY, TYPE, SCHEMA_VERSION, at,
        team.eventId(), actor.toString(), payload(team));
  }

  private static Map<String, Object> payload(Team team) {
    var payload = new LinkedHashMap<String, Object>();
    payload.put("teamId", team.teamId().toString());
    payload.put("name", team.name());
    payload.put("permissions", team.permissions().stream().map(TeamEvents::permission).toList());
    payload.put("handledCategories", team.handledCategories().stream().map(HelpCategory::name).toList());
    payload.put("deleted", team.deleted());
    payload.put("version", team.version());
    return payload;
  }

  private static Map<String, Object> permission(TeamPermission permission) {
    var written = new LinkedHashMap<String, Object>();
    written.put("permission", permission.permission().code());
    written.put("reportTypes", permission.reportTypes().stream().map(ReportType::name).toList());
    return written;
  }
}
