package pt.isep.labdsof.authusers.application;

/** No account holds the e-mail the FarolAdmin looked up (US-06 D7): 404, and the attempt is still recorded. */
public class AccountNotFoundException extends RuntimeException {

  public AccountNotFoundException() {
    super("account not found");
  }
}
