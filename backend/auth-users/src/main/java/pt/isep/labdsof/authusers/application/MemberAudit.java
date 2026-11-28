package pt.isep.labdsof.authusers.application;

import java.time.Instant;
import java.util.UUID;

/**
 * Port: the record of what happened to an event's members (spec D8, story Definition of Done, US-14). Only
 * pseudonymous ids reach it — never an e-mail and never a display name (assessment M-10).
 */
public interface MemberAudit {

  /** Why a call about an event's members was refused, recorded as US-14 asks. */
  enum Refusal { NOT_A_MEMBER, NOT_AN_EVENT_ADMIN, NO_SUCH_MEMBER, NO_SUCH_TEAM, NOT_JOINABLE,
    NOT_A_FAROL_ADMIN, NO_SUCH_EVENT_COPY }

  /** US-12: the caller became a member of the event by its QR code or link. */
  void joined(UUID userId, UUID eventId, Instant at);

  /**
   * US-06 D7: the FarolAdmin looked an account up by e-mail. The address is <em>never</em> recorded — only a
   * hash of it — because this is the one operation of the service whose input is personal data (M-10), and
   * whether it matched, so the trail shows an enumeration attempt even when nothing was found.
   */
  void accountLookedUp(UUID actorUserId, String emailHash, boolean found, Instant at);

  /** US-06: a user was named EventAdmin of the event, the first one or a further one. */
  void namedEventAdmin(UUID actorUserId, UUID eventId, UUID userId, Instant at);

  void teamAssigned(UUID actorUserId, UUID eventId, UUID userId, UUID teamId, Instant at);

  void teamUnassigned(UUID actorUserId, UUID eventId, UUID userId, UUID teamId, Instant at);

  void refused(UUID actorUserId, UUID eventId, Refusal refusal, Instant at);
}
