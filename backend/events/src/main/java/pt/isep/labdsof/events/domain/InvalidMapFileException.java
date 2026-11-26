package pt.isep.labdsof.events.domain;

/**
 * The uploaded file is not a map this service accepts (US-19 AC-2): not a PNG, a JPEG or a single-page PDF, or an
 * image that would not fit in memory (D3), or a PDF that cannot be rendered (D4). The reason is for the log, never
 * for the caller: the answer is one fixed {@code 400} that says what is accepted.
 */
public class InvalidMapFileException extends RuntimeException {

  public InvalidMapFileException(String reason) {
    super(reason);
  }

  public InvalidMapFileException(String reason, Throwable cause) {
    super(reason, cause);
  }
}
