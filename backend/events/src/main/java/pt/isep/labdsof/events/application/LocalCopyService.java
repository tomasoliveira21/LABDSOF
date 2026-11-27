package pt.isep.labdsof.events.application;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.isep.labdsof.events.domain.GlobalRoleGrant;
import pt.isep.labdsof.events.domain.Membership;

/**
 * Applies the events of Auth &amp; Users to the local copies: idempotent and by version (ADR-004).
 * MembershipChanged keeps who is in which event and team (#192 D2); GlobalRoleChanged keeps who holds a platform
 * role, which is the only thing that makes a caller the FarolAdmin of US-06 and US-07 (US-06 D1).
 */
@Service
public class LocalCopyService {

  public enum Outcome { APPLIED, STALE, DUPLICATE }

  private final ProcessedMessages processed;
  private final LocalCopies copies;
  private final GlobalRoles globalRoles;

  public LocalCopyService(ProcessedMessages processed, LocalCopies copies, GlobalRoles globalRoles) {
    this.processed = processed;
    this.copies = copies;
    this.globalRoles = globalRoles;
  }

  @Transactional
  public Outcome apply(UUID messageId, Membership membership) {
    if (!processed.markProcessed(messageId)) {
      return Outcome.DUPLICATE;
    }
    return copies.upsertIfNewer(membership) ? Outcome.APPLIED : Outcome.STALE;
  }

  @Transactional
  public Outcome apply(UUID messageId, GlobalRoleGrant grant) {
    if (!processed.markProcessed(messageId)) {
      return Outcome.DUPLICATE;
    }
    return globalRoles.upsertIfNewer(grant) ? Outcome.APPLIED : Outcome.STALE;
  }
}
