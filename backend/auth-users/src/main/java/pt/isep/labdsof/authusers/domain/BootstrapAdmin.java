package pt.isep.labdsof.authusers.domain;

/**
 * The e-mail that becomes FarolAdmin when its account is created (spec D6, #179). Empty grants nobody:
 * callers pass only a non-blank token e-mail, so an empty setting never matches.
 */
public record BootstrapAdmin(String email) {

  public static BootstrapAdmin of(String setting) {
    return new BootstrapAdmin(setting == null ? "" : setting.strip());
  }

  public boolean grants(String tokenEmail, Boolean emailVerified) {
    return Boolean.TRUE.equals(emailVerified) && email.equalsIgnoreCase(tokenEmail.strip());
  }
}
