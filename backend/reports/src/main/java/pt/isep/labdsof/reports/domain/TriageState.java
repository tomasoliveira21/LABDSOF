package pt.isep.labdsof.reports.domain;

/** Triage state (decision 6). NotTriaged is never stored: it is derived on read. */
public enum TriageState { Pending, Suggested, NotTriaged, Accepted, Changed }
