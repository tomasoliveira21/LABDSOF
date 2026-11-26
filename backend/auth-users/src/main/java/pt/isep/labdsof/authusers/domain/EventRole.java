package pt.isep.labdsof.authusers.domain;

/**
 * What a member may do in one event (features.md §4, API outline §1.5). It is never read from the token
 * (ADR-006): this service owns the memberships and publishes them as {@code MembershipChanged}.
 */
public enum EventRole {
  EventAdmin,
  RegularUser
}
