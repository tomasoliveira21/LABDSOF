# Farol — Domain Model

> Conceptual model organised into five services: **Auth & Users**, **Events**, **Reports**, **Transports** and **Badges**. Service boundaries, attributes and cardinalities are proposed for review.

```mermaid
classDiagram
    direction LR
    namespace AuthAndUsers {
        class UserAccount {
            +string displayName
            +AccountStatus status
        }
        class EngagementPreferences {
            +bool earningEnabled
            +BadgeVisibility visibility
        }
        class GlobalRoleGrant {
            +GlobalRole role
        }
        class EventMembership {
            +EventRole role
            +MembershipStatus status
        }
        class TeamAssignment {
            <<association>>
        }
    }
    namespace Events {
        class Event {
            +string name
            +string organiserName
            +DateRange dates
            +EventStatus status
        }
        class ResponseTeam {
            +ResponseTeamType type
            +string name
        }
        class VenueMapVersion {
            +int version
            +FileReference file
        }
        class PointOfInterest {
            +PoiType type
            +string name
            +MapPosition position
        }
        class Announcement {
            +string message
            +datetime publishedAt
            +datetime expiresAt
        }
        class AttendanceCheckIn {
            +datetime checkedInAt
        }
    }
    namespace Reports {
        class HelpRequest {
            +HelpCategory category
            +MapPosition location
            +Priority priority
            +RequestStatus status
            +datetime submittedAt
        }
        class TriageSuggestion {
            +HelpCategory suggestedCategory
            +Priority suggestedPriority
            +TeamId suggestedTeamId
            +SuggestionDecision decision
        }
        class CommunityReport {
            +ReportType type
            +MapPosition location
            +ReportStatus status
            +datetime expiresAt
            +datetime confirmedAt
            +datetime confirmationRevokedAt
        }
        class FieldTask {
            +FieldTaskPurpose purpose
            +FieldTaskStatus status
            +datetime dispatchedAt
        }
        class AuditEntry {
            +ActorRef actor
            +string action
            +ChangeSet changes
            +datetime occurredAt
        }
    }
    namespace Transports {
        class TransportOption {
            +TransportMode mode
            +DataOrigin origin
            +ExternalRouteReference externalRoute
            +string name
            +datetime lastUpdatedAt
        }
        class TransportDeparture {
            +string destination
            +datetime scheduledAt
            +datetime expectedAt
        }
        class BusVehicle {
            +string externalEntityId
            +string fleetVehicleId
            +string dataProvider
        }
        class BusObservation {
            +GeoPosition location
            +datetime observedAt
            +datetime retrievedAt
            +string routeId
        }
    }
    namespace Badges {
        class BadgeProgress {
            +BadgeTrack track
            +int qualifyingCount
            +BadgeTier tier
        }
    }
    namespace ExternalSystems {
        class PortoDigitalAPI {
            <<external>>
        }
    }

    UserAccount "1" *-- "1" EngagementPreferences
    UserAccount "1" -- "0..*" GlobalRoleGrant
    UserAccount "1" -- "0..*" EventMembership
    Event "1" -- "1..*" EventMembership
    Event "1" -- "4" ResponseTeam
    EventMembership "1" -- "0..*" TeamAssignment
    ResponseTeam "1" -- "0..*" TeamAssignment
    Event "1" -- "0..*" VenueMapVersion : retains versions
    VenueMapVersion "1" *-- "0..*" PointOfInterest
    Event "1" -- "0..*" TransportOption
    TransportOption "1" *-- "0..*" TransportDeparture
    TransportOption "0..*" --> "0..*" PointOfInterest : serves stops
    Event "1" -- "0..*" Announcement
    TransportOption "0..*" --> "0..*" BusVehicle : matched by external route reference
    BusVehicle "1" *-- "0..1" BusObservation : latest valid observation
    PortoDigitalAPI ..> BusObservation : supplies Vehicle data
    HelpRequest "1" *-- "0..1" TriageSuggestion
    HelpRequest "0..*" --> "0..1" HelpRequest : duplicate of
    HelpRequest "0..1" -- "0..*" FieldTask : dispatches
    CommunityReport "0..1" -- "0..*" FieldTask : verification or fix
    HelpRequest "0..1" -- "1..*" AuditEntry : history
    CommunityReport "0..1" -- "1..*" AuditEntry : history
    CommunityReport ..> BadgeProgress : eligible confirmation contributes
    AttendanceCheckIn ..> BadgeProgress : attendance contributes
    UserAccount "1" -- "0..*" HelpRequest : submits
    Event "1" -- "0..*" HelpRequest : receives
    HelpRequest "0..*" --> "0..1" ResponseTeam : assigned to
    UserAccount "0..1" <-- "0..*" HelpRequest : accepted by
    UserAccount "1" -- "0..*" CommunityReport : submits
    Event "1" -- "0..*" CommunityReport : receives
    CommunityReport "0..*" --> "1" ResponseTeam : responsible team
    HelpRequest "0..*" --> "1" VenueMapVersion : location reference
    CommunityReport "0..*" --> "1" VenueMapVersion : location reference
    FieldTask "0..*" --> "1" ResponseTeam : dispatched to
    UserAccount "0..1" <-- "0..*" FieldTask : accepted by
    UserAccount "1" -- "0..*" AttendanceCheckIn : checks in
    Event "1" -- "0..*" AttendanceCheckIn : records
    UserAccount "1" -- "0..*" BadgeProgress : earns
    Event "0..1" -- "0..*" BadgeProgress : event reporter scope
    note for FieldTask "Targets exactly one HelpRequest or CommunityReport"
    note for BusObservation "GeoPosition: longitude, latitude<br/>Separate from venue MapPosition"
    note for PortoDigitalAPI "Porto Digital FIWARE — NGSI v2<br/>GET /v2/entities<br/>q=vehicleType==bus, limit=1000"
```

