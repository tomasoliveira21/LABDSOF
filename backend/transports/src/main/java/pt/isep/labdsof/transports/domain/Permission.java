package pt.isep.labdsof.transports.domain;

import java.util.Arrays;

/** The fixed list of team permissions (features.md 4.2), with the names they have on the wire. */
public enum Permission {
  HELP_REQUESTS_TRIAGE("HelpRequests.Triage"),
  HELP_REQUESTS_RESPOND("HelpRequests.Respond"),
  REPORTS_REVIEW("Reports.Review"),
  REPORTS_RESPOND("Reports.Respond"),
  ANNOUNCEMENTS_PUBLISH("Announcements.Publish"),
  MAP_EDIT("Map.Edit"),
  TRANSPORT_EDIT("Transport.Edit"),
  ENTRANCES_UPDATE("Entrances.Update");

  private final String wireName;

  Permission(String wireName) {
    this.wireName = wireName;
  }

  public String wireName() {
    return wireName;
  }

  public static Permission fromWireName(String name) {
    return Arrays.stream(values()).filter(p -> p.wireName.equals(name)).findFirst()
        .orElseThrow(() -> new IllegalArgumentException("unknown permission: " + name));
  }
}
