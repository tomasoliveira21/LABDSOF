package pt.isep.labdsof.events.application;

/**
 * No live team with that id in the caller's event: 404. A team of another event looks the same, so the answer
 * never says whether the id exists elsewhere (US-14).
 */
public class TeamNotFoundException extends RuntimeException {

  public TeamNotFoundException() {
    super("team not found");
  }
}
