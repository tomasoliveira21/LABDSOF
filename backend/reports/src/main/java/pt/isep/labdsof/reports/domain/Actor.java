package pt.isep.labdsof.reports.domain;

/**
 * The member who changes a request: the token's sub, and the token's name copied into the history so the entry says
 * who acted under the name they had (spec US-28 D5); the name may be missing.
 */
public record Actor(String userId, String name) {
  public Actor {
    if (userId == null || userId.isBlank()) {
      throw new IllegalArgumentException("userId is required");
    }
  }
}
