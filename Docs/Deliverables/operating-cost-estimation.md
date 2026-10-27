# Operating Cost Estimation

**Course:** LABDSOF 2026/27, Sprint A · **Issue:** #134 · **Owner:** Bruno Lopes · **Date:** 23 October 2026
**Asked for by:** the professor, meeting of 22 October 2026 (P7, [minutes](../Meetings/2026-10-22-professor-meeting.md)): salaries and infrastructure, simple, reasonable assumptions.
**Infrastructure figures:** from the cloud research of #53 ([`deployment.md`](../Research/architecture-investigation/deployment.md), prices of **22 October 2026** in the [price snapshot](../Research/architecture-investigation/azure-price-snapshot.json)).

What Farol would cost to build and to keep running if the team were a startup paying salaries, and why it is delivered locally instead of in the cloud. It is an estimate with stated assumptions, not a quote or a business plan.

## 1. Summary (one slide)

All amounts in euros, without VAT, rounded to the euro.

| Phase | Salaries | Infrastructure | Total |
|---|---:|---:|---:|
| **Development, per month** (6 roles, full time) | 17,360 | 102 (local; Copilot only) | **17,462** |
| **Development, whole project** (24 Sep 2026 – 3 Jan 2027, 3.35 months) | 58,170 | 341 | **58,511** |
| *Same, if the demo ran in the cloud* (Azure, 100 h/month, Sprint B and C) | *58,170* | *642* | *58,812* |
| **Maintenance, per month** (1 FTE, production in the cloud, 730 h) | 3,029 | 377 | **3,406** |
| **Maintenance, first year** (12 months) | 36,348 | 4,518 | **40,866** |
| **Development + first year** | | | **≈ 99,380** |

**Why local:** salaries are a notional cost for a student team; infrastructure is the only cost the team would actually pay. Running locally costs 0 € beyond Copilot, which is the same in both scenarios. The cloud demo would cost about 301 €, and continuous cloud operation 358 € per month, against a one-off Azure for Students credit of 89 € ($100). See §5.

## 2. Assumptions

