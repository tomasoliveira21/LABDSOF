package pt.isep.labdsof.events.application;

/** Another live team of the event already has this name, ignoring case (US-16 AC-2, D3): 409. */
public class TeamNameTakenException extends RuntimeException {

  public TeamNameTakenException() {
    super("team name already taken");
  }
}
