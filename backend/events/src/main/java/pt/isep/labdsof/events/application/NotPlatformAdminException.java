package pt.isep.labdsof.events.application;

/**
 * A caller who is not the FarolAdmin on an operation that belongs to the platform, not to one event (US-06 AC-2,
 * US-07): 403. Distinct from {@link ForbiddenException}, whose detail speaks of a permission in an event — there
 * is no event to speak of here, and the role is read from the local copy, never from the token (US-06 D1).
 */
public class NotPlatformAdminException extends RuntimeException {

  public NotPlatformAdminException() {
    super("not the FarolAdmin");
  }
}