| # | Assumption | Value | Source or reason |
|---|---|---|---|
| A1 | Staffing | **6 people, one per role**, full time: Product Owner, Scrum Master, Tech lead/architect, QA lead, DevOps lead, AI lead | Roles in [`team-and-process.md`](../Process/team-and-process.md) §2. A startup would hire one person per role; the student team covers the six roles with 4 people, so this is about 50% above the team's size |
| A2 | Development period | 24 Sep 2026 – 3 Jan 2027 = 102 days = **3.35 months** (102 / 30.44) | Start: first commit of the repository (Sprint A has no written start date). End: Sprint C review ([`features.md`](../Product/features.md) §8.1) |
| A3 | Period with code to deploy | 2 Nov 2026 – 3 Jan 2027 = 63 days = **2.07 months** | Sprint B and C ([`features.md`](../Product/features.md) §8.1) |
| A4 | Gross salary, Junior | **1,816 € per month**, paid 14 times a year | [Teamlyzer salary calculator](https://pt.teamlyzer.com/users/salary-calculator), average of all IT roles in Portugal by seniority, updated 23 Oct 2026, 23,585 salaries. No public source gives junior salaries per role (Hays and Michael Page guides need registration) |
| A5 | Gross salary, Middle | **2,380 € per month**, paid 14 times a year | Same source as A4 |
| A6 | Level per role | **Middle:** Tech lead/architect, Product Owner (they decide the architecture and the product). **Junior:** Scrum Master, QA, DevOps, AI lead | Team decision for this estimate: a newly created startup |
| A7 | Employer cost | Gross × 14 / 12 × **1.2375** (employer social security, TSU 23.75%) | Portuguese general regime. Meal allowance, insurance and equipment are not included |
| A8 | Region | National average used for Porto | Porto is 0.5% above the national average in the same source (2,447 € vs 2,434 €): negligible |
| A9 | Maintenance team | **1 FTE**: 0.5 DevOps (Junior) + 0.5 Tech lead (Middle) | Fixes, dependency updates and operation. Team decision for this estimate |
| A10 | Currency | USD → EUR at **1.1205 USD per euro** | [ECB reference rate](https://www.ecb.europa.eu/stats/policy_and_exchange_rates/euro_reference_exchange_rates/html/index.en.html) of 22 Oct 2026, the date of the price snapshot |
| A11 | Taxes | All amounts **without VAT** | The cloud prices in the snapshot are pre-tax retail prices |

## 3. Salaries

Employer cost per month (A4, A5, A7): Junior 1,816 × 14 / 12 × 1.2375 = **2,622 €**; Middle 2,380 × 14 / 12 × 1.2375 = **3,436 €**.

| Role | Level (A6) | Development, per month | Maintenance, per month (A9) |
|---|---|---:|---:|
| Product Owner | Middle | 3,436 | – |
| Tech lead / architect | Middle | 3,436 | 0.5 × 3,436 = 1,718 |
| Scrum Master | Junior | 2,622 | – |
| QA lead | Junior | 2,622 | – |
| DevOps lead | Junior | 2,622 | 0.5 × 2,622 = 1,311 |
| AI lead | Junior | 2,622 | – |
| **Total per month** | | **17,360** | **3,029** |
| **Total for the phase** | | × 3.35 months (A2) = **58,170** | × 12 months = **36,348** |

**Check against the backlog:** 6 people × 3.35 months × about 21 working days ≈ 420 person-days. The 62 user stories take 62 to 186 person-days (each is "one PR of one to three days", [`features.md`](../Product/features.md) §8.5). The rest goes to Sprint A (research, product and architecture documents), the walking skeleton, tests, the AI evaluation and the presentations.

## 4. Infrastructure

### 4.1 Local scenario (what is actually used)

Delivery is local only ([ADR-011](../Technical/adr/0011-deployment-and-infrastructure.md)): everything runs with Docker Compose on one team member's laptop, with a second laptop as a backup for the demo, and the phones open the web app over the LAN ([`local-delivery.md`](../Technical/local-delivery.md), #129).

| Item | Per month | Assumption |
|---|---:|---|
| Laptops (demo and backup), Docker, PostgreSQL (one instance, five databases), RabbitMQ | 0 € | Equipment the team already owns. Electricity and wear are not measured, so they are not zero, only not counted (ADR-011) |
| Container registry | 0 € | No registry: images built locally from the git tag (proposed in #127; guide H7 still open) |
| App stores, mobile builds | 0 € | No mobile app: one React web app (guide F1, #125) |
| GitHub Free, private repository, GitHub Actions | 0 € | 2,000 Actions minutes per month; the pipeline is designed to fit them and uses no paid feature ([ADR-010](../Technical/adr/0010-cicd-strategy.md)) |
| AI triage with local Ollama | 0 € | No API charges; runs on the demo laptop if guide K1 picks it ([`responsible-ai.md`](../Research/architecture-investigation/responsible-ai.md)) |
| GitHub Copilot Business (automatic code review, README §5) | 6 × $19 = $114 = **102 €** | Copilot Business, the plan for organisations, $19 per user per month ([GitHub plans](https://docs.github.com/en/copilot/get-started/plans), checked 26 Oct 2026), one licence per person (A1). Notional, like the salaries: the team uses Copilot Pro free through GitHub Education, so the real cost is 0 € |
| **Total per month** | **102 €** | × 3.35 months (A2) = **341 €** |

### 4.2 Cloud scenario (researched in #53, not used)

Azure Container Apps for the five services, Azure Database for PostgreSQL Flexible Server (one server, five databases), a VM for RabbitMQ, Azure Container Registry, West Europe. All figures from [`deployment.md`](../Research/architecture-investigation/deployment.md) §*Cost and budget gates*, prices of 22 Oct 2026:

| Scenario | USD per month | EUR per month (A10) | Used for |
|---|---:|---:|---|
| **Demo, 100 h/month** of application time; database, broker and registry always on | 162.84 | **145** | Development: Sprint B and C, × 2.07 months (A3) = **301 €**, plus Copilot (341 €) = 642 € |
| **Continuous, 730 h/month** | 400.98 | **358** | Maintenance: a production system runs all the time |

Both include $30 of unquoted allowances (disks, networking, telemetry). They do **not** include AI, VAT, an API gateway, overlapping revisions or private-network charges (deployment.md), so the real bill would be higher. In the cloud scenario a registry is needed, because the platform pulls images from it; the "no registry" proposal (#127) is for local delivery.

### 4.3 AI (ADR-005)

| Option | Cost | Assumption |
|---|---|---|
| Local Ollama (`qwen3:4b`) | 0 € of API | Runs on the laptop; in the cloud it would need its own host (unquoted, [ADR-005](../Technical/adr/0005-ai-triage-provider-and-evaluation.md)) |
| Hosted Mistral Small 4 | **$0.000192 per request** = 0.00017 € | $0.15 per million input tokens, $0.60 per million output, 800 input + 120 output tokens per request ([`responsible-ai.md`](../Research/architecture-investigation/responsible-ai.md), research date) |
| Hosted, 10,000 help requests per month | $1.92 = **2 €** per month | Example volume from the same source; counted in the maintenance month |

Even at hosted prices the AI is negligible next to hosting. The choice of provider (guide K1) depends on quality, latency and privacy, not on cost.

### 4.4 Maintenance month

| Item | Per month |
|---|---:|
| Cloud, continuous (730 h) | 358 € |
| GitHub Copilot Business, 1 licence (A9) | 17 € |
| Hosted AI, 10,000 requests | 2 € |
| **Total infrastructure** | **377 €** (× 12 = 4,518 €) |

## 5. Why the delivery is local

1. **Salaries are notional; infrastructure is real money.** The team is not paid, so the only cash it would spend is on infrastructure. That is the part that decides where the system runs.
2. **The cloud does not fit the available credit.** Azure for Students gives a one-off credit of $100 (89 €), not $100 per month ([`deployment.md`](../Research/architecture-investigation/deployment.md)). The reduced demo would cost about 301 € over Sprint B and C, 3.4 times the credit; continuous operation would cost 358 € every month, 4 times the credit, with costs still missing from the quote (§4.2).
3. **Locally the extra cost is 0 € beyond Copilot**, which is the same in both scenarios (and free for the team through GitHub Education). The same five independent services, five databases and RabbitMQ run on a laptop the team already has, which shows independent deployment without a cloud bill.
4. **The professor agreed.** After discussing these costs, the professor authorised local delivery, provided the report and presentation explain that cost is the reason ([clarification](../Meetings/2026-10-22-local-deployment-clarification.md), ADR-011).

**What this costs the project:** cloud operation, managed scaling and cloud recovery are not demonstrated. The local topology, the smoke test and the degraded-mode tests are shown instead ([`local-delivery.md`](../Technical/local-delivery.md)).

## 6. Limits of this estimate

- Salaries come from a seniority average for all IT roles, not from a per-role junior figure; per-role junior salaries are not public.
- The cloud figures are the dated scenarios of #53, not a quote, and they leave costs out (§4.2).
- Not included: meal allowance, insurance, equipment, office, electricity, VAT, recruitment, legal costs, and the Porto Digital API (open data, free).
- Out of scope: a formal quote or a business plan.