## Entities and concepts

| Service / area | Entity or concept | Purpose and main rule |
|---|---|---|
| Auth & Users | **UserAccount** | One account across events for participants and staff. Staff permissions come from event memberships, not from simply having an account. |
| Auth & Users | **EngagementPreferences** | The account's badge earning and visibility preferences. Badges are private by default; earning is optional. |
| Auth & Users | **GlobalRoleGrant** | Grants a platform-wide role such as PlatformAdmin. It does not itself grant access to event help requests, reports or participants. |
| Auth & Users | **EventMembership** | Links an account to one event with a role and membership status. Proposed roles: Owner, Admin, Operator and Responder; an event keeps at least one active Owner. |
| Auth & Users | **TeamAssignment** | Associates an event membership with a response team in the same event. Determines which team-specific work a member can access. |
| Events | **Event** | A festival edition with its own organiser, dates, status and operational data. Defines the isolation boundary for event-specific information. |
| Events | **ResponseTeam** | An event's Medical, Security, PCR or Logistics team. Help requests can be assigned to the first three; Logistics handles community reports. |
| Events | **VenueMapVersion** | A version of the uploaded venue image/PDF. Historical versions remain available for the requests and reports that refer to them. |
| Events | **PointOfInterest** | A named place on a map version, such as an entrance, stage, WC, first-aid point or transport stop. |
| Events | **Announcement** | Official event information published by authorised staff, with publication time and optional expiry. |
| Events | **AttendanceCheckIn** | Records an authenticated participant's attendance at the venue during the event dates. Counts once per account/event; included as a Should-have feature. |
| Reports | **HelpRequest** | A participant's request for assistance, with category, map location, priority and status. Goes to the event's control-room queue; it never earns badges. |
| Reports | **TriageSuggestion** | Optional AI recommendation attached to a help request. An operator accepts or changes it; it never overwrites a human decision or blocks the request. |
| Reports | **CommunityReport** | A report about a place or service, routed by type to the responsible team. Visible to other participants only while confirmed and active. |
| Reports | **FieldTask** | Work dispatched to a response team to assist with a request, verify a report or fix a problem. Targets exactly one request or report; only one responder can accept it. |
| Cross-cutting; shown in Reports | **AuditEntry** | Records the actor, time and previous/new values of a change. Each service owns its own audit history; the diagram shows request/report history as the operational example. |
| Transports | **TransportOption** | A transport service relevant to an event: manual shuttle or an external service reference. Can serve several stops and match several observed buses. |
| Transports | **TransportDeparture** | A scheduled or expected departure associated with a transport option. Requires manual data or another validated source; the bus-vehicle endpoint does not supply departure times. |
| Transports | **BusVehicle** | A bus identified by its external entity ID and provider. Shared external transport data, rather than an event-owned vehicle. |
| Transports | **BusObservation** | The latest valid bus position, route reference and observation/retrieval timestamps. A fresh retrieval does not imply a fresh observation. |
| Badges | **BadgeProgress** | Derived account progress for ReportHistory, EventReporter or EventAttendance. Confirmed reports count after expiry/resolution; withdrawing confirmation removes their credit. |
| External system | **PortoDigitalAPI** | [Porto Digital FIWARE bus API](https://broker.fiware.urbanplatform.portodigital.pt/v2/entities?q=vehicleType==bus&limit=1000), supplying NGSI v2 vehicle observations. Provides bus context, not timetables or predicted arrivals. |

## How to read the model

- **Scope:** event-specific references must resolve within the same event. Accounts, account-wide badge progress and external bus observations can span events. Associations across services express conceptual references, not shared database tables.
- **Locations:** `MapPosition` uses normalised coordinates on a specific venue-map version. `GeoPosition` uses geographic longitude/latitude for buses. They are different coordinate systems.
- **Cardinalities:** `1` means exactly one; `0..1` means optional; `0..*` means zero or more; `1..*` means one or more. The optional Event association on BadgeProgress applies only to the EventReporter track.
- **Notation:** a filled diamond denotes composition; a plain association connects concepts; a dashed arrow expresses a dependency or contribution. Namespace boxes group concepts by service; ExternalSystems contains the external provider.
