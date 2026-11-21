package pt.isep.labdsof.transports;

/** The one MDC key that carries the correlation id of a request, a message or an import run into the logs (D9). */
public final class Correlation {

  public static final String MDC_KEY = "correlationId";

  private Correlation() {}
}
