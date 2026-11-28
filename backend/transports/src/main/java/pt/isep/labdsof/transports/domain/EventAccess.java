package pt.isep.labdsof.transports.domain;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Who may read an event's buses and who may change its transport (spec US-41 D3, D5), from the local copies. Every
 * refusal that would tell an outsider the event exists is {@code NOT_FOUND}, as the security gate asks.
 */
public final class EventAccess {

  /** What a request to change the event's transport may do; a closed event is refused later (409, D3). */
  public enum Edit { ALLOWED, NOT_FOUND, FORBIDDEN }

  private static final Set<EventStatus> REGULAR_USER_READS = Set.of(EventStatus.Published, EventStatus.Closed);
  private static final Set<EventStatus> EVENT_ADMIN_READS =
      Set.of(EventStatus.Draft, EventStatus.Published, EventStatus.Closed);

  private EventAccess() {}

  /** D5: an EventAdmin of a draft, published or closed event; a RegularUser of a published or closed one. */
  public static boolean canRead(Optional<Membership> membership, Optional<EventCopy> event) {
    if (!isActiveMemberOfOpenEvent(membership, event)) {
      return false;
    }
    var readable = membership.get().role() == Membership.Role.EventAdmin ? EVENT_ADMIN_READS : REGULAR_USER_READS;
    return readable.contains(event.get().status());
  }

  /**
   * D3: an active EventAdmin, or an active member of a team of this event, not deleted, with {@code Transport.Edit}.
   * {@code teams} are the copies of the membership's teams that exist.
   */
  public static Edit canEditTransport(Optional<Membership> membership, Optional<EventCopy> event,
      Collection<Team> teams) {
    if (!isActiveMemberOfOpenEvent(membership, event)) {
      return Edit.NOT_FOUND;
    }
    var member = membership.get();
    if (member.role() == Membership.Role.EventAdmin) {
      return Edit.ALLOWED;
    }
    var granted = teams.stream().anyMatch(team -> team.eventId().equals(member.eventId())
        && member.teamIds().contains(team.teamId()) && team.grants(Permission.TRANSPORT_EDIT));
    return granted ? Edit.ALLOWED : Edit.FORBIDDEN;
  }

  /** An unknown event, a suspended one (API outline 7.4), no membership or a removed one: nothing to see. */
  private static boolean isActiveMemberOfOpenEvent(Optional<Membership> membership, Optional<EventCopy> event) {
    return event.isPresent() && !event.get().isSuspended()
        && isActiveMemberOf(event.get().eventId(), membership);
  }

  public static boolean isActiveMemberOf(UUID eventId, Optional<Membership> membership) {
    return membership.isPresent() && membership.get().isActive() && membership.get().eventId().equals(eventId);
  }
}
