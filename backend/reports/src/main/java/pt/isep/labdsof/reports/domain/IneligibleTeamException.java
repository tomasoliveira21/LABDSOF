package pt.isep.labdsof.reports.domain;

/**
 * The team a member routes a request to cannot take it (US-29 D1): unknown, of another event, deleted or without
 * HelpRequests.Respond. One answer for all of them, 422, so nothing is said about other events' teams.
 */
public class IneligibleTeamException extends RuntimeException {
  public IneligibleTeamException() {
    super("The team cannot take help requests");
  }
}
