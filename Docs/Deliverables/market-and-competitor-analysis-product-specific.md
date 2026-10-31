# Annex 02a — Product-specific market analysis

> LABDSOF 2026/27 · Sprint A
> **What this document is:** the market analysis **of each thing we want to include in the project**. The general document ([`market-and-competitor-analysis.md`](market-and-competitor-analysis.md)) covers the whole market, phase by phase; this annex goes down to feature level.
> **Basis:** what is in the repository on 2026-10-06 (`main`, commit `442ecc3`): `Docs/Deliverables/problem-opportunity-report-queima.md` (problem, vision, MVP, assumptions A1–A9) and `Docs/Research/inquerito-estudantes-google-forms.gs` (student survey, questions 1–11).
> **Assignment, Market section:** competitors and non-digital alternatives: users, features, strengths/weaknesses, business model, UX, privacy → value proposition.
>
> **Evidence legend**
> - No mark = fact with a source. IDs (all in section 9): `P#` = sources from 2026-10-06; `M#` = security, operations and app sources; `N#` = ticketing, payments and market sources; `F#` = reference case (Queima do Porto); `R:` = evidence cited in the repository's Problem and Opportunity Report.
> - Competitors' features are **supplier claims** (websites, prices, app stores). We did not test any product.
> - In the matrix, ❌ means "not found in the sources", not "does not exist".
>
> **Decisions taken since 6 October 2026** (added 31 October 2026, #162). This analysis was written on 6 October; the team decided later, and where the text below differs, these decisions win:
> - **Transport:** live positions of the city buses near the venue come from the Porto Digital urban platform, plus the event shuttles and temporary stops entered by the team ([ADR-008](../Technical/adr/0008-transport-data-source.md), F9 in [`features.md`](../Product/features.md)). A link to Google Maps is no longer the plan for regular lines.
> - **AI:** help-request triage was chosen on 8 October 2026; the participant Q&A assistant was rejected (a wrong transport answer at night is a safety risk; [`features.md` §5](../Product/features.md#5-ai-assisted-workflow-help-request-triage)).
> - **Weak network:** offline mode (cached map and information) is F11, a *Could* since 9 October 2026; the time of the last update on each item stays in the MVP (SC7, [product vision](../Product/product-vision.md)). The rows C5, C9 and C10 below are corrected accordingly.

---

## 0. Summary

| Question | Short answer |
|---|---|
| What do we want to build? | A **multi-event** platform with two sides: a **participant web application** (map, information, transport, help request) and a **backoffice for each event's team** (configure the event, update information, handle incidents) |
| Is there a product that does all this for academic events in Portugal? | **We did not find one.** Each component exists in isolation: festival apps provide a map and alerts; professional control rooms handle incidents; Google Maps provides transport; WhatsApp receives help requests |
| Who comes closest? | **Appmiral** (map, alerts and SOS in a festival app) on the participant side and **Raven Controls** (incidents with public reporting by QR) on the team side. Neither joins the two sides for academic teams, in Portuguese and without installation |
| What is already common and does not set us apart? | Interactive map, timetables, push alerts, SOS button, incident dashboard, real-time transport |
| What sets us apart? | (1) the two sides connected by the **map**; (2) **multi-event with per-event permissions**; (3) **no installation**, with the last-update time on every item; (4) **the event's own transport** maintained by the team; (5) Portuguese context |
| Biggest threat | The free status quo: **Instagram + WhatsApp line + radio** |

---

## 1. What we want to include (read from the repository)

| # | Component | Status in the repository | Origin |
|---|---|---|---|
| **C1** | Multi-event platform: each event with its own owner, team and permissions | **MVP** | Report, "Product vision" and "MVP definition" |
| **C2** | Participant web application (PWA), opened by link or QR, no mandatory installation, with mandatory login (decided on 9 October 2026) | **MVP** | Report; survey, questions 7 and 9 |
| **C3** | Venue map uploaded and configured by the team (entrances, stages, toilets, first aid, stops) | **MVP** | Report, "Opportunity"; survey, question 8 |
| **C4** | Essential event information, updated by the team during the night (alerts) | **MVP** | Report, root cause 2; survey, question 8 |
| **C5** | Transport and getting-home information | **MVP** | Report; survey, questions 3 and 8 |
| **C6** | Help request by the participant, with the place marked on the map | **MVP** | Report; survey, questions 5, 8 and 10 |
| **C7** | Backoffice: receive, prioritise and route incidents | **MVP** | Report, "Main user journeys" |
| **C8** | Expected waiting time at the entrances | **Candidate** (survey option) | Survey, question 8. The physical capacity of the gates is out of scope |
| **C9** | AI: support for prioritising help requests (triage); the participant Q&A assistant was rejected on 8 October 2026 | **MVP** (triage; the assignment requires at least one AI workflow) | Survey, question 8; report, assumption A7 |
| **C10** | Work on a weak network (cache, degraded mode) and show the time of the last update | **MVP:** the last-update time (SC7). **Could:** the offline cache (F11, since 9 October 2026) | Report, assumption A8 and technical risks |
| **C11** | Electronic ticket in the application | **Candidate** (survey option); ticketing is a partner, not part of the MVP | Survey, question 8; report, stakeholder map |

**Out of scope (repository):** physical capacity of the entrances; heavy integrations; advanced crowd prediction; full automation.

---

## 2. Analysis by component

Each profile answers: who already does this, with which strengths and weaknesses, whether they use AI, and what it means for us.

### C1 — Multi-event platform with per-event teams and permissions

| Alternative | What it does | Strengths | Weaknesses for us | AI |
|---|---|---|---|---|
| **One app per event** (Appmiral; NOS Alive and Rock in Rio Lisboa apps) | An app branded for each festival (M2, M8, M9) | Own brand; mature | Each event pays for and maintains its own; the student installs one per festival | Not identified |
| **Woov** | One app for hundreds of festivals; the user chooses the event (M5, P2) | Proves the "one app, many events" model works; more than 1 M users per season | No operations; the organiser does not handle incidents there | Chat moderation |
| **FEST App** | Multi-festival "container" with a web dashboard for the organiser; free entry plan (P10) | Self-service; cheap | Distributed through app stores; information only | Not identified |
| **LineUpr** | Yearly "Flex" subscription: a quota of events and participants to spread over the year, up to 60% cheaper than buying event by event (P3) | Pricing model designed for those with several events a year | Conferences; no incidents | Not identified |
| **BindePaFesta** (PT, June 2026) | Map of village and popular festivals across the country; organisers manage their own content (P1) | Shows there is demand in Portugal for an app shared by many festivals; reached the top of the Portuguese App Store | Festival discovery, not event operations; popular-festival audience | Not identified |
| **APP FAP** | Official app of the Porto federation (M10) | Already has Porto's student base | A single federation; each federation would have to build its own | Not identified |
| **MSL + native / FIXR** (United Kingdom) | Platforms for students' unions with per-committee permissions (N15, N16, N17) | Prove the academic segment buys its own platforms | Not present in Portugal; focused on sales | Not identified |
| **Non-digital:** each organisation handles its event with its own tools | — | Full independence | Nothing is reused between events or between boards | — |

**What we learned:** multi-event is **not new** on the participant side (Woov, FEST, BindePaFesta). What is new is that the **backoffice** is also multi-event, with **isolation between teams**. That is why the repository's report treats permissions across events as a technical risk: it is the point that has to be demonstrated.

### C2 — Participant web application, no installation

| Alternative | Entry channel | Strengths | Weaknesses for us |
|---|---|---|---|
| **Native apps** (Appmiral, Woov, FEST, SafeZone, APP FAP) | Installation from the app store | Reliable push; work better offline | Friction of installing yet another app |
| **LineUpr** | PWA, no installation (P3) | Opens by link; 20 languages | Push only on the most expensive plan |
| **Raven Eye** | The public reports through **WhatsApp with a QR code** (M13) | Zero installation; a channel everyone has | Exposes the phone number |
| **42Chat FestivalBot** | Assistant by **SMS or web** (P4) | No installation | Questions and answers only |
| **FAP's WhatsApp/QR line** | QR at the venue (F4, F5) | Already in use | No structure or metrics |
| **Non-digital:** signs, signage, staff | In person | Does not depend on the phone | In 2022 and 2023 there were complaints about "lack of information and signage" (R: Renascença, Público) |

**What we learned:** the professional market has already gone after the participant **without asking them to install anything** (WhatsApp, SMS, PWA). The link or QR is the right choice; the cost is losing native push, so alerts (C4) must work with the application open and, if possible, with web notifications. Survey question 9 measures exactly this barrier ("having to install yet another app").

### C3 — Configurable venue map

| Alternative | What it does | Model | Strengths | Weaknesses for us |
|---|---|---|---|---|
| **Map image on Instagram or the website** (status quo) | Static image | Free | Zero cost | Not updated; cannot be used to point to a place; not connected to requests |
| **Appmiral** | Interactive map (Pro plan only, €935/month) (M2) | Yearly SaaS | Mature; find friends | Price; one app per festival |
| **Woov** | Festival map; 3D maps (M5, P2) | B2B, price not public | Rich experience | Collects the user's exact location (P2) |
| **MapsPeople (MapsIndoors) / Mappedin** | Indoor maps with turn-by-turn routes, point search and real-time updates; customers such as Fira Barcelona (P6) | Enterprise, price not public | Professional quality | Designed for trade fairs and buildings; does not say whether it serves temporary outdoor venues |
| **Iventis** | Geo-referenced venue planning for the organisation (M42) | SaaS, on request | Capacity and evacuation plans | For planning beforehand, not for the participant during the event |
| **Google Maps / OpenStreetMap** | City map | Free | Universal | Do not know the inside of a temporary venue |
| **what3words** (used in Controlled Events' control room) | Gives a name to each 3 m square (M18) | — | Precise location by voice | Yet another app; does not show the venue |

**What we learned:** an interactive map is **common** in festival apps. What we did not find was the map used as a **shared reference between the participant and the team**: the participant marks on the map where they are, and the team sees the incident at that point. The repository's simple solution (the team uploads an image or PDF and marks points) avoids the cost of a map supplier and addresses the fact that phone location is inaccurate in a crowded venue.

### C4 — Essential information and alerts during the night

| Alternative | What it does | Strengths | Weaknesses for us |
|---|---|---|---|
| **Instagram (stories) and website** | Organisation's announcements | Reach | Does not reliably reach those at the venue; mixed with all other content |
| **Festival app push** (Appmiral, WMT Digital, Woov, FEST) | General and per-zone alerts (geofencing in WMT) (M2, M7, P10) | Immediate | Requires an installed app |
| **Aloompa Presence** (Crowd Connected) | Live heat maps and push segmented by venue zone, with consent-based location (P5) | Alert only those in a zone | Native app; the organisation must provide the map in CAD |
| **Digital screens, megaphone and staff** | On-site announcements (P8) | Do not depend on the phone | Limited reach; no record |

**What we learned:** broadcasting alerts is a **commodity**. What we add is **reliability**: each piece of information with the time of its last update (the report identifies the risk of "outdated information making things worse") and a single source for what changes during the night.

### C5 — Transport and getting home

| Alternative | What it does | Strengths | Weaknesses for us |
|---|---|---|---|
| **STCP, Metro and Andante announcements** | Announce shuttles and extra services before the event (R: Andante, STCP) | Official | Static; on separate channels |
| **Google Maps with STCP real time** (since 9 June 2026) | Bus location and time to arrival at 2,517 stops (P9) | Free; already installed | The news does not say whether the Queima's special shuttles are included **[H: check in the GTFS]** |
| **Moovit, Anda app** | Planning; Andante ticketing (M36) | Well known | Do not know the event |
| **Festival apps** (Rock in Rio Lisboa) | Static shuttle information (M9) | Same place as everything else | Does not change during the night |
| **SafeZone** | Campus shuttle tracking (M22) | Real time | Campus, not events |
| **Puntos Violeta** (Spain) | Groups to "go home together" (M28) | Safe journey home | Another country; another focus |
| **Ride-hailing** (Uber, Bolt) | Trip sharing (M35) | Already installed | Price at peaks |

**What we learned:** since June 2026, real time for STCP's regular lines is **already solved** by Google Maps; there is no point in rebuilding it. Our part is what mobility apps do not know: **temporary stops on the map, the event shuttle's timetable and what changed tonight**, entered by the team (assumption A6 of the report). For the rest, a link to Google Maps is enough. *Update 31 October 2026:* the team later chose to show live bus positions near the venue from the Porto Digital platform ([ADR-008](../Technical/adr/0008-transport-data-source.md)); temporary stops and the shuttle are still entered by the team.

### C6 — Help request with the place on the map

| Alternative | Users | UX | Privacy | Strengths | Weaknesses for us | AI |
|---|---|---|---|---|---|---|
| **FAP's WhatsApp/QR line** | Participant → operator | Familiar, no installation | Exposes the number; data at Meta | Already exists | No category, place or status | — |
| **Finding a security guard, volunteer or the medical post** | Participant | In person | Good | Human | Only serves those who see them and can reach them | — |
| **Appmiral SOS** | Participant → festival security | Button in the app | Real-time location | "Hundreds of alerts" handled (M1) | Native app; no version for volunteer teams | No |
| **SafeZone** | Student → campus security | Three buttons: emergency, first aid, help | Location **only at the moment of the alert** (M22) | Good reference practice | Campus only | No |
| **Raven Eye** | Public → control room | WhatsApp + QR | Number exposed | Integrates with the control room (M13) | Enterprise; no PT | Yes, in the control room |
| **AlertCops** (Spain) | Citizen → police | App | Mandatory identification (M27) | Official | Does not accept anonymity | No |

**What we learned:** the help button **exists** in festival and campus apps. Three patterns to follow: (1) entry without installation, like Raven Eye; (2) location only at the moment of the request, like SafeZone; (3) make clear it is not an emergency service, as the report already says. What is new is the participant **marking the place on the map** instead of relying on GPS. Survey question 10 measures the acceptance of this.

### C7 — Incident backoffice

| Alternative | Users | Features | Model | Strengths | Weaknesses for us | AI |
|---|---|---|---|---|---|---|
| **Radio + control room with manual log** | Coordination, security | Voice per channel | €20–25 per radio per week (M45) | Works without a network | No structured history; place described by voice | — |
| **WhatsApp groups and spreadsheets** | Team | Messages | Free | Everyone knows how to use them | No owner or status per case | — |
| **Raven Controls** | Professional control room | Log, maps, checklists, group notification | SaaS, on request (M13) | Used at COP26 and in a partnership for the FIFA World Cup | For professional control rooms; no PT | **Yes**: suggests category, priority and procedure |
| **Momentus WeTrack** | Venues | Incidents with severity and response plans (M16) | Enterprise | More than 4,000 venues | Enterprise | Not identified |
| **Controlled Events** | Civic events | Control room with map and weather; **per-event** licence (M18) | Per event or yearly | Pricing model useful for seasonal events | Not connected to the participant | Not identified |
| **Appmiral Emergency Reporting** | Organiser | Brings reporting channels into one view (M3) | Included in the app | Same supplier as the app | Little public information | Not identified |
| **Control room software** (market) | Stadiums, cities | — | US$30k to more than US$1M (M19, a supplier's source) | — | Out of reach for a federation | — |

**What we learned:** the incident → priority → routing cycle is **proven** in the professional market. What we did not find is a version for **academic teams** (organisation, prevention committee, volunteers), in Portuguese, connected to the participant application and at a federation's price.

### C8 — Waiting time at the entrances *(candidate)*

| Alternative | What it does | Strengths | Weaknesses for us |
|---|---|---|---|
| **No information** (status quo) | The participant only discovers the queue when they arrive | — | Queues of more than 500 m and more than 3 h in 2022 and 2023 (R: Renascença, Público) |
| **Appmiral Venue Occupancy** | Red, orange or green status per zone, **updated by hand** by staff (M4) | Simple; needs no sensors | Depends on someone updating it |
| **Festival operations dashboards** | Combine gate scans, queue length and shuttles; alerts when the wait passes a threshold, with push and signage (P8, a supplier's blog) | Practice described in the sector | Needs ticketing data |
| **Waitwhile** | Virtual queue by link or SMS with estimated time (P7) | More than 30,000 people managed at Tribeca | Not for the gates of a venue |
| **Queue-it** | Virtual queue for online sales, from US$1,499 per event (N34) | Handles purchase peaks | For the sale, not for the gate |
| **Crowdscan** | Measures density with sensors, no cameras (M43) | Objective data | Hardware and cost per event |
| **Estimate from the app** | A study shows a correlation above 0.8 between the density estimated by a festival app and the one measured on video (R: arXiv 1306.3133) | Technically feasible | Needs many users with location on |

**What we learned:** the simple version (the team sets the status of each gate by hand, as in Appmiral) fits in the MVP and is consistent with assumption A6. The automatic version depends on ticketing or location data, which means heavy integration and raises privacy questions. It only makes sense to go ahead if the survey (question 8) shows demand.

### C9 — AI *(candidate; the assignment requires an AI workflow and a fallback)*

| Use | Who already does it | Strengths | Weaknesses and risks | Non-AI alternative |
|---|---|---|---|---|
| **Participant Q&A assistant** | **42Chat FestivalBot**: SMS and web, more than 60 topics, tiers up to 50,000 users (P4) | Answers immediately; relieves staff | The supplier does not publish accuracy or what it does when it does not know; a wrong transport answer can put someone at risk | FAQ page and search over the event information |
| **Incident triage** | **Raven Controls** suggests category, priority and procedure to an operator who decides (M13) | Validates the "AI suggests, the person decides" pattern | No PT; professional operator | Category chosen by the participant + order of arrival |
| **Guided interview for sensitive reports** | **Spot** (M32) | Helps describe what happened | Outside the moment of the event | Short form |
| **Content moderation** | **Woov**, with 95% less human work (M6) | Scales | Does not apply to our MVP | — |
| **Marketing and demand forecasting** | Eventbrite, Fever (N1; general document) | — | Outside our scope | — |

**What we learned:** AI **does not set the product apart by itself**, because both candidate uses already exist. What counts is meeting the assignment better than suppliers show publicly: the assistant would answer **only from the information the team uploaded** (it was rejected on 8 October 2026; triage was kept); the AI never lowers a request's priority without a person; evaluation with a set of cases and comparison with the non-AI alternative (assumption A7).

### C10 — Weak network and degraded mode

| Alternative | How it deals with the network | Source |
|---|---|---|
| Radio | Does not depend on the mobile network | M45 |
| Woov | RFID cashless working offline | M5 |
| Zpos | POS offline for up to 5 days | N24 |
| Weezevent | Offline entry terminals | N5 |
| Zello, WhatsApp | Depend on the network | M40 |
| Festival apps | We found no public information on offline mode | — |

**What we learned:** only professional payment and entry systems advertise offline operation. Congestion in crowds is documented (Sónar is a case study, M46). Caching the map and the latest information and saying "data from HH:MM" is an assignment requirement that almost no participant-side alternative shows.

### C11 — Electronic ticket in the application *(candidate; ticketing = partner)*

| Alternative | Situation |
|---|---|
| **BOL, Fnac, APP FAP** | Already sell the tickets for the Queimas of Porto and Coimbra and for the Enterro da Gata (N8–N11, M10) |
| **Cashless wristband** | In Porto, entry is by wristband only (N22) |

**What we learned:** selling or storing tickets would mean competing with suppliers already inside the customer, in a well-served phase. If the survey shows demand, the lowest-risk answer is a **link to the ticket** on the ticketing platform, not our own ticketing.

---

## 3. Comparison matrix (components of our product)

Legend: ✅ confirmed in the source · ◐ partial · ❌ not found in the sources · ? no information · — not applicable. The last row is the MVP **target**, not a fact.

| Alternative | C1 Multi-event | C2 No install | C3 Map | C4 Alerts | C5 Transport | C6 Ask for help | C7 Incidents | C8 Wait time | C9 AI | C10 Weak network | PT-PT |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Instagram + website + map image | — | ✅ | ◐ static | ◐ | ◐ static | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |
| FAP's WhatsApp/QR line | ❌ | ✅ | ❌ | ❌ | ❌ | ✅ | ◐ manual | ❌ | ❌ | ◐ | ✅ |
| Radio + control room | — | — | ❌ | ❌ | ❌ | ❌ | ◐ verbal | ❌ | ❌ | ✅ | ✅ |
| APP FAP | ◐ FAP events | ❌ | ❌ | ◐ | ❌ | ❌ | ❌ | ❌ | ❌ | ? | ✅ |
| Google Maps (STCP real time) | — | ✅ | ❌ | ❌ | ✅ regular lines | ❌ | ❌ | ❌ | ? | ◐ | ✅ |
| BindePaFesta | ✅ | ◐ app and website | ◐ festival map | ✅ | ◐ directions | ❌ | ❌ | ❌ | ❌ | ? | ✅ |
| Appmiral | ❌ one per festival | ❌ | ✅ (Pro) | ✅ | ? | ✅ SOS | ◐ | ◐ manual | ❌ | ? | ? |
| Woov | ✅ | ❌ | ✅ | ✅ | ? | ❌ | ❌ | ❌ | ◐ moderation | ◐ cashless | ✅ interface |
| LineUpr | ✅ | ✅ PWA | ◐ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ? | ? |
| FEST App | ✅ | ❌ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ? | ? |
| 42Chat FestivalBot | ? | ✅ SMS/web | ❌ | ❌ | ◐ answers questions | ❌ | ❌ | ❌ | ✅ assistant | ? | ❌ |
| Raven Controls (+ Eye) | ? | ✅ WhatsApp | ✅ for the control room | ❌ | ❌ | ✅ | ✅ | ❌ | ✅ triage | ? | ❌ |
| SafeZone | ❌ campus | ❌ | ◐ | ✅ | ◐ shuttles | ✅ | ✅ | ❌ | ❌ | ? | ? |
| Waitwhile | ? | ✅ | ❌ | ◐ SMS | ❌ | ❌ | ❌ | ✅ virtual queue | ❌ | ? | ? |
| **Us (MVP target)** | ✅ | ✅ | ✅ | ✅ | ✅ the event's and live buses | ✅ | ✅ | ◐ candidate | ◐ triage with fallback | ◐ last-update time; offline is a Could | ✅ |

**Reading:**
1. **No alternative covers C3 + C5 + C6 + C7 at the same time**, which is the core of the MVP in the repository (map, transport, help request, incidents).
2. Those with **C6 and C7** (Raven, SafeZone) have no Portuguese version; Raven has no participant information side, and SafeZone's alerts are for a campus, not an event. Those with **C3 and C4** (Appmiral, Woov, FEST) do not have an incident backoffice.
3. **C2 (no install)** appears mostly in messaging channels (WhatsApp, SMS); the most feature-rich festival apps are native.
4. The Portuguese status quo covers almost everything **partially and on different channels**. That is the real competitor.

---

## 4. Closest competitors along the assignment's dimensions

| Dimension | Appmiral (CM.com) | Raven Controls | Woov | Queima do Porto status quo |
|---|---|---|---|---|
| **Users** | Participant; festival organiser | Control room; staff; public through Raven Eye | Participant; organiser | Participant; organisation; volunteers; security |
| **Relevant features** | Map, timetables, push, SOS with location, manual occupancy | Incident log, maps, checklists, triage AI, reporting by WhatsApp/QR | Map, timetables, friends, chat | Instagram and website, APP FAP, WhatsApp/QR line, radio, colour-coded volunteers |
| **Strengths** | Mature; SOS in real use | Full cycle; credibility at large events | One app for many festivals; PT interface | Free; familiar; human |
| **Weaknesses** | One app per festival; marketing DNA | Enterprise; no PT; for professionals | No help or operations | Static; no record; separate channels |
| **Business model** | €375–935/month, billed yearly | Tiered SaaS, price on request | B2B, price not public | No software cost |
| **UX** | Native app branded for the festival | Operator console; public through WhatsApp | Social native app | Several channels; none shows the request's status |
| **Privacy** | Real-time location in SOS | Phone number exposed when reporting | Exact location and contacts tied to the user (P2) | Number exposed on WhatsApp; radio on an open channel |

**Us, along the same dimensions (target):** participant and event team with the same weight · map, information, transport, help request and incidents · web application by link or QR · place marked by the participant on the map, shared only with the request, with short retention.

---

## 5. Differentiation by component

| Component | Common in the market? | Where we stand out | How it is proven |
|---|---|---|---|
| C1 Multi-event | Yes, on the participant side | Multi-event backoffice with isolation between teams | Automated permission tests; a second event configured without development |
| C2 No installation | Partly (messaging channels) | Full web application, not just a reporting channel | Time between scanning the QR and seeing the map |
| C3 Map | Yes | Map uploaded by the team and used by both sides | Percentage of requests with a marked place |
| C4 Alerts | Yes | Time of the last update on every piece of information | Percentage of content with a visible time |
| C5 Transport | Yes, for regular lines | The event's shuttles and stops, plus live buses near the venue (ADR-008) | Comparison with the official announcements |
| C6 Help request | Yes, in native apps | No installation; place on the map | Time until the request is submitted |
| C7 Incidents | Yes, in professional control rooms | For academic teams, in PT, connected to the participant | Time between request and routing |
| C8 Wait time | Rare | Manual version per gate | Only if the survey shows demand |
| C9 AI | Yes | Published evaluation and fallback | Accuracy against the non-AI alternative |
| C10 Weak network | Rare on the participant side | Cache and "data from HH:MM" | Demonstration of the degraded mode |

**In short:** almost every component exists in some product. The differentiation lies in the **combination** (the two sides connected by the map), the **segment** (Portuguese academic event teams) and the **delivery model** (multi-event, no installation, resilient to a weak network).

---

## 6. Business model: relevant price anchors

| Reference | Price | Source |
|---|---|---|
| Appmiral (festival app) | €375 / 625 / 935 per month, billed yearly | M2 |
| LineUpr (event app as a PWA) | Three tiers; yearly subscription for several events up to 60% cheaper; price by calculator | P3 |
| FEST App | Free entry plan; the company itself places festival tools between US$1,000 and US$25,000 and custom development between US$40,000 and US$400,000 | P10 (a supplier's blog) |
| 42Chat FestivalBot | Three tiers, up to 5,000, 15,000 and 50,000 users; price not public | P4 |
| Controlled Events | Per-event or yearly licence; price not public | M18 |
| Control room software | US$30k to more than US$1M | M19 (a supplier's source) |
| Radio rental (PT) | €20–25 per radio per week | M45 |
| Zello | US$6.80–12.75 per user per month | M40 |
| Queue-it | From US$1,499 per event | N34 |

---

## 7. Threats

| Threat | Probability | Impact | Response |
|---|---|---|---|
| The organisation keeps using Instagram, WhatsApp and radio | High | High | Measure and show time to help and up-to-date information in a pilot |
| The supplier of the federation's app adds a map and help | Medium | High | Come in as a complement by link; multi-event value |
| Appmiral or Woov win academic customers in Portugal | Low to medium | High | Operations and incidents; local context; no installation |
| Google Maps starts covering the event's shuttles | Medium | Low | Use the link; keep only the stops on the map |
| Participants do not open the application during the night (assumption A2) | Medium | Very high | Survey, questions 7 and 9; QR on the ticket and at the venue |

---

## 8. What still needs validating, and where

| Hypothesis in this analysis | Where it is validated | Linked to |
|---|---|---|
| Students look for information on Instagram and in WhatsApp groups today | Survey, question 4 | C4 |
| Map, transport and the help button are the most useful features | Survey, question 8 | C3, C5, C6 |
| Installing an app is a barrier | Survey, question 9 | C2 |
| Students accept marking their place on the map instead of sharing GPS | Survey, question 10 | C6 |
| There is demand for waiting time, an assistant and a ticket in the application | Survey, question 8 | C8, C9, C11 |
| Incidents are coordinated by radio and phone, with no history | Interview with the security volunteer | C7 |
| The special shuttles do not appear on Google Maps | Technical check of STCP's GTFS | C5 |
| Organisations would pay a yearly licence | Conversation with organisations | Section 6 |

Until then, these rows are hypotheses and do not go into the report as evidence.

---

## 9. Sources

Only the sources cited in this annex are listed. The IDs come from the original research rounds, so the numbering has gaps.

### 9.1 New sources (checked on 2026-10-06)

| ID | Source | Type |
|---|---|---|
| P1 | [RFM — Portuguese app shows where the festivals are (BindePaFesta), 2026-08-27](https://rfm.pt/atualidade/23680/esta-app-portuguesa-mostra-te-onde-sao-as-festas-da-terrinha-e-ja-esta-entre-as-mais-descarregadas) | Press |
| P2 | [App Store — Woov: Your Festival Companion](https://apps.apple.com/nl/app/woov-your-festival-companion/id1580044680) | App store |
| P3 | [LineUpr — Pricing](https://lineupr.com/en/pricing/) | Supplier |
| P4 | [42Chat — FestivalBot](https://www.42chat.com/festivalbot) | Supplier |
| P5 | [Aloompa — Presence (powered by Crowd Connected)](https://support.aloompa.com/article/400-presence-crowdconnected) | Supplier |
| P6 | [MapsPeople — Interactive indoor maps for events](https://mapspeople.com/industries/events) | Supplier |
| P7 | [Waitwhile — Tribeca Film Festival case study](https://waitwhile.com/case-studies/tribeca-film-festival/) | Supplier |
| P8 | [Ticket Fairy — Real-time festival ops dashboards](https://www.ticketfairy.com/blog/real-time-festival-ops-dashboards-feeder-city-arrivals-and-queue-health.md) | Supplier blog |
| P9 | [Renascença — STCP makes real-time timetables available on Google Maps, 2026-06-09](https://rr.pt/noticia/pais/2026/06/09/stcp-disponibiliza-horarios-dos-autocarros-em-tempo-real-no-google-maps/473765/) | Press |
| P10 | [FEST — How much does a festival app cost in 2026](https://festapp.io/blog/how-much-festival-app-cost-2026) | Supplier blog |

### 9.2 Repository evidence (Problem and Opportunity Report, section 4)

| ID | Source |
|---|---|
| R: Renascença | [Queues for the Queima do Porto (2022)](https://rr.pt/noticia/pais/2022/05/05/filas-para-a-queima-das-fitas-do-porto-achei-que-ia-colapsar-a-ser-esmagada-e-quase-a-sufocar/283010/) |
| R: Público | [Chaotic first night at the Queima do Porto (2023)](https://www.publico.pt/2023/05/08/p3/noticia/primeira-noite-caotica-queima-fitas-porto-voltou-normal-2048919) |
| R: Andante, STCP | [Special operations for the Queima 2025](https://andante.pt/noticias/operacoes-especiais-queima-fitas-2025/) · [STCP with a dedicated service](https://www.stcp.pt/pt/noticias/stcp-com-servico-dedicado-para-a-queima-das-fitas-do-porto/) |
| R: arXiv | [Crowd density estimation from a festival app](https://arxiv.org/pdf/1306.3133) |

### 9.3 Security, operations and event apps (checked on 2026-09-24)

| ID | Source |
|---|---|
| M1 | [CM.com — Festival App Introduces SOS Alert (2024-08-05)](https://www.cm.com/press/festival-app-introduces-sos-notification/) |
| M2 | [Appmiral — Pricing](https://appmiral.com/pricing) |
| M3 | [Appmiral Knowledge Center — Emergency Reporting](https://help.appmiral.com/emergency) |
| M4 | [Appmiral — Venue Occupancy Module](https://help.appmiral.com/app-features-explained/crowd-control-with-the-venue-occupancy-module) |
| M5 | [Woov — official website](https://woov.com/) |
| M6 | [Lasso Moderation — Woov case study](https://www.lassomoderation.com/case-studies/woov-keeping-festival-communities-safe-at-scale) |
| M7 | [WMT Digital (formerly Aloompa) — Live Events](https://wmt.digital/platforms/live-events) |
| M8 | [PCGuia — App of the Day: NOS Alive](https://www.pcguia.pt/2026/07/app-do-dia-nos-alive-2024/) |
| M9 | [PCGuia — App of the Day: Rock in Rio Lisboa](https://www.pcguia.pt/2026/06/app-do-dia-rock-in-rio-lisboa-2/) |
| M10 | [FAP — APP FAP, FAP in your pocket](https://www.fap.pt/noticias/app-fap-fap-no-teu-bolso) |
| M13 | [Raven Controls — Total Event Control](https://ravencontrols.com/) |
| M16 | [Momentus — WeTrack Incident Management](https://gomomentus.com/incident-management-software) |
| M18 | [Controlled Events — Incident Management Software](https://controlledevents.com/incident-management-software/) |
| M19 | [SIRV — Best control room software (2024; a supplier's source)](https://getsirv.com/2024/04/best-control-room-software/) |
| M22 | [CriticalArc — SafeZone Features](https://criticalarc.com/safezone-features/) |
| M27 | [Segurma — What is AlertCops?](https://segurma.com/que-es-alertcops/) |
| M28 | [Puntos Violeta — official app](https://www.puntosvioleta.com/index.html) |
| M32 | [Talk to Spot — FAQ](https://www.talktospot.com/faq) |
| M35 | [Uber — Rider Safety Features](https://www.uber.com/gb/en/ride/safety/rider-safety-features/) |
| M36 | [Andante — Anda App](https://andante.pt/en/purchase/anda-app/) |
| M40 | [Zello — Pricing](https://zello.com/pricing/) |
| M42 | [Iventis — Pricing & Plans](https://www.iventis.com/pricing/) |
| M43 | [Crowdscan — Events](https://www.crowdscan.be/markets/events) |
| M45 | [Rádio Silva — Radio rental](https://radiosilva.com/radios/) |
| M46 | [MDPI Sensors — Opportunistic network algorithms in music festival scenarios (2021)](https://www.mdpi.com/1424-8220/21/10/3315) |

### 9.4 Ticketing, payments and market (checked on 2026-10-01)

| ID | Source |
|---|---|
| N1 | [TickPick — Eventbrite's 2026 changes: what organizers pay now](https://www.tickpick.com/blog/organizer/eventbrite-2026-fee-changes) |
| N5 | [Weezevent — Festivals](https://weezevent.com/en-gb/festivals/) |
| N8 | [BOL — Enterro da Gata 2026 (shop branded with the event)](https://enterrodagata.bol.pt/Comprar/Bilhetes/176491-enterro_da_gata_2026_bilhete_geral-forum_braga_gatodromo/) |
| N9 | [Bilheteira Fnac — Enterro da Gata 2026](https://bilheteira.fnac.pt/Evento-556974/ENTERRO-DA-GATA-2026-) · [Bilheteira Fnac — Queima das Fitas do Porto 2026](https://bilheteira.fnac.pt/Evento-555445/NOITES-DA-QUEIMA-DAS-FITAS-DO-PORTO-2026-) |
| N10 | [BOL — General Pass, Queima das Fitas do Porto 2026](https://www.bol.pt/Comprar/Bilhetes/169689-passe_geral_noites_da_queima_das_fitas_do_porto_2026-queimodromo/) |
| N11 | [BOL — Queima das Fitas Coimbra 2026](https://www.bol.pt/Comprar/Bilhetes/168549-queima_das_fitas_coimbra_2026_bilhete_geral-praca_da_cancao/) |
| N15 | [FIXR — 10 reasons to use FIXR for your student society](https://blog.fixr.co/reasons-to-use-fixr-for-your-society-events/) |
| N16 | [MSL — About MSL](https://www.ukmsl.com/aboutmsl/) |
| N17 | [native — Events & Ticketing (knowledge base)](https://knowledge.native.fm/kb/en/events-ticketing-227940) |
| N22 | [JPN — Queima das Fitas: at the Queimódromo, payments by wristband (2023)](https://www.jpn.up.pt/2023/04/14/queima-das-fitas-no-queimodromo-pagamentos-vao-ser-feitos-com-uma-pulseira/) |
| N24 | [Zpos — Festivals](https://en.zpos.pt/festivals/) |
| N34 | [Queue-it — Pricing](https://queue-it.com/pricing/) |

### 9.5 Reference case: Queima das Fitas (checked on 2026-09-24)

| ID | Source |
|---|---|
| F4 | [JPN — "Pantones da Queima": students wear different colours to give assistance (2023)](https://www.jpn.up.pt/2023/05/15/pantones-da-queima-estudantes-vestem-diferentes-cores-para-dar-assistencia-no-recinto/) |
| F5 | [24 Notícias — Queima do Porto returns safer and more responsible (2022)](https://24noticias.sapo.pt/atualidade/artigos/as-excecoes-nao-marcam-o-que-e-a-queima-do-porto-dois-anos-depois-a-festa-dos-estudantes-regressa-com-vontade-de-ser-mais-segura-e-responsavel) |

---

## 10. How AI was used in this research

### 10.1 Tool and role

| Item | Description |
|---|---|
| Tool | **Claude** (Anthropic), with web search, page reading and read access to the project's local folder |
| AI's role | Read the repository, extract the list of product components, search for alternatives for each one and organise the comparison |
| Human role | The team member who wrote this analysis asked for the iteration based on the repository and for the split between the general document and the specific annex, and reviewed the result |
| What the AI did **not** do | It did not change the repository, test products or generate user answers |

### 10.2 What was done in this iteration

| Step | How | Result |
|---|---|---|
| 1. Read the repository | The AI read the Problem and Opportunity Report, the survey script and the commit history of the local clone | List of components C1–C11 (section 1), each with its origin in the repository |
| 2. Reuse the earlier research | The AI reused the relevant profiles from the two earlier rounds (security, 2026-09-24; general market, 2026-10-01) | Sources `M#`, `N#` and `F#` (sections 9.3 to 9.5) |
| 3. New research | 13 web searches on: multi-festival apps in Portugal, official Queima apps, Woov, LineUpr, venue maps, queues and waiting times, AI assistants for festivals, STCP transport | 10 new sources (`P1`–`P10`) |
| 4. Reading the pages | The AI opened 11 pages and extracted concrete facts (prices, features, dates) | Tables in section 2 |
| 5. Synthesis | Matrix by component, differentiation, threats and link to the survey questions | Sections 3 to 8 |

### 10.3 Searches without useful results

| Search | What happened | How it was handled in the document |
|---|---|---|
| Official app of the Queima de Coimbra in 2026 | No relevant results | We claim nothing about that app |
| Official app of the Enterro da Gata or of academic weeks | No relevant results | Not included |
| APP FAP news in 2026 (map, alerts) | Only consumer complaints and already known news | The description from FAP's page is kept |
| Woov, MapsPeople and 42Chat prices | Not public | "Price not public" |
| Greencopper and Crowd Connected | Only old or indirect pages | Greencopper not included; Crowd Connected cited through Aloompa (P5) |

### 10.4 Rules and limitations

| Rule or limitation | How it was handled |
|---|---|
| The pages were read through an automatic summary | Numbers must be confirmed at the source before they go into the final report |
| Two of the new sources are supplier blogs (P8, P10) and five are commercial pages (P3–P7) | Shown in the "Type" column and next to each use |
| The AI may misread a page | "❌" means "not found", not "does not exist" |
| The "MVP target" rating in the matrix is an intention, not a fact | Shown in the matrix legend |
| No user data comes from the AI | The hypotheses are left to the survey and the interview (section 8) |
