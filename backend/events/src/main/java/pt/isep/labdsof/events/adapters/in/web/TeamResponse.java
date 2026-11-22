package pt.isep.labdsof.events.adapters.in.web;

import java.util.List;
import pt.isep.labdsof.events.adapters.in.web.generated.model.HelpCategory;
import pt.isep.labdsof.events.adapters.in.web.generated.model.Permission;
import pt.isep.labdsof.events.adapters.in.web.generated.model.ReportType;
import pt.isep.labdsof.events.adapters.in.web.generated.model.Team;
import pt.isep.labdsof.events.adapters.in.web.generated.model.TeamPermission;

/** Maps the domain's team to the contract's Team, and the contract's categories back. No business rule here. */
final class TeamResponse {

  private TeamResponse() {}

  static Team of(pt.isep.labdsof.events.domain.Team team) {
    return new Team(team.teamId(), team.name(),
        team.permissions().stream().map(TeamResponse::permission).toList(),
        team.handledCategories().stream().map(category -> HelpCategory.fromValue(category.name())).toList());
  }

  /**
   * The order the caller sent is kept, so a repeated category is still a repeated category, and a null item is
   * passed on for the domain to refuse as a bad request (US-16 D7); the contract's array does not forbid it.
   */
  static List<pt.isep.labdsof.events.domain.HelpCategory> categories(List<HelpCategory> sent) {
    return sent.stream().map(category -> category == null ? null
        : pt.isep.labdsof.events.domain.HelpCategory.valueOf(category.getValue())).toList();
  }

  /**
   * The permissions of a PUT body (US-17). A null item is passed on for the domain to refuse as a bad request,
   * and so is a repeated one; each permission's report types become its set there too.
   */
  static List<pt.isep.labdsof.events.domain.TeamPermission> permissions(List<TeamPermission> sent) {
    return sent.stream().map(TeamResponse::permission).toList();
  }

  private static pt.isep.labdsof.events.domain.TeamPermission permission(TeamPermission sent) {
    if (sent == null) {
      return null;
    }
    return new pt.isep.labdsof.events.domain.TeamPermission(
        pt.isep.labdsof.events.domain.Permission.ofCode(sent.permission().getValue()),
        pt.isep.labdsof.events.domain.TeamPermission.reportTypesOf(sent.reportTypes().stream()
            .map(type -> type == null ? null : pt.isep.labdsof.events.domain.ReportType.valueOf(type.getValue()))
            .toList()));
  }

  private static TeamPermission permission(pt.isep.labdsof.events.domain.TeamPermission permission) {
    return new TeamPermission(Permission.fromValue(permission.permission().code()),
        permission.reportTypes().stream().map(type -> ReportType.fromValue(type.name())).toList());
  }
}
