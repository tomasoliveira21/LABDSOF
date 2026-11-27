package pt.isep.labdsof.transports.domain;

/**
 * The venue, in geographic coordinates (spec US-41 D8): the point the import asks around, the centre of the map and
 * where distances are measured from.
 */
public record Venue(double longitude, double latitude) {

  /** Mean Earth radius (IUGG), in metres. */
  private static final double EARTH_RADIUS = 6_371_008.8;

  public Venue {
    if (!(Math.abs(longitude) <= 180) || !(Math.abs(latitude) <= 90)) {
      throw new IllegalArgumentException("not a longitude and a latitude: " + longitude + ", " + latitude);
    }
  }

  /** {@code farol.transport.source.coords}: latitude first, as the NGSI geo-query writes it ({@code "41.1724,-8.6841"}). */
  public static Venue parse(String coords) {
    var parts = coords == null ? new String[0] : coords.split(",", -1);
    if (parts.length != 2) {
      throw new IllegalArgumentException("coords must be \"latitude,longitude\": " + coords);
    }
    try {
      return new Venue(Double.parseDouble(parts[1].strip()), Double.parseDouble(parts[0].strip()));
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("coords must be \"latitude,longitude\": " + coords, e);
    }
  }

  /** The great-circle distance from the venue (haversine), rounded to the metre (D6). */
  public long distanceMetres(double longitude, double latitude) {
    var phi1 = Math.toRadians(this.latitude);
    var phi2 = Math.toRadians(latitude);
    var dPhi = phi2 - phi1;
    var dLambda = Math.toRadians(longitude - this.longitude);
    var a = Math.pow(Math.sin(dPhi / 2), 2) + Math.cos(phi1) * Math.cos(phi2) * Math.pow(Math.sin(dLambda / 2), 2);
    return Math.round(2 * EARTH_RADIUS * Math.asin(Math.min(1, Math.sqrt(a))));
  }
}
