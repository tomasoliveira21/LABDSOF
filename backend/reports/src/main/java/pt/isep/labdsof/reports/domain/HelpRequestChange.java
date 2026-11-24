package pt.isep.labdsof.reports.domain;

/**
 * One field a member changed, with its old and new value (spec US-35 D3, US-28 D2). The field names and values are
 * those of the history entry in openapi.yaml: category, priority, status, triage.
 */
public record HelpRequestChange(String field, String oldValue, String newValue) {}
