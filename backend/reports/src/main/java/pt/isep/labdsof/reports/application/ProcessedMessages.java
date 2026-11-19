package pt.isep.labdsof.reports.application;

import java.util.UUID;

/** Port: the messageIds already processed, so a redelivered event is applied once (ADR-004). */
public interface ProcessedMessages {

  /** False when this messageId was already processed. */
  boolean markProcessed(UUID messageId);
}
