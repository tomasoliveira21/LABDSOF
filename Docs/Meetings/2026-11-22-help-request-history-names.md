# The member's name in the help-request history, and which changes are entries

**Recorded:** 22 November 2026 · **Source:** decisions D5 and D6 of [spec US-28](../specs/US-28-priority-status-history.md), proposed on PR [#245](https://github.com/tomasoliveira21/LABDSOF/pull/245) and accepted on 22 November 2026 by the owner of the Reports stories, with the approval of that PR; the review of #245 asked for this record, because D5 adds personal data to a published contract that the web app consumes. This is a record of decisions, not minutes of a meeting.

## Decisions

| # | Question | Options | Decision |
|---|---|---|---|
| 1 | US-28 AC-1 asks that a change be recorded *with my name*, but Reports has no copy of display names: nothing in Reports consumes `UserProfileChanged`, although the API outline (its decision D4) and assessment M-7 planned that copy | (a) record only the `sub` and let the web app resolve names; (b) add a profile copy fed by `UserProfileChanged`; (c) copy the token's `name` claim into each history entry when the change is recorded | **(c)**. Column `help_request_change.actor_name` (Flyway `V5`), returned as `actorName` by `GET …/history`; `null` when the token has none, blank is `null`, longer than 200 characters (code points) is cut. A name changed later is not rewritten: the entry says who acted, under the name they had. (a) was rejected because the web app has no endpoint for another member's name; (b) builds a new consumer and a new table for one label. The name is personal data: until account deletion (US-05) is built, nothing removes it before the request itself is deleted (see *Still open*) |
| 2 | Is the triage worker's result a history entry? | (a) only a member's change is an entry; (b) also record system entries for the AI's suggestion | **(a)**. The worker never writes the request's category, priority or status (US-34 D6), only the suggestion, which the control-room view shows, and a derived `NotTriaged` is never written (decision 6 of [15 November 2026](2026-11-15-sprint-a-review-and-sprint-b-planning.md)). Every entry has a member's `sub` (`actor_user_id NOT NULL`), so the contract no longer says `actorUserId` may be empty. Routing (US-29), closing (US-30) and the field stories (US-32, US-33) add their own member entries |

## Applied

- Decision 1: `V5__help_request_change_actor_name.sql`; `Actor`, `JdbcHelpRequests` and `HelpRequestController` in `backend/reports`; `actorName` in `backend/reports/contracts/openapi.yaml`; [assessment](../Technical/security-and-privacy-assessment.md) §3, M-7, §7 (account deletion) and §12; [spec US-28](../specs/US-28-priority-status-history.md) D5; `CHANGELOG.md` (#75).
- Decision 2: the `actorUserId` description in `openapi.yaml`; spec US-28 D6; `CHANGELOG.md` (#75).

## Still open

- **Erasing the copied name.** The [assessment §7](../Technical/security-and-privacy-assessment.md#7-retention-and-deletion) says Reports deletes it on `UserDeleted`, but Reports does not consume that event yet (US-05), and the clean-up job of the 30-day retention does not exist either (Sprint C). Until both exist, `actor_name` is removed only with its request (`ON DELETE CASCADE`).
- **System entries** (decision 2, option b) can be proposed again if the team wants the AI's suggestion in the history; the contract would then have to allow an `actorUserId` without a member.
