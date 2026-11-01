# Sprint A close: two architecture and product decisions

**Recorded:** 1 November 2026 · **Source:** the options sent to the team for issue #175 (open points of the Sprint A review, PR #172), and the answer relayed by Bruno de Abreu Lopes on 1 November 2026. This is a record of two decisions, not minutes of a meeting.

## Decisions

| # | Question | Options | Decision |
|---|---|---|---|
| 1 | ADR-001 decision 3 says "Microservices from Sprint A": every service built, deployed and with its own database from the first sprint. Sprint A delivered one service, the R0 skeleton of Reports (#166) | (a) amend decision 3 to what was delivered; (b) keep it and record why Sprint A deviated | **(a)**. The architecture stays five services from the start, with no modular-monolith phase. Sprint A delivers the first service (R0); from Sprint B every service is built, deployed and owns its database, as assignment §7.2.2 asks |
| 2 | Survey decision 4 of 30 October 2026 (phone location first, map as the fallback; 71 % would share it) against assessment M-1 ("the app never asks the phone for GPS") and ADR-007 (a map image with no geographic data) | (a) map only; (b) phone location first already in Sprint B; (c) keep decision 4 as the direction, in phases | **(c)**. The Sprint B vertical slice uses the point on the map, the fallback the product needs anyway. The phone's location comes in Sprint C, after ADR-007 says how a phone position is placed on a map version (e.g. reference points) and M-1 and the API location field are amended |

## Applied (#175)

- Decision 1: [ADR-001](../Technical/adr/0001-architectural-style.md) decision 3, *Status* and *Consequences*; ADR-002, ADR-003, ADR-004, ADR-011 and ADR-013 context; [guide A4](../Technical/architecture-decisions-guide.md); [`architecture.md`](../Technical/architecture.md) §3 and §11; Sprint A deck slide 9.
- Decision 2: [assessment M-1](../Technical/security-and-privacy-assessment.md#6-data-minimisation-measures); [ADR-007](../Technical/adr/0007-venue-map-representation.md) *Open points*; [`features.md`](../Product/features.md) F6 and US-23 (and issue #71); [`user-workflows.md`](../Product/user-workflows.md) W2; [`personas.md`](../Product/personas.md) P1; the report's *Resulting product decisions*; Sprint A deck slide 4. The BPMN of the help request already shows the map point, so it does not change.
