package pt.isep.labdsof.authusers.application;

/** The account is disabled (the web layer answers 403). */
public class AccountDisabledException extends RuntimeException {

  public AccountDisabledException() {
    super("the account is disabled");
  }
}
