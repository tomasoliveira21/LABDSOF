package pt.isep.labdsof.events.domain;

/**
 * The platform roles Auth &amp; Users publishes. {@code FarolAdmin} is the only one (features.md §4.1), and it
 * gives <em>no</em> access to an event's data: it creates events (US-06), lists them with their basic details
 * (US-07) and suspends or reactivates them (US-09). Reading one event, its teams or its map still needs a
 * membership, which is what US-14 asks and what {@link EventReader} enforces.
 */
public enum GlobalRole {
  FarolAdmin
}
