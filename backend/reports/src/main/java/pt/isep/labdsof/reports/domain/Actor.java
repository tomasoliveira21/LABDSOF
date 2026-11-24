package pt.isep.labdsof.reports.domain;

/**
 * The member who changes a request: the token's sub, and the token's name copied into the history so the entry says
 * who acted under the name they had (spec US-28 D5). A missing or blank name is null; a longer one is cut at
 * {@link #MAX_NAME} characters (code points), so a profile name cannot grow every history row (G8 review).
 */
public record Actor(String userId, String name) {

  public static final int MAX_NAME = 200;

  public Actor {
    if (userId == null || userId.isBlank()) {
      throw new IllegalArgumentException("userId is required");
    }
    if (name == null || name.isBlank()) {
      name = null;
    } else if (name.codePointCount(0, name.length()) > MAX_NAME) {
      name = name.substring(0, name.offsetByCodePoints(0, MAX_NAME));
    }
  }
}
