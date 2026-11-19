package pt.isep.labdsof.reports.application;

/** A member of the event without the permission the use case needs: 403 (API outline §1.7). */
public class ForbiddenException extends RuntimeException {

  public ForbiddenException() {
    super("forbidden");
  }
}
