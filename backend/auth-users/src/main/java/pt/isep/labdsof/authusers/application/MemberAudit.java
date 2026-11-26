package pt.isep.labdsof.authusers.application;

import java.time.Instant;
import java.util.UUID;

/**
 * Port: the record of what happened to an event's members (spec D8, story Definition of Done, US-14). Only
 * pseudonymous ids reach it — never an e-mail and never a display name (assessment M-10).
 */
public interface MemberAudit {

  /** Why a call about an event's members was refused, recorded as US-14 asks. */
  enum Refusal { NOT_A_MEMBER, NOT_AN_EVENT_ADMIN, NO_SUCH_MEMBER, NO_SUCH_TEAM }

  void teamAssigned(UUID actorUserId, UUID eventId, UUID userId, UUID teamId, Instant at);

  void teamUnassigned(UUID actorUserId, UUID eventId, UUID userId, UUID teamId, Instant at);

  void refused(UUID actorUserId, UUID eventId, Refusal refusal, Instant at);
}
