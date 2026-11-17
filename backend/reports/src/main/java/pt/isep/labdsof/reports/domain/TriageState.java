package pt.isep.labdsof.reports.domain;

/** Triage state (decision 6). NotTriaged is never stored: it is derived on read. */
// The constants are the contract values (API, events, database, JSON Schemas), so they keep its PascalCase.
@SuppressWarnings("java:S115")
public enum TriageState { Pending, Suggested, NotTriaged, Accepted, Changed }
