# Sprint A review and Sprint B planning

**Recorded:** 15 November 2026 · **Source:** the professor's feedback on the two Sprint A presentations (T/TP and PL classes), as relayed to the team, and the team's answers to the planning questions of issue #188, relayed by Bruno de Abreu Lopes on 15 November 2026.

## 1. Feedback on the Sprint A presentations

No major problem was pointed out in either presentation.

| Class | Feedback | Response |
|---|---|---|
| **T/TP** | The product must solve a real problem and make it simpler, and the AI must be used where it is really useful. For the help-request triage, that is the **priority**, more than the category | Decisions 1 and 2. This is the decision revised after the Sprint A review (assignment §5.7) |
| **PL** | In the Definition of Ready, a front-end item must have **mockups**, and every entity it uses must have its **states defined** in the domain model | Decisions 3 and 4 |

## 2. Decisions

| # | Question | Decision |
|---|---|---|
| 1 | What does the AI triage do? | **The participant no longer chooses a category.** They write a short description (required, 1 to 500 characters, assessment M-2) and mark the point on the map. The AI reads **only the description** and suggests the **category** and the **priority**, each from a fixed set, with a short reason. The **team is not suggested by the AI**: the default team is the event's team that handles the suggested category (`features.md` §4.2), a rule, not a model output. The operator accepts or changes the suggestion; the AI never overwrites a member's decision (NFR-22). Without a valid suggestion within 30 s (scenario D1), the request is shown at once with its description, **no category and no priority**, marked *Not triaged*, in order of arrival, and the operator sets both by hand. Gates: zero urgent cases under-prioritised first, then category accuracy (issue #197). Replaces the design of 8 October 2026, where the participant chose the category and the AI also suggested the team |
| 2 | Priority scale (AI-3, API outline O7) | Four levels, below. The "default priority of each category" is dropped: with no category chosen by the participant there is nothing to default from |
| 3 | Mockups in the DoR | **A story with front-end pages is Ready only with mockups** of those pages, covering every state the user can see. Mockups are generated with Claude and kept in `Docs/Product/mockups/`, linked from the story issue. Stories without pages do not need them |
| 4 | States of the domain entities | Proposed below (§4), to be confirmed by the team in #198. A front-end story is Ready only when the entities it shows have their states defined |
| 5 | Role model (#178) | **Confirmed** as written on 22 October 2026 (`features.md` §4): roles `FarolAdmin`, `EventAdmin`, `RegularUser`; teams created per event with permissions from the fixed list |
| 6 | Help-request lifecycle (#177) | **One status set**: `Submitted → Seen → OnTheWay → Resolved → Closed`, with the triage as a separate state (`Pending`, `Suggested`, `NotTriaged`, `Accepted`, `Changed`). `NotTriaged` is **derived when the request is read** (still `Pending` 30 s after submission), so a dead-lettered or lost `HelpRequestSubmitted` needs no sweeper job. **Local copies**: the queues are declared by the broker at start-up (definitions file) and are durable, so a service that starts late finds every event waiting; events carry the full state with a version and consumers upsert. A service whose database alone is reset is rebuilt by resetting the stack with its seed data; a republish command is added only if that proves insufficient |
| 7 | Verified e-mail for the first FarolAdmin (#179) | **Yes**: the bootstrap grants `FarolAdmin` only when the Keycloak token has `email_verified: true` |
| 8 | Help categories | `Health`, `Safety`, `LostPerson`, **`Infrastructure`**, `Other`. **`Theft` is removed** (a theft is a `Safety` case). **`Infrastructure`** is something broken or damaged on the site that needs a team now, such as a fallen barrier, a damaged structure or an exposed cable. A broken thing that only informs other participants stays a community report (F12) |
| 9 | US-41 and the Porto Digital data (API outline O6) | Whatever the source supports: US-41 shows the buses on the event's routes and how fresh their positions are, not timetables, and no longer names STCP ([ADR-008](../Technical/adr/0008-transport-data-source.md)) |
| 10 | HTTPS for phones on the LAN | **The demo runs only on the laptop.** The participant pages are shown in the laptop's browser at phone width, on `localhost`, where the PKCE login works without HTTPS; phones are not used in the demo. The phone's location (Sprint C, decision 2 of 1 November 2026) also needs HTTPS, so this is revisited then |
| 11 | Sprint B scope | **Goal of `features.md` §8.1 confirmed**, with a cut line: US-27, US-20, US-24, US-28 (only the history) and US-30 are dropped first, in that order, if the sprint runs short |
| 12 | Badges service in Sprint B | **Added**: a minimal Badges service is deployed with its own database in Sprint B, as decided on 1 November 2026, although its stories are in Sprint C |
| 13 | Who owns each service | Decided later the same day: **Tomás Oliveira**, the web app and Badges; **António Guerra**, Transports; **Tiago Teixeira**, Events and Auth & Users; **Bruno de Abreu Lopes**, Reports, the AI triage and observability, and first the initial Docker base that every service starts from (#202). The issues carry the owner as assignee |
| 14 | Sprint A retrospective actions | Not recorded |

### Priority scale (decision 2)

| Level | Meaning | Examples |
|---|---|---|
| **Critical** | A life or someone's physical safety is at risk now; a team goes at once | Someone unconscious or needing emergency medical help; a fight with injuries; a structure about to fall on people; an exposed live cable where people walk |
| **High** | Someone is hurt or at risk and needs a team within minutes, but no life is in danger | A lost child; a person very drunk and alone; a cut that keeps bleeding; harassment happening now; a fallen barrier next to the crowd |
| **Medium** | Needs a team, but nobody is in danger | A lost adult friend; a small wound; someone unwell but conscious and accompanied; a stolen phone; a broken toilet door |
| **Low** | An inconvenience that can wait | A spilled drink on the floor; a light out away from the paths; a question for the staff |

## 3. Risk noted

A typed description is slower than a tap on a category for someone in a hurry or unwell. The participant pages keep the text short, with examples in the field, and the mockups of #199 test it. If the AI is down, the operator reads the description (scenario D1).

## 4. Proposed states of the domain entities (decision 4)

Proposed on 15 November 2026 for #198; not yet confirmed by the team.

| Entity | States | Transitions (who, story) |
|---|---|---|
| UserAccount | `Active`, `Disabled`, `DeletionPending` | `Active → DeletionPending` when the user deletes the account (US-05), then the row is deleted once Keycloak confirms (ADR-006, D7); `Active ⇄ Disabled` when the account is disabled in Keycloak (ADR-006, account-disabled event; no MVP story) |
| EventMembership | none | No MVP story ends a membership other than account deletion, which deletes it. **Proposal: drop `MembershipStatus`**; a membership exists or it does not |
| Event | `Draft`, `Published`, `Suspended`, `Closed` | `Draft → Published` (EventAdmin, US-11); `Draft` or `Published → Suspended` and back to the previous state (FarolAdmin, US-09); `Published → Closed` (EventAdmin, US-13); `Closed` is final |
| HelpRequest | `Submitted`, `Seen`, `OnTheWay`, `Resolved`, `Closed` | `Submitted → Seen` (control room, `/acknowledge`, US-28); routing (US-29) creates a field task and keeps the status; `Seen → OnTheWay` when a field-team member accepts the task (US-32); `OnTheWay → Resolved` (US-33); any status → `Closed` (control room, US-30). A cancelled task returns the request to `Seen` |
| Triage (of a HelpRequest) | `Pending`, `Suggested`, `NotTriaged`, `Accepted`, `Changed` | `Pending → Suggested` (valid AI answer within 30 s, US-34); `Pending → NotTriaged` (derived at 30 s, US-36); `Suggested → Accepted` or `Changed`, and `NotTriaged → Changed` (control room, US-35) |
| CommunityReport | `Pending`, `Confirmed`, `Rejected`, `Expired`, `Resolved`, `Unconfirmed` | As proposed in `features.md` §6: `Pending → Confirmed`, `Rejected` or `Expired` (reviewer, US-50; expiry by type, §6.1); `Confirmed → Resolved` (field team, US-53), `Expired` or `Unconfirmed` (reviewer, US-51) |
| FieldTask | `Open`, `Accepted`, `Answered`, `Resolved`, `Cancelled` | `Open → Accepted` (one team member, US-32, US-52); `Accepted → Answered` (*Verify* task, US-52) or `Resolved` (*Assist* or *Fix*, US-33, US-53); `Open` or `Accepted → Cancelled` when its team is deleted (US-16) |

## 5. Applied (#188)

Meeting decisions applied to the documents and issues listed in the [CHANGELOG](../../CHANGELOG.md) entry of 15 November 2026. The AI evaluation package (dataset, schema, scorer and baseline) is updated in #197, and the states of §4 in #198.
