package pt.isep.labdsof.authusers.application;

/**
 * A caller who does not hold {@code FarolAdmin} on an operation that belongs to the platform (US-06 AC-2): 403.
 * Distinct from {@link NotAnEventAdminException}, whose detail speaks of being an admin <em>of the event</em>;
 * naming the first EventAdmin of a new event is not something any event's admin may do.
 *
 * <p>Unlike Events, this service owns the global roles ({@link GlobalRoleGrants}), so there is no copy to read
 * and nothing can be stale.
 */
public class NotAFarolAdminException extends RuntimeException {

  public NotAFarolAdminException() {
    super("not a FarolAdmin");
  }
}
