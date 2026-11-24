package pt.isep.labdsof.reports.domain;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Local copy of an event's team and its permissions, from TeamChanged (API outline §7.4). */
public record Team(UUID teamId, UUID eventId, String name, List<TeamPermission> permissions,
    Set<Category> handledCategories, boolean deleted, long version) {

  /** A permission, with the report types it applies to (only for Reports.Review). */
  public record TeamPermission(Permission permission, Set<String> reportTypes) {
    public TeamPermission {
      Objects.requireNonNull(permission, "permission");
      reportTypes = Set.copyOf(reportTypes);
    }
  }

  public Team {
    Objects.requireNonNull(teamId, "teamId");
    Objects.requireNonNull(eventId, "eventId");
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name is required");
    }
    if (version < 1) {
      throw new IllegalArgumentException("version must be at least 1");
    }
    permissions = List.copyOf(permissions);
    handledCategories = Set.copyOf(handledCategories);
  }

  /** A deleted team grants nothing. */
  public boolean grants(Permission permission) {
    return !deleted && permissions.stream().anyMatch(p -> p.permission() == permission);
  }

  /** Whether this team can take requests of {@code category}: it handles it and responds (spec US-34 AC-2, D1). */
  public boolean handles(Category category) {
    return handledCategories.contains(category) && grants(Permission.HELP_REQUESTS_RESPOND);
  }
}
