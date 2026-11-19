package pt.isep.labdsof.reports.application;

/** Not found, or not visible to the caller: both answer 404 so existence is never revealed (ADR-006). */
public class NotFoundException extends RuntimeException {

  public NotFoundException() {
    super("not found");
  }
}
