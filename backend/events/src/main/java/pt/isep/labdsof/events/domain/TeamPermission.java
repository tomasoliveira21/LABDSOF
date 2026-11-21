package pt.isep.labdsof.events.domain;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * One permission of a team, with the report types it applies to. Only Reports.Review carries any (features.md
 * §4.2, mirrored by team_permission's check); the types are kept in declaration order, so TeamChanged is stable.
 */
public record TeamPermission(Permission permission, Set<ReportType> reportTypes) {

  public TeamPermission {
    Objects.requireNonNull(permission, "permission");
    reportTypes = immutable(reportTypes);
    if (!permission.carriesReportTypes() && !reportTypes.isEmpty()) {
      throw new InvalidTeamException("only Reports.Review carries report types");
    }
  }

  public static TeamPermission of(Permission permission) {
    return new TeamPermission(permission, Set.of());
  }

  private static Set<ReportType> immutable(Set<ReportType> types) {
    Objects.requireNonNull(types, "reportTypes");
    return types.isEmpty() ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(types));
  }
}
