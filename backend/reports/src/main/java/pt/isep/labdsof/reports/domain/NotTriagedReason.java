package pt.isep.labdsof.reports.domain;

/** Why a request has no AI suggestion (API outline §4.1, ADR-005). */
// The constants are the contract values (API, events, database, JSON Schemas), so they keep its PascalCase.
@SuppressWarnings("java:S115")
public enum NotTriagedReason { Timeout, ProviderError, InvalidOutput, Abstained, Unavailable }
