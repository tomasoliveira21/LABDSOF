# Mockups

Mockups of the front-end pages, one file or folder per story, named after it (e.g. `US-23-ask-for-help`). Since the meeting of 15 November 2026 ([record](../../Meetings/2026-11-15-sprint-a-review-and-sprint-b-planning.md), decision 3), a story with front-end pages is *Ready* only when its mockups are here and linked from its issue ([DoR](../../Process/definition-of-ready-and-done.md#definition-of-ready-backlog-item)). They are generated with Claude and cover every state the user can see: empty, loading, error and each status of the entities shown ([domain model](../../Technical/domain-model.md)).

Sprint B pages: participant pages in #199, staff pages in #200.

### Status coverage for lifecycle stories

Use the [domain lifecycles](../../Technical/domain-model.md#states-and-transitions) with each story's acceptance criteria. Show a status only on a page and for a role that can see it.

| Stories | States or outcomes the mockups cover |
|---|---|
| US-01, US-03, US-05 | Signed-in account, account unavailable, deletion pending and completion, where the flow exposes them |
| US-06 to US-13 | Draft, published, suspended and closed where visible to staff. For the participant join flow, show published and the refused-link outcome for other statuses |
| US-15, US-18 | Active membership, role or team changes, and the unavailable-member outcome |
| US-23, US-24, US-26 to US-33 | Submitted, seen, on the way, resolved and closed where visible to the participant or staff |
| US-47, US-49 to US-53 | Pending, confirmed, rejected, expired, unconfirmed and resolved where visible to the reporter or reviewer. Other participants only see confirmed, active reports |
| US-29, US-31 to US-33, US-52, US-53 | Open, accepted, answered, resolved and cancelled where visible by task purpose and role |

### Sprint B participant mockups

| Story | Phone artboards |
|---|---|
| [US-12 · Join by QR or link](https://github.com/tomasoliveira21/LABDSOF/issues/63) | [Join an event](US-12-join-event.svg) |
| [US-21 · Open the venue map](https://github.com/tomasoliveira21/LABDSOF/issues/70) | [Open the venue map](US-21-open-map.svg) |
| [US-23 · Ask for help on the map](https://github.com/tomasoliveira21/LABDSOF/issues/71) | [Ask for help](US-23-ask-for-help.svg) |
| [US-24 · Follow a help request](https://github.com/tomasoliveira21/LABDSOF/issues/72) | [Follow a help request](US-24-follow-request.svg) |

The artboards use fictitious requests and locations. The QR flow uses the same unavailable-link message for draft, suspended, closed and invalid links so it does not disclose event details. The help form uses manual map placement for Sprint B.
