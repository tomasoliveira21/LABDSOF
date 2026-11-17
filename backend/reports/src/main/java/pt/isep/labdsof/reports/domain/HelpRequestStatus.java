package pt.isep.labdsof.reports.domain;

/** One status set for every document (#177, decision 6): Submitted, Seen, OnTheWay, Resolved, Closed. */
// The constants are the contract values (API, events, database, JSON Schemas), so they keep its PascalCase.
@SuppressWarnings("java:S115")
public enum HelpRequestStatus { Submitted, Seen, OnTheWay, Resolved, Closed }
