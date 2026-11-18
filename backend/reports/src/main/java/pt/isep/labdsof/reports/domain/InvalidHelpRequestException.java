package pt.isep.labdsof.reports.domain;

/** A help request or location the caller sent that the domain refuses: the API answers 400 with this message. */
public class InvalidHelpRequestException extends IllegalArgumentException {
  public InvalidHelpRequestException(String message) {
    super(message);
  }
}
