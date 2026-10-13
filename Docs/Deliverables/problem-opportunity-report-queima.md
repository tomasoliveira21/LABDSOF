# Problem and Opportunity Report

**Project:** Coordination platform for large-scale academic events
**Pilot case:** Queima das Fitas do Porto (Queimódromo, 8 nights, May)
**Course:** LABDSOF 2026/27, Sprint 1
**Assignment direction:** 4.3 *Local Event Coordination Platform*, applied to an event with crowds and road closures
**Version:** revised with the group's alignment questionnaire and Tiago's decisions (October 2026)

---

## Product vision (context)

We propose a **platform built from scratch** to coordinate large-scale academic events. It is a single application that brings together **several events** (Queima do Porto, Coimbra's Queima, an academic week). Each event has an **owner and a team** that configure and run it independently.

- **For the participant:** a web application (PWA) where they choose the event and find the information and help they need during the night.
- **For each event team:** a backoffice where they configure their event (for example, the venue map and transport) and receive and handle incidents.
- **Per-event permissions:** each team only sees and changes its own event.

**Out of scope:** the physical capacity of the entrances (how many gates there are and how the queue funnel is designed). That is solved with infrastructure, not software.

---

## 1. Target Community

### Community

The academic community that organises and attends large student festivals in Portugal: Queimas das Fitas, academic weeks and similar events. In the **pilot case**, Queima das Fitas do Porto, there are about **250 thousand entries over 8 nights**, in an academic community of more than 80 thousand students.

The platform is not built for a single event. Each event joins the platform with its own team, so the community grows event by event without the product having to be redesigned.

### Users

| Group | Profile | Context of use |
|---|---|---|
| **Event team** (primary user) | The event owner (for example FAP) and its team: organisation, the Risk Behaviour Prevention committee (PCR), security coordination | Before the event, they configure it. During the event, they are in the control room and on the ground, need an overview of the venue and must respond quickly. |
| **Participants** (primary user) | Students aged 18 to 25, mostly in groups | At night, phone in hand, limited battery, congested network and often under the influence of alcohol. They need short, immediate information: where everything is, which transport to take, where to ask for help. |
| **Operational partners** (secondary users) | STCP, Metro do Porto, PSP, Civil Protection | They are a source or destination of information: reinforced timetables, road closures, incidents. |

The two primary users have **the same weight**, by unanimous choice of the group. Each has its own interface: the backoffice for the event team and the web application for the participant.

### Why this group

- The problem is concentrated in predictable, recurring, large-scale events, where a failure has safety consequences.
- Each event's organisation has the need, the budget (35% of Queima do Porto's 2026 budget went to security) and the channel to the students (ticketing, wristbands, social media) all at once.
- The format repeats in several cities and every year, which justifies a multi-event platform instead of a solution built for a single event.

---

## 2. Problem Statement

### Statement

> **The organisers of large-scale academic festivals and their participants** face **fragmented and outdated operational information** **when** tens of thousands of people move each night between the venue, the entrances and transport, **which causes** slow responses to incidents, long and badly distributed queues, and participants who do not know where to go, how to get home or whom to ask for help.

From the participant's point of view:

> **Students who go to the Queima** have no access to reliable, real-time information **when** they are in or around the venue in the early hours, **which leads** to long waits and risky decisions (splitting from the group, walking home, driving).

### Root causes

1. **There is no single source of operational information.** Organisation, PCR, security, PSP, transport operators and the City Council each communicate through their own channel: radio, website, Instagram, press releases, the municipality's traffic platform. Nobody has the full picture.
2. **Communication is static.** Timetables, road closures and rules are published before the event. What changes during the night (a congested gate, a late shuttle, an area to avoid) does not reach the participant.
3. **There is no shared spatial reference of the venue.** When there is one, the map is an image posted on social media that is not updated and cannot be used to show where each thing is or where an incident happened.
4. **Incident coordination depends on radio and phone.** This leaves no structured history and does not allow fast prioritisation and routing. It is a hypothesis to confirm in the interview with the security volunteer.
5. **Slow processes carried out at the gate itself.** In 2023, the chaos at the entrance was caused by exchanging the ticket for the wristband at the moment of entry.

### Opportunity

Each **event configuration team can upload the venue map** to the platform. The map becomes the common reference: the participant finds their way with it, and the team uses it to place help requests and information. The same model applies to any event, because each team uploads and maintains its own map.

