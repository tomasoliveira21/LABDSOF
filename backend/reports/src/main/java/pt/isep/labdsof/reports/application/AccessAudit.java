package pt.isep.labdsof.reports.application;

import java.time.Instant;
import java.util.UUID;

/** Port for US-14 authorization refusals on event data. */
public interface AccessAudit {

  enum Refusal { NOT_A_MEMBER, NOT_VISIBLE, MISSING_PERMISSION }

  void refused(String userId, UUID eventId, Instant at, Refusal refusal);
}
