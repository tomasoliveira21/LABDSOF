package pt.isep.labdsof.events.application;

/**
 * The team changed between the read and the write, so this request would publish a version another change has
 * already used (US-16 D3): 409, nothing stored and nothing published. The caller reads the team again and decides.
 */
public class TeamChangedConcurrentlyException extends RuntimeException {

  public TeamChangedConcurrentlyException() {
    super("team changed concurrently");
  }
}
