package pt.isep.labdsof.reports.domain;

/** A normalised point on one version of the event's venue map (API outline §4.1). */
public record Location(int mapVersion, double x, double y) {

  public Location {
    if (mapVersion < 1) {
      throw new IllegalArgumentException("mapVersion must be at least 1");
    }
    if (!(x >= 0 && x <= 1) || !(y >= 0 && y <= 1)) {
      throw new IllegalArgumentException("x and y must be between 0 and 1");
    }
  }
}
