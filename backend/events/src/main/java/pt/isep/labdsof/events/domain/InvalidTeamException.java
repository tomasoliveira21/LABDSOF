package pt.isep.labdsof.events.domain;

/** A team the caller described wrongly (name out of bounds or blank, a repeated category): 400. */
public class InvalidTeamException extends RuntimeException {

  public InvalidTeamException(String reason) {
    super(reason);
  }
}
