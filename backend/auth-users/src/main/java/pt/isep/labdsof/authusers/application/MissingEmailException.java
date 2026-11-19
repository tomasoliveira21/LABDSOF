package pt.isep.labdsof.authusers.application;

/** The token has no e-mail (the web layer answers 401). */
public class MissingEmailException extends RuntimeException {

  public MissingEmailException() {
    super("the token has no e-mail");
  }
}
