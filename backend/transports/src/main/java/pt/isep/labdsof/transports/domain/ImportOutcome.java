package pt.isep.labdsof.transports.domain;

/** How a read of the bus source ended (spec D5); stored as written, so the names are the database values. */
@SuppressWarnings("java:S115")
public enum ImportOutcome { Succeeded, Unavailable, Invalid }
