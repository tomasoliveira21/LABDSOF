package pt.isep.labdsof.events.domain;

import java.util.Arrays;

/**
 * The fixed list of team permissions (features.md §4.2). The codes carry a dot, so the constants cannot be named
 * after them; {@link #code()} is the value the contract, the database and TeamChanged use.
 */
public enum Permission {
  HELP_REQUESTS_TRIAGE("HelpRequests.Triage"),
  HELP_REQUESTS_RESPOND("HelpRequests.Respond"),
  REPORTS_REVIEW("Reports.Review"),
  REPORTS_RESPOND("Reports.Respond"),
  ANNOUNCEMENTS_PUBLISH("Announcements.Publish"),
  MAP_EDIT("Map.Edit"),
  TRANSPORT_EDIT("Transport.Edit"),
  ENTRANCES_UPDATE("Entrances.Update");

  private final String code;

  Permission(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }

  /** Only Reports.Review carries report types (features.md §4.2, team_permission's check). */
  public boolean carriesReportTypes() {
    return this == REPORTS_REVIEW;
  }

  /** The permissions that let a member read the event's teams (API outline §3). */
  public boolean readsTeams() {
    return this == HELP_REQUESTS_TRIAGE || this == REPORTS_REVIEW;
  }

  public static Permission ofCode(String code) {
    return Arrays.stream(values()).filter(p -> p.code.equals(code)).findFirst()
        .orElseThrow(() -> new IllegalArgumentException("unknown permission"));
  }
}
