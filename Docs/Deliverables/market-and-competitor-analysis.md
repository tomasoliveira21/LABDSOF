# Market and Competitor Analysis (general)

> LABDSOF 2026/27 · Sprint A
> **What this document is:** the **general** view of the academic event management market in Portugal, phase by phase, with everything that exists today. The **specific** analysis of the features we want to include in the project is in the annex [`market-and-competitor-analysis-product-specific.md`](market-and-competitor-analysis-product-specific.md) (annex 02a).
> **Aligned with the repository** (`Docs/Deliverables/problem-opportunity-report-queima.md`, commit `442ecc3`, 2026-10-03): a **multi-event** coordination platform for large-scale academic events, with a web application for the participant and a backoffice for each event's team. Pilot case: Queima das Fitas do Porto.
> **Target market:** Portugal. International references are for comparison only.
>
> **Legend:** no mark = fact with a source · "Not identified" = we did not find it in the sources (it does not mean it does not exist). Competitors' features are the ones they claim themselves; we did not test them.
>
> **Decisions taken since 6 October 2026** (added 31 October 2026, #162). This analysis was written on 6 October; the team decided later, and where the text below differs, these decisions win:
> - **Transport:** live positions of the city buses near the venue come from the Porto Digital urban platform, plus the event shuttles and temporary stops entered by the team ([ADR-008](../Technical/adr/0008-transport-data-source.md), F9 in [`features.md`](../Product/features.md)). A link to Google Maps is no longer the plan for regular lines.
> - **AI:** help-request triage was chosen on 8 October 2026; the participant Q&A assistant was rejected (a wrong transport answer at night is a safety risk; [`features.md` §5](../Product/features.md#5-ai-assisted-workflow-help-request-triage)).
> - **Weak network:** offline mode (cached map and information) is F11, a *Could* since 9 October 2026; the time of the last update on each item stays in the MVP (SC7, [product vision](../Product/product-vision.md)).

---

## 1. Context

| Indicator | Value | Source |
|---|---|---|
| Students in higher education (2025/26) | 447,680 | [ECO](https://eco.sapo.pt/2026/04/24/numero-de-inscritos-no-ensino-superior-e-o-mais-alto-da-ultima-decada/) |
| Budgets of the big festivals (2023) | Porto €2.5 M · Coimbra €1.5 M · Braga €0.9 M · Aveiro €0.6 M | [Notícias Magazine](https://www.noticiasmagazine.pt/2023/os-milhoes-que-alimentam-as-associacoes-academicas/historias/288877/) |
| Queima do Porto 2026 | About 250,000 people over 8 nights; 35% of the budget on security; more than 500 staff per day | [Observador](https://observador.pt/2026/04/30/queima-das-fitas-do-porto-arranca-com-maior-investimento-em-seguranca-de-sempre-diz-fap/) |
| Queima de Coimbra 2026 | €2.2 M; more than 210,000 visitors | [As Beiras](https://www.asbeiras.pt/orcamento-da-queima-das-fitas-supera-os-dois-milhoes-de-euros/) |
| Queues at Queima do Porto (2022 and 2023) | Queues of more than 500 m, waits of more than 3 h, reports of crushing; complaints about "lack of information and signage" | [Renascença](https://rr.pt/noticia/pais/2022/05/05/filas-para-a-queima-das-fitas-do-porto-achei-que-ia-colapsar-a-ser-esmagada-e-quase-a-sufocar/283010/), [Público](https://www.publico.pt/2023/05/08/p3/noticia/primeira-noite-caotica-queima-fitas-porto-voltou-normal-2048919) |

Today, each phase of the event is handled by a different tool, and the information that changes during the night does not reach the participant (Problem and Opportunity Report, section 2).

---

## 2. Alternatives by phase

### Phase 1 — Availability and purchase *(outside the product; partners)*

| Alternative | Strengths | Weaknesses | AI |
|---|---|---|---|
| **BOL** (Queimas do Porto and Coimbra, Enterro da Gata) | Leader in academic festivals; shop branded with the event; physical network; fights resale | 6% fee + VAT; ends at the sale | Not identified |
| **Ticketline / MEO Blueticket / Fnac** | Well-known brands; physical points of sale | About 6% fee; only handle the sale | Not identified |
| **Fever (+ DICE) / Shotgun** | Event discovery; young audience; managed resale | Focused on consumption and marketing; organiser pricing is not public | **Yes** (Fever): recommendations and demand forecasting |
| **Eventbrite** | Self-service; check-in app | Expensive on cheap tickets (about 15.6% on a US$20 ticket) | **Yes**: generates descriptions, emails and ads |
| **APP FAP** | Official app; student discount; tops up the cashless wristband | A single federation; does not cover event operations | Not identified |
| **Status quo** (Instagram + Google Forms + MB Way) | Free and familiar | Payments confirmed by hand; no control of capacity (how many tickets are left) | — |
| *International ref.:* **FIXR / native** (United Kingdom) | Built for student unions; student-only tickets | Not present in Portugal; do not handle the venue | Not identified |

### Phase 2 — Entry and access control *(the product only informs; physical capacity is out of scope)*

| Alternative | Strengths | Weaknesses | AI |
|---|---|---|---|
| **Queima do Porto cashless wristband** (entry + payment) | One wristband for everything; less fraud | In 2023, exchanging the ticket for the wristband at the gate caused hours of queuing ([JN](https://www.jn.pt/pais/artigo/horas-na-fila-e-nao-entraram-troca-de-bilhete-por-pulseira-gera-confusao-no-queimodromo/16310596)); the participant does not know which gate is less crowded | No |
| **The ticketing platform's own scanners** (BOL, Eventbrite, pretix) | QR validation included | Each platform has its own scanner; no real-time occupancy per zone | No |
| **Paper or Excel lists** (small events) | Free; work without a network | Fraud, queues, no data | — |
| *International ref.:* **Weezevent** (France) | Ticket, entry (QR/NFC, zones, offline) and cashless in one platform | Professional festivals; no known presence in Portugal | Not identified |
| *International ref.:* **Waitwhile** (virtual queue) | Join the queue by link or SMS, with an estimated wait time; more than 30,000 people managed at the Tribeca Film Festival ([case](https://waitwhile.com/case-studies/tribeca-film-festival/)) | Designed for experience and service queues, not for the gates of a venue with tens of thousands of people | Not identified |

### Phase 3 — During the event

#### 3a. Participant information and wayfinding at the venue

| Alternative | Strengths | Weaknesses | AI |
|---|---|---|---|
| **Instagram, event website and map as an image** (status quo) | It is where the audience is; zero cost | Static information, published before the event; the map is an image that is not updated; does not reach those already at the venue | — |
| **APP FAP** | Porto student base; official brand | Tickets, discounts and sport; no map, operational alerts or help (according to FAP's page) | Not identified |
| **Portuguese festival apps** (NOS Alive, Rock in Rio Lisboa) | Line-up, map, schedule, shuttle information | One app per festival; information only; no help request | Not identified |
| **BindePaFesta** (PT, launched in June 2026) | A single map of the country's village and popular festivals; reached the top of the Portuguese App Store ([RFM](https://rfm.pt/atualidade/23680/esta-app-portuguesa-mostra-te-onde-sao-as-festas-da-terrinha-e-ja-esta-entre-as-mais-descarregadas)) | For discovering festivals, not for wayfinding inside the venue or for operations | Not identified |
| *International ref.:* **Appmiral** (CM.com) | Mature festival app: map, timetables, push, SOS button with location | €375–935/month; one app per festival; requires installation | Not identified |
| *International ref.:* **Woov** | One app for many festivals; map, timetables, friends; has a Portuguese interface ([App Store](https://apps.apple.com/nl/app/woov-your-festival-companion/id1580044680)) | Requires installation and collects exact location; no help request or operations | **Yes**: automatic chat moderation |
| *International ref.:* **LineUpr** (Germany) | Event app as a PWA, no installation; yearly subscription for several events ([pricing](https://lineupr.com/en/pricing/)) | Built for conferences; no help or incidents | Not identified |
| *International ref.:* **42Chat FestivalBot** (USA) | SMS or web assistant that answers questions about the event (more than 60 topics) ([site](https://www.42chat.com/festivalbot)) | Questions and answers only; pricing not public; does not say how it handles what it cannot answer | **Yes**: chatbot |

#### 3b. Payments at the venue *(outside the product)*

| Alternative | Strengths | Weaknesses | AI |
|---|---|---|---|
| **Zpos** (POS, PT) | Certified by the Tax Authority; MB Way; works offline for up to 5 days; from €19.90/month | Payments and invoicing only | Not identified |
| **SumUp / ifthenpay** | Cheap and well known | Payment only | No |

#### 3c. Help requests, incidents and team coordination

| Alternative | Strengths | Weaknesses | AI |
|---|---|---|---|
| **Radio + WhatsApp groups** | Immediate; radio works without a network | No record, no priority, no precise location; do not reach the participant | — |
| **WhatsApp/QR line + Ponto Lilás + colour-coded volunteers** (Queima do Porto) | Human, familiar, no installation | Requests without category, place or status; leaves no data | — |
| **Zello** (walkie-talkie on the phone) | Replaces radios for US$7–13/user/month | Depends on the network; unstructured voice | Partial: transcription |
| *International ref.:* **Raven Controls** (COP26, FIFA) | Full incident → response cycle; the public reports by WhatsApp/QR | Enterprise; no PT; designed for professional control rooms | **Yes**: suggests category, priority and procedure |
| *International ref.:* **CriticalArc SafeZone** (campus) | Three help buttons; location only at the moment of the alert; about 25% of UK universities | Campus only; requires an app; institutional licence | Not identified |
| *International ref.:* **Rosterfy / VolunteerLocal** | Volunteer rosters and check-in | Do not handle incidents in real time | **Yes** (Rosterfy) |

### Phase 4 — Getting home and mobility

| Alternative | Strengths | Weaknesses | AI |
|---|---|---|---|
| **STCP, Metro and Andante announcements** | Official information on shuttles and extra services ([STCP](https://www.stcp.pt/pt/noticias/stcp-com-servico-dedicado-para-a-queima-das-fitas-do-porto/), [Andante](https://andante.pt/noticias/operacoes-especiais-queima-fitas-2025/)) | Published before the event, on separate channels; do not say what changed during the night | — |
| **Google Maps with STCP real time** (since 9 June 2026) | Bus location and minutes to arrival at 2,517 stops ([Renascença](https://rr.pt/noticia/pais/2026/06/09/stcp-disponibiliza-horarios-dos-autocarros-em-tempo-real-no-google-maps/473765/)) | The news does not say whether it includes the Queima's special shuttles **[H: to verify]**; does not know the venue or the temporary stops | Not identified |
| **Moovit / Anda app** | Journey planning; Andante ticketing | Do not know the event context | Not identified |
| **Ride-hailing** (Uber, Bolt) | Trip sharing and emergency button | Price and wait at peaks; not connected to the organisation | Not identified |

### Phase 5 — After the event

| Alternative | Strengths | Weaknesses | AI |
|---|---|---|---|
| **Each supplier's reports** (BOL, cashless, Zpos) | Reliable sales and consumption data | Each one shows only its own part | **Yes** (Fever) |
| **Excel and manual reports** | Flexible | Slow; incidents handled by radio leave no history; knowledge is lost when the board changes | — |

### Summary

| Phase | Situation in the Portuguese market | Our product |
|---|---|---|
| Purchase | **Well served** (BOL, Ticketline, Fever), with about a 6% fee | Out. Partner |
| Entry | Technically solved at large events; the 2022 and 2023 queues show a lack of **information** for the participant | Information only (gates, alerts). Expected wait time at the gates is one of the features the survey asks about (component C8 of the annex) |
| During: information | **Static** (Instagram, map image); existing apps cover a single event or a single federation | **In**: map, information and alerts |
| During: help and incidents | **Fragmented and unrecorded** (radio, WhatsApp) | **In**: help request and backoffice |
| Getting home | Scattered official information; STCP real time reached Google Maps in 2026 | **In**: the event's transport information |
| After the event | **Manual** | Side effect: the incident history is kept |
| AI | Exists in marketing (Eventbrite, Fever), in professional control rooms (Raven) and in Q&A assistants (42Chat). **We found no AI in the operation of academic events in Portugal** | Help-request triage, with a non-AI alternative (decided 8 October 2026; the Q&A assistant was rejected) |

---

## 3. Differentiation opportunity

**The market problem:** those who inform the participant (Instagram, festival apps) do not handle incidents; those who handle incidents (radio, professional control rooms) do not talk to the participant. In Portugal, these two halves live on separate channels and neither is recorded.

**Our proposal (repository):** a multi-event platform with two sides connected by the same data.

| Side | What it does | What it gains from being connected to the other |
|---|---|---|
| **Participant web application** (link or QR, no installation, with login) | Chooses the event; sees the map, essential information and transport; asks for help | The request reaches the team with context and a place on the map |
| **Event team backoffice** | Configures the event (map, points, transport); updates information during the night; receives, prioritises and routes incidents | What the team updates appears to the participant immediately; each team only sees its own event |

**Why it is different from the alternatives:**

1. **It connects participant and operations.** Festival apps stop at information; radio and WhatsApp stop at operations. The closest reference (Raven Controls) is for professional control rooms and has no Portuguese version.
2. **Multi-event with independent teams.** One application for several Queimas and academic weeks. Classic festival apps are one per event (Appmiral); multi-event ones (Woov, LineUpr) have no operations.
3. **The map as the common reference.** The participant finds their way with it and the team places requests on it. In the Portuguese status quo, the map is an image on social media; the festival apps that have one (NOS Alive, Rock in Rio Lisboa) cover a single festival and do not place help requests on it.
4. **No installation.** Appmiral, Woov and SafeZone require a native app. Every piece of information shows its last-update time; offline mode is a *Could* (F11).
5. **Information that changes during the night**, with the time of the last update. The status quo is static.
6. **Local:** European Portuguese and the event's special transport.
7. **AI with a non-AI alternative.** It is not a differentiator in itself (Raven and 42Chat already use it); what counts is that it is evaluated and has a fallback, as the assignment requires.

**Risks to manage:**

| Risk | Response |
|---|---|
| "We already have Instagram and WhatsApp" | Show the gain in time to help and in up-to-date information; QR entry as fast as WhatsApp |
| The supplier of the federation's current app feels replaced | Come in as a complement, through a link or QR, without requiring changes to the existing app |
| An international supplier (Appmiral, Woov) moves down-market | Operations and incidents, which their products do not cover; Portuguese context |
| Use concentrated in a few days a year | Multi-event: the same user comes back for other events |
| Large scope for the MVP | Priority: help request, map and transport (as in the repository) |

The detail per feature (alternatives, comparison matrix, prices and hypotheses to validate) is in [annex 02a](market-and-competitor-analysis-product-specific.md).

---

## 4. Main sources

[Problem and Opportunity Report](problem-opportunity-report-queima.md) · [Ticketing fees in Portugal (Observador)](https://observador.pt/especiais/bilhetes-para-concertos-mais-caros-ha-espetaculos-com-taxa-extra-para-garantir-servidores-mais-potentes/) · [BOL — Enterro da Gata 2026](https://enterrodagata.bol.pt/Comprar/Bilhetes/176491-enterro_da_gata_2026_bilhete_geral-forum_braga_gatodromo/) · [Fever — Data & Analytics](https://business.feverup.com/en/event-data-and-analytics/) · [Shotgun](https://nevent.ai/en/ticketing-platforms/shotgun/) · [Eventbrite — AI tools](https://www.eventbrite.com/blog/press/newsroom/eventbrite-introduces-ai-powered-tools-to-empower-creators-and-reimagine-event-marketing/) · [Eventbrite fees 2026](https://www.tickpick.com/blog/organizer/eventbrite-2026-fee-changes) · [APP FAP](https://www.fap.pt/noticias/app-fap-fap-no-teu-bolso) · [Queima cashless wristband (JPN)](https://www.jpn.up.pt/2023/04/14/queima-das-fitas-no-queimodromo-pagamentos-vao-ser-feitos-com-uma-pulseira/) · [FIXR](https://blog.fixr.co/reasons-to-use-fixr-for-your-society-events/) · [native](https://knowledge.native.fm/kb/en/events-ticketing-227940) · [Weezevent](https://weezevent.com/en-gb/festivals/) · [Waitwhile — Tribeca](https://waitwhile.com/case-studies/tribeca-film-festival/) · [BindePaFesta (RFM)](https://rfm.pt/atualidade/23680/esta-app-portuguesa-mostra-te-onde-sao-as-festas-da-terrinha-e-ja-esta-entre-as-mais-descarregadas) · [Appmiral — pricing](https://appmiral.com/pricing) · [Woov (App Store)](https://apps.apple.com/nl/app/woov-your-festival-companion/id1580044680) · [LineUpr — pricing](https://lineupr.com/en/pricing/) · [42Chat FestivalBot](https://www.42chat.com/festivalbot) · [Zpos](https://en.zpos.pt/festivals/) · [SumUp PT](https://pt.mobiletransaction.org/sumup-leitor-cartoes/) · [ifthenpay](https://shelf.pt/blog/ifthenpay-review) · [Zello](https://zello.com/pricing/) · [Raven Controls](https://ravencontrols.com/) · [CriticalArc SafeZone](https://criticalarc.com/safezone-features/) · [Rosterfy](https://www.rosterfy.com/pricing/) · [STCP — Queima service](https://www.stcp.pt/pt/noticias/stcp-com-servico-dedicado-para-a-queima-das-fitas-do-porto/) · [STCP real time on Google Maps (Renascença, 2026)](https://rr.pt/noticia/pais/2026/06/09/stcp-disponibiliza-horarios-dos-autocarros-em-tempo-real-no-google-maps/473765/).

Remaining sources in [annex 02a](market-and-competitor-analysis-product-specific.md#9-sources), section 9.

---

## 5. How AI was used in this research

### 5.1 Tool and role

| Item | Description |
|---|---|
| Tool | **Claude** (Anthropic), in a project called "LABDSOF" that keeps the working documents, with web search and page reading enabled |
| AI's role | **Research and writing assistant.** It searched for alternatives, read supplier pages and the press, summarised and organised them into tables |
| Human role | The team member who wrote this analysis set the scope and the questions in each round, decided what went in and out, and aligned the result with the group's decisions |
| What the AI did **not** do | It did no user research, tested no products and made no product decisions |

### 5.2 Process, by iteration

| Date | Version | What was asked | What the AI did |
|---|---|---|---|
| 2026-09-24 | v1.x | Competitors and non-digital alternatives for security coordination, along the assignment's dimensions | Three research rounds (8 → 20 → 29 profiles), with a 15-capability matrix. |
| 2026-10-01 | v2.0 | Widen it to academic event management as a whole | Research per stage of the event lifecycle: 10 families, more than 50 products, public prices |
| 2026-10-01 | v3.0 | A short version, by phase, with strengths, weaknesses and AI use, Portuguese market only | Condensed the previous research into 2–3 pages |
| 2026-10-06 | **v4.0** (this document) | Iterate the analysis based on what is already in the repository | Read the Problem and Opportunity Report, the survey script and the repository's commit history; reorganised the phases; did new research on participant information, queues and getting home (sources P1–P10 of annex `02a`) |

### 5.3 Rules followed to keep the evidence reliable

| Rule | Where it shows in the document |
|---|---|
| Every fact has a linked source | Links in the tables and in section 4 |
| Absence of evidence is not evidence of absence | "Not identified" in the AI column |
| Competitors' features are commercial claims | Warning in the legend; no product was tested |
| No user data was generated by AI | This document has no survey or interview results |
| AI output is not presented as fact | The conclusions in section 3 are our reading of the tables, not a measured result |

### 5.4 Limitations of the AI use

| Limitation | Possible effect | Mitigation |
|---|---|---|
| The AI reads pages through an automatic summary | It may miss a detail or a price | Open the source before quoting a number in the final report |
| Prices and features change | Data may be out of date | Most source lists of the annex give the date they were checked; the list in §4 and annex §9.2 do not |
| Some searches returned nothing useful (for example, official apps of Queima de Coimbra and Enterro da Gata) | Gaps in coverage | Recorded as "not found" |
| AI can make mistakes or invent things | Risk of unfounded claims | Only claims with a link were kept, and the rules of §5.3 were applied by hand |
