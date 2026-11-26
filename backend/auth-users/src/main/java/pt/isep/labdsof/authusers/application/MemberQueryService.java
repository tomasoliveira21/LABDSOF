package pt.isep.labdsof.authusers.application;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.isep.labdsof.authusers.domain.EventRole;

/**
 * The event's members for the back office (US-18, US-15): who they are, their role and the teams they are in, so
 * an EventAdmin can pick one to put in a team (spec D1, D10).
 */
@Service
public class MemberQueryService {

  private final EventAdmins admins;
  private final EventMemberships memberships;

  MemberQueryService(EventAdmins admins, EventMemberships memberships) {
    this.admins = admins;
    this.memberships = memberships;
  }

  /** {@code role} and {@code teamId} are optional; a team of another event simply matches nobody. */
  @Transactional(readOnly = true)
  public List<EventMemberships.Member> list(UUID actorUserId, UUID eventId, EventRole role, UUID teamId) {
    admins.require(actorUserId, eventId);
    return memberships.list(eventId, role, teamId);
  }
}
