package pt.isep.labdsof.badges.domain;

import java.util.UUID;

public record BadgeProgress(Track track, UUID eventId, int qualifyingCount, Tier tier) {

  public enum Track { ReportHistory, EventReporter, EventAttendance }

  public enum Tier { Wood, Bronze, Silver, Gold, Platinum, Diamond }
}
