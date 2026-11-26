package pt.isep.labdsof.events.domain;

/**
 * A position on one map version's image: normalised coordinates, both between 0 and 1 (ADR-007 decision 1), so a
 * point does not depend on the screen, the device or the image resolution. The bounds are
 * {@code point_of_interest_x} and {@code point_of_interest_y}. US-19 only ever builds one from a stored row, so a
 * value outside them is a broken row, not a caller's mistake; the request bodies that carry a position arrive with
 * US-20, which validates them before they get here.
 */
public record MapPosition(double x, double y) {

  public MapPosition {
    check(x, "x");
    check(y, "y");
  }

  private static void check(double value, String name) {
    if (!(value >= 0 && value <= 1)) { // also refuses NaN
      throw new IllegalArgumentException(name + " must be between 0 and 1");
    }
  }
}
