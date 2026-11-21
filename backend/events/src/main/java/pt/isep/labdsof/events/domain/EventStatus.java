package pt.isep.labdsof.events.domain;

/** Constants are the contract values. */
@SuppressWarnings("java:S115")
public enum EventStatus {
  Draft, Published, Suspended, Closed;

  /** Spec D5: participants see an event only while it is Published or Closed. */
  public boolean visibleToParticipants() {
    return this == Published || this == Closed;
  }
}
