package pt.isep.labdsof.transports.application;

import java.util.UUID;

/** Port: the messageIds already consumed, so a redelivered event is applied once (ADR-004). */
public interface ProcessedMessages {

  /** True the first time a messageId is seen, false for a duplicate. */
  boolean markProcessed(UUID messageId);
}
