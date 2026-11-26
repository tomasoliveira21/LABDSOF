package pt.isep.labdsof.events.adapters.out.audit;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pt.isep.labdsof.events.application.TeamAudit;
import pt.isep.labdsof.events.domain.Permission;
import pt.isep.labdsof.events.domain.ReportType;
import pt.isep.labdsof.events.domain.TeamPermission;

/**
 * Logs a permission change at INFO with pseudonymous values only (US-17 AC-4, spec D6, M-10). The request's
 * correlation id is already in the MDC, so the line carries it.
 */
@Component
class LoggingTeamAudit implements TeamAudit {

  private static final Logger log = LoggerFactory.getLogger(LoggingTeamAudit.class);

  @Override
  public void permissionsReplaced(UUID userId, UUID eventId, UUID teamId, Instant at,
      List<TeamPermission> permissions) {
    log.atInfo()
        .addKeyValue("userId", userId.toString())
        .addKeyValue("eventId", eventId.toString())
        .addKeyValue("teamId", teamId.toString())
        .addKeyValue("at", at.toString())
        .addKeyValue("permissions", permissions.stream().map(LoggingTeamAudit::describe).toList().toString())
        .log("Replaced a team's permissions (US-17)");
  }

  /**
   * {@code Reports.Review[Hazard, NoWater]} for a reviewing team, {@code Map.Edit} for the others. The team
   * sorts its permissions, so the line reads the same whatever order the caller sent.
   */
  private static String describe(TeamPermission permission) {
    var code = permission.permission().code();
    return permission.reportTypes().isEmpty() ? code
        : code + permission.reportTypes().stream().map(ReportType::name).toList();
  }
}
