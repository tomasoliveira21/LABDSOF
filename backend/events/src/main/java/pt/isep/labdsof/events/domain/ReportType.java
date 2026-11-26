package pt.isep.labdsof.events.domain;

/**
 * The community report types a team with Reports.Review reviews. Proposed codes until US-17 (spec #192 D6);
 * constants are the contract values and their declaration order is the order TeamChanged carries.
 */
@SuppressWarnings("java:S115")
public enum ReportType {
  QueueAtEntrance, CrowdedArea, WcProblem, NoWater, Hazard, TransportProblem, WrongInformation
}