### What the problem is and is not

| It is the problem | It is not the problem |
|---|---|
| Slow and badly distributed access processes across gates | Lack of physical capacity at the entrances (infrastructure, out of scope) |
| Scattered and outdated information during the event | Lack of security: investment is already at a record (more than 500 staff per night, more than 100 cameras) |
| Incident coordination without a shared view | Risk behaviours themselves (alcohol, violence). They are cultural. The product can shorten the response time, but it does not remove the cause. |
| | "There is no app": FAP already has one, but it does not cover event operations |

### Consequences of not solving it

- Crushing episodes and panic attacks in the queues, as in 2022 and 2023.
- Longer time until assistance reaches those who need it.
- Loss of reputation for the organisation, with public complaints and negative press coverage.
- Journeys home in the early hours without information, with a higher risk of drink-driving and unsafe travel.

---

## 3. Stakeholder Map

| Stakeholder | Role | Interest | Influence | Position |
|---|---|---|---|---|
| Academic federation (FAP) | Organiser, customer and decision-maker | High | High | For |
| PCR committee and support teams | Operational user | High | Medium | For |
| Student participants | End users | Medium | Low individually, high collectively (social media, complaints) | Neutral. They adopt it if it is useful and immediate. |
| Private security | Execution on the ground | Medium | Medium | Neutral |
| PSP and Civil Protection | Public order and emergency | High | High | Neutral or sceptical (they have their own systems and protocols) |
| INEM and fire brigades | Medical response | High | Medium | Neutral |
| STCP and Metro do Porto | Transport | Medium | Medium | For, if it does not require heavy integration |
| City Councils (Porto and Matosinhos) | Licensing and traffic | Medium | High | Neutral (they already have their own traffic platform) |
| Supplier of the federation's current app | Competitor or partner | High | Medium | Against, if it feels replaced |
| Ticketing (BOL, FNAC) | Partner needed to validate access | Medium | High | Neutral |
| Education institutions (ISEP, universities) | Institutional influencers, communication channel with students | Medium (student wellbeing and safety) | Medium | For |
| Each faculty's student associations | Co-organisers (parade, stalls) and direct channel to students | High | Medium | For, if they have a say in the configuration |
| Sponsors and concessionaires | Fund the event | Low | Medium | For, for the visibility |
| CNPD (data protection) | Regulator | Low, rises if there is location data | High | Neutral, but a constraint |
| Parents and families | Influencers | High on safety | Low | For |

### Influence × interest matrix

- **Manage closely** (high influence, high interest): FAP, PSP and Civil Protection.
- **Keep satisfied** (high influence, medium or low interest): City Council, ticketing, CNPD.
- **Keep informed** (low or medium influence, high interest): PCR, INEM, families, current app supplier, each faculty's student associations.
- **Involve as a channel:** education institutions (outreach and surveys).
- **Monitor:** sponsors, concessionaires.

### Conflicts of interest

1. **New platform vs. current supplier (the most critical, according to the group).** The supplier of the federation's app may see the product as a threat and not cooperate on integration. Mitigation: come in as a complementary module, through a link or QR code, without requiring changes to the existing app.
2. **Transparency vs. reputation.** Showing real queue times helps the participant, but exposes the organisation's failures.
3. **Monitoring vs. privacy.** Density and location help operations, but require data minimisation and consent (GDPR).
4. **Revenue vs. safety.** Showing real-time occupancy may pressure the organisation to limit entries, which reduces revenue.
5. **Yearly mandates.** Student association boards change every year, which puts continued adoption at risk.

---

## 4. Problem Evidence

### Strong evidence (verifiable public facts)

