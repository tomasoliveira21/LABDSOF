package pt.isep.labdsof.transports.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class FakeAccessAudit implements AccessAudit {

  record Entry(UUID userId, UUID eventId, Instant at, Refusal refusal) {}

  final List<Entry> refusals = new ArrayList<>();

  @Override
  public void refused(UUID userId, UUID eventId, Instant at, Refusal refusal) {
    refusals.add(new Entry(userId, eventId, at, refusal));
  }
}
