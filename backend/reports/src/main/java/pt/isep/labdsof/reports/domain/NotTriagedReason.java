package pt.isep.labdsof.reports.domain;

/** Why a request has no AI suggestion (API outline §4.1, ADR-005). */
public enum NotTriagedReason { Timeout, ProviderError, InvalidOutput, Abstained, Unavailable }
