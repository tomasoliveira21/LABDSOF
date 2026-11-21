package pt.isep.labdsof.events.application;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.isep.labdsof.events.domain.Membership;

/** Applies MembershipChanged to the local copy: idempotent and by version (ADR-004). */
@Service
public class LocalCopyService {

  public enum Outcome { APPLIED, STALE, DUPLICATE }

  private final ProcessedMessages processed;
  private final LocalCopies copies;

  public LocalCopyService(ProcessedMessages processed, LocalCopies copies) {
    this.processed = processed;
    this.copies = copies;
  }

  @Transactional
  public Outcome apply(UUID messageId, Membership membership) {
    if (!processed.markProcessed(messageId)) {
      return Outcome.DUPLICATE;
    }
    return copies.upsertIfNewer(membership) ? Outcome.APPLIED : Outcome.STALE;
  }
}
