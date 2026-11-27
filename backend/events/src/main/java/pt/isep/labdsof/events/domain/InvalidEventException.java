package pt.isep.labdsof.events.domain;

/** An event the caller described wrongly (a name or an organiser out of bounds or blank, a long description): 400. */
public class InvalidEventException extends RuntimeException {

  public InvalidEventException(String reason) {
    super(reason);
  }
}