| # | Evidence | Source |
|---|---|---|
| E1 | About 250 thousand participants expected over 8 days (2025 and 2026) | [Jornal da Maia](https://www.jornaldamaia.pt/queima-das-fitas-do-porto-2025-reforca-seguranca-e-prevencao/), [Observador](https://observador.pt/2026/04/30/queima-das-fitas-do-porto-arranca-com-maior-investimento-em-seguranca-de-sempre-diz-fap/) |
| E2 | Security takes 35% of the budget: more than 500 staff per day, 12 watchtowers, more than 100 cameras, PCR with 90 people | [JN](https://www.jn.pt/pais/artigo/organizacao-investe-35-das-verbas-para-garantir-seguranca-na-queima-do-porto/18077018), [Rádio Festival](https://radiofestival.com.pt/noticia/2541955/queima-do-porto-2026-seguranca-recebe-investimento-recorde-e-absorve-35-do-orcamento) |
| E3 | 2022 and 2023: queues of more than 500 m, waits of more than 3 h, reports of crushing and panic attacks, complaints about "lack of information and signage" | [Renascença](https://rr.pt/noticia/pais/2022/05/05/filas-para-a-queima-das-fitas-do-porto-achei-que-ia-colapsar-a-ser-esmagada-e-quase-a-sufocar/283010/), [Público](https://www.publico.pt/2023/05/08/p3/noticia/primeira-noite-caotica-queima-fitas-porto-voltou-normal-2048919) |
| E4 | The cause identified in 2023 was exchanging the ticket for the wristband at the entrance. The organisation changed the venue after the complaints. | [JN](https://www.jn.pt/pais/artigo/horas-na-fila-e-nao-entraram-troca-de-bilhete-por-pulseira-gera-confusao-no-queimodromo/16310596), [Notícias ao Minuto](https://www.noticiasaominuto.com/pais/2315392/alteracoes-no-queimodromo-do-porto-apos-queixas-na-queima-das-fitas) |
| E5 | Special transport operation (shuttle every 15 min or less, extra metro and bus services) and road closures, announced through separate channels | [Andante](https://andante.pt/noticias/operacoes-especiais-queima-fitas-2025/), [STCP](https://www.stcp.pt/pt/noticias/stcp-com-servico-dedicado-para-a-queima-das-fitas-do-porto/) |
| E6 | The FAP app exists and covers tickets, discounts and sport, but not event operations | [FAP](https://www.fap.pt/noticias/app-fap-fap-no-teu-bolso) |
| E7 | Crowd density estimated from a festival app correlated above 0.8 with video measurement (technical feasibility) | [arXiv 1306.3133](https://arxiv.org/pdf/1306.3133) |

### Medium or weak evidence

- Serious incidents in 2026: one rape complaint at the Queimódromo do Porto and two in Coimbra. They show the need for fast help-request channels, but do not prove that an app would have prevented anything.
- Event app adoption rates between 55% and 65% with promotion. The figures come from vendor blogs (Cvent, Lineupr), which have a commercial interest.
- The idea that "students want real-time information" is common sense and has not been validated yet.

### Gaps and primary validation plan

The group decided to go ahead with two validation methods:

| Gap | Method |
|---|---|
| What information students look for during the night, whether the lack of information is still a problem and whether they would use an application for it | **Online student survey** (target: 100 or more answers) |
| How security coordinates incidents today, which channels it uses and where the delays are | **Interview with a security volunteer** at the Queima |

**Accepted limitation:** the group has no direct access to the organisation (FAP) or to its internal queue and incident data. The view of operations comes from the volunteer interview and from public data. As the assignment requires, no user-research data in this report was invented. The limitations are documented.

---

## 5. Assumptions and Risks

### Critical assumptions

| # | Assumption | Risk | Ease of validation |
|---|---|---|---|
| A1 | Organisations accept configuring and maintaining their event on the platform (map, transport, team) | High | Medium (interview and conversation with organisations) |
| A2 | Participants check the application during the night, even in adverse conditions (alcohol, battery, noise) | High | Medium (survey and prototype) |
| A3 | Participants adopt one application shared by several events, opened through a link or QR code, instead of one app per event | High | Easy (survey) |
| A4 | Help requests made through the application reach the right team faster than through the current channels | High | Medium (volunteer interview and simulation) |
| A5 | Each organisation has a usable venue map (image or PDF) | Medium | Easy |
| A6 | Real-time information (transport, queues) can be entered or estimated by the event team, without direct integration with the operators | Medium | Medium |
| A7 | AI features are more useful than a non-AI alternative and do not mislead users | Medium | Easy (simulated dataset and comparison with a non-AI alternative) |
| A8 | The mobile network holds up at peak, or the application works in degraded or offline mode | Medium | Easy (tests and reports) |
| A9 | The same product serves different events through configuration alone, without custom development | Medium | Medium |

The most critical assumptions are **A2, A3 and A4**. If participants do not use the application during the night, or if help requests do not get faster, the product loses its reason to exist. The survey validates A2 and A3; the interview with the security volunteer validates A4.

### Risks

**Market and adoption**
- Use is concentrated in a few days a year per event, with little retention. The multi-event platform softens this, because the same user comes back for other events.
- Convincing students to use yet another application. Mitigation: access through a link or QR code, no mandatory installation, with a single account for all events (mandatory login, decided on 9 October 2026).
- The supplier of each federation's current app may copy the features.
- Student association boards change every year, which makes each event's continuity on the platform uncertain.

**Technical**
- **Permissions across events:** a bug in the authorisation system can expose one event's data or incidents to another event's team. This requires automated tests dedicated to this isolation.
- Load peaks of tens of thousands of requests within minutes, for example at the end of a concert.
- Congested mobile network at the venue: requires caching, offline mode and short messages.
- Outdated or wrong information can make things worse (for example, a transport that no longer runs). Requires a clear indication of when each piece of data was updated.
- Phone location inside a crowded venue is inaccurate. The participant must be able to mark their location on the map.

**Execution**
- **Large scope for 3 sprints.** Mitigation: prioritise help requests, the map and transport information in the MVP. The rest comes in by order of value.
- Limited access to the real organisation during the semester. Mitigated with the volunteer interview, public data and simulation, documenting the limitation.

**Regulatory and ethical**
- GDPR: location and help requests (especially health-related ones) are sensitive data. Explicit consent, minimisation and short retention apply.
- A help button creates an expectation of response. The application must make clear that it is not an emergency service.
- AI-generated content cannot be presented as verified fact, as the assignment requires (5.6.2).

---

## 3 key questions before the solution phase

1. **Would students use an application during the night**, and what information would they look for first? (survey)
2. **How does a help request reach the right team today**, and how long does it take? (interview with the security volunteer)
3. **Would event organisations be willing to configure and maintain their event** on a shared platform?

---

## User Research

### Research method

We will use Google Forms to collect information from students who have attended academic events such as the Queima das Fitas. The questionnaire will help us understand what information students need during the event, how they currently look for help and which problems they remember from previous editions. We will also use online research, public news, official event communications and available information on transport, security, queues and crowd management. Whenever possible, this information will be complemented with interviews or informal conversations with people who have operational experience at the event.

### Results

TBD.

### Limitations

TBD.

### Resulting product decisions

TBD. The final product decisions will be refined after we receive feedback from the interviews and the student questionnaire.

---

## Product vision

### Personas

The main personas are the event team and the student participant. The event team includes the owner, the organisation members, the prevention teams, security coordination and other operational roles. This team uses the backoffice before and during the event to configure the event, manage the map, update transport information and handle incidents. The student participant is usually between 18 and 25 years old, goes to the event with friends and uses their phone during the night. This user needs short, reliable and easy-to-reach information, especially in a context where battery, network quality, noise and alcohol consumption can affect decision-making.

### Value proposition

The platform gives participants a single, reliable place to find practical information about the event and to ask for help when needed. At the same time, it gives the event team a shared operational view, helping it understand what is happening, where problems are and how to respond faster. The product's value is not only in publishing information, but in keeping that information tied to the real situation at the event.

### Main user journeys

A participant opens the web application through a link or QR code, selects the event, checks the map, finds entrances, services, transport options and safety information, and can ask for help if something goes wrong. The event team creates or selects an event in the backoffice, uploads or configures the venue map, adds key points such as entrances, stages, toilets, first aid and transport stops, and updates information during the night. When a participant sends a help request, the event team receives it with context and location, assesses its priority and routes it to the right operational team.

### Success criteria

The product will be successful if students can quickly find the information they need during the event, if help requests are easier to submit and manage, and if the event team gains a clearer view of what is happening at the venue. Success should also be measured by adoption during the event, by less confusion around entrances and transport, by a faster operational response and by positive feedback from students and event teams.

### MVP definition

The MVP should focus on the core coordination problem. It should include a web application for participants with event selection, a clear venue map, essential event information, transport information and a simple help-request flow. It should also include a backoffice for the event team, where each team can configure its own event, manage the venue map, update relevant information and view incoming incidents. Features that require heavy integrations, advanced crowd prediction or full automation should stay out of the first version, unless they are essential to validate the product.

**Out of scope:** the physical capacity of the entrances, including the number of gates and the physical design of the queue funnel. This is an infrastructure problem, not a software problem.
