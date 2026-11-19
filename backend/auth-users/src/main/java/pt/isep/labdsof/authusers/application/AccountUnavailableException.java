package pt.isep.labdsof.authusers.application;

/** Deleted (tombstone) or being deleted (the web layer answers 401). */
public class AccountUnavailableException extends RuntimeException {

  public AccountUnavailableException() {
    super("the account was deleted or is being deleted");
  }
}
