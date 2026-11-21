package pt.isep.labdsof.events;

/** The one MDC key that carries the correlation id of a request or message into the structured logs. */
public final class Correlation {

  public static final String MDC_KEY = "correlationId";

  private Correlation() {}
}
