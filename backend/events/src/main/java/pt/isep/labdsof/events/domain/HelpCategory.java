package pt.isep.labdsof.events.domain;

/**
 * The help categories a team handles (features.md §4.2; Theft removed and Infrastructure added on
 * 15 November 2026, decision 8). Constants are the contract values, and their declaration order is the order
 * TeamChanged carries (US-16 D7).
 */
@SuppressWarnings("java:S115")
public enum HelpCategory {
  Health, Safety, LostPerson, Infrastructure, Other
}
