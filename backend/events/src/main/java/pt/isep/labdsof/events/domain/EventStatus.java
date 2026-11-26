package pt.isep.labdsof.events.domain;

/** Constants are the contract values. */
@SuppressWarnings("java:S115")
public enum EventStatus {
  Draft, Published, Suspended, Closed;

  /** Spec D5: participants see an event only while it is Published or Closed. */
  public boolean visibleToParticipants() {
    return this == Published || this == Closed;
  }

  /** US-16 D2, US-19 D6: a suspended or closed event takes no change to its teams or its map. */
  public boolean acceptsChanges() {
    return this == Draft || this == Published;
  }
}
