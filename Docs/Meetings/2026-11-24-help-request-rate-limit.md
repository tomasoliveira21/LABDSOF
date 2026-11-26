# Where the help-request limit is enforced

**Recorded:** 24 November 2026 · **Source:** the open point of US-23 ([#71](https://github.com/tomasoliveira21/LABDSOF/issues/71)) on limit L-2 of [ADR-013](../Technical/adr/0013-api-gateway.md), decided by the owner of Reports with the agreement of the owner of the gateway (decision 13 of the [meeting of 15 November 2026](2026-11-15-sprint-a-review-and-sprint-b-planning.md): Reports and the gateway have different owners, so the change is recorded here).

This is recorded here because it moves a limit that an accepted ADR places in the gateway into another service.

## Decisions

| # | Question | Options | Decision |
|---|---|---|---|
| 1 | Where is L-2 (5 help requests per account in 10 minutes, `429` with `Retry-After`) enforced? The gateway built in #236 has L-1 only (120 calls per minute per account, every route) | (a) the gateway, as ADR-013 says: a second Bucket4j bucket on `POST …/help-requests`, counting every `POST`, counters in memory; (b) Reports: count the help requests it saved, in its database | **(b)**. Only a saved request counts, as the criterion says (*I have sent 5*): a retry with the same `clientRequestId` after a network error, or a request refused as invalid, does not use up a participant's quota, which matters for a call for help. The count survives a restart and `Retry-After` is exact. L-3 (reports) is already a Reports limit of the same kind |
| 2 | The key | (a) the account across events (ADR-013); (b) the account and the event (as L-3) | **(a)**, ADR-013's key, unchanged |

## Applied

- [ADR-013](../Technical/adr/0013-api-gateway.md): dated note under *Rate limits*; the table row is kept as written.
- [Spec US-23](../specs/US-23-ask-for-help.md) D1 to D6; `backend/reports/contracts/openapi.yaml` (`429` on `submitHelpRequest`); [API outline](../Technical/api-outline.md) §4; [security and privacy assessment](../Technical/security-and-privacy-assessment.md) T15; `CHANGELOG.md` (#71).
