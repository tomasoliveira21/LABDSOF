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
- The idea that "students want real-time information" is common sense. The student survey (n = 24, *User Research*) points the same way: 88 % would use the app and 38 % chose real-time announcements, but this is a small, non-random sample.

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

| Item | Detail |
|---|---|
| Instrument | Online questionnaire in Google Forms, *"Queima das Fitas: como vives a noite do evento?"*, 11 questions in Portuguese, about 3 minutes. Script: [`inquerito-estudantes-google-forms.gs`](../Research/inquerito-estudantes-google-forms.gs) |
| Anonymity | No name or e-mail collected; answers used only in aggregate |
| Collection | **Wave 1, 3 to 6 October 2026:** 21 answers. **Wave 2, 30 October 2026:** 4 answers from friends of a team member (Tiago), sent between 22:42 and 22:43, after the wave 1 results were known |
| Answers | **25** in total. 1 person has never been to an academic festival and does not plan to go, so the form ended after question 1. **n = 24** for questions 2 to 10 (20 from wave 1, 4 from wave 2) |
| Flow | Question 1 filters: people who have been answer about their last event (Q2–Q6) and about an application (Q7–Q10); people who never went and do not plan to go end the form |
| Assumptions tested | A2 and A3 (Q6–Q9), A4 (Q3, Q5, Q10), the problem itself (Q3, Q4, Q6) |
| Cleaning | Free-text answers in the *Outro* (other) option were **excluded**: one is a joke and the others cannot be compared with the closed options (one each in Q2, Q3 and Q9, all in wave 1). The closed answers of those respondents were kept. Nothing else was changed |
| Analysis | Counts and percentages per option. Multiple-choice questions (Q2, Q3, Q4, Q8, Q9) add up to more than 100 %. Where the two waves differ, both are shown |

The group also used public news, official event communications and transport information (§4). The interview with a security volunteer (§4, assumption A4) **has not been done yet**.

### Results

Respondents are experienced Queima-goers: 22 of 25 have been several times and 2 once. 23 of the 24 who went have been to the **Queima do Porto**; 6 have been to Coimbra's.

**What went wrong the last time (Q3, n = 24)**

| Situation | Answers |
|---|---|
| Waited more than 30 minutes to get in | **21 (88 %)** |
| Got separated from my group | 10 (42 %) |
| Needed help (health, security, theft) and did not know whom to ask | 5 (21 %); 3 of them in wave 2 |
| Did not know where something was in the venue (WC, stage, first aid) | 4 (17 %) |
| Did not know how to get home or when there was transport | 2 (8 %) |
| Was the victim or witness of a theft | 2 (8 %) |
| None of these | 2 (8 %) |

22 of 24 (92 %) went through at least one of these situations.

**Where they looked for information (Q4, n = 24):** the organisation's Instagram or social media 19 (79 %), friends or WhatsApp groups 15 (63 %), the federation app (e.g. FAP) 7 (29 %), the official website 6 (25 %), signs and staff at the venue 5 (21 %). Nobody chose *"I did not look"*.

**How easy it was to find information during the night (Q6, 1 = very hard, 5 = very easy, n = 24):** 1 → 3 answers, 2 → 1, 3 → 8, 4 → 10, 5 → 2. **Average 3.29**, median 3.5. The two waves differ sharply: wave 1 averages **3.7** (n = 20, nobody below 3); wave 2 averages **1.25** (n = 4).

**First thing they would do if they needed help inside the venue (Q5, n = 24):** ask friends 10 (42 %), look for a security guard or staff member 8 (33 %), go to the first-aid post 5 (21 %), call 112 1 (4 %). Nobody chose *"I don't know"*.

**Would they use an application like this during the night (Q7, n = 24):** *yes, definitely* 11 (46 %), *probably yes* 10 (42 %), *probably not* 2 (8 %), *no* 1 (4 %). **21 of 24 (88 %) positive.**

**Most useful features (Q8, up to 3, n = 24)**

| Feature | Answers |
|---|---|
| Venue map (WC, stages, support points) | **16 (67 %)** |
| Timetable and location of transport home | 13 (54 %) |
| Help button (health, security, theft) that alerts the organisation | 11 (46 %) |
| Expected waiting time at the entrances | 10 (42 %) |
| Real-time announcements from the organisation (changes, alerts) | 9 (38 %) |
| Electronic ticket in the application | 6 (25 %) |
| Assistant to answer questions about the event | 2 (8 %) |

**What would stop them from using it (Q9, up to 2, n = 24):** battery use 12 (50 %), weak network at the venue 9 (38 %), having to install another app 7 (29 %), not trusting that the information is up to date 5 (21 %), *nothing, I would use it* 4 (17 %), having to share my location 1 (4 %).

**Location when asking for help (Q10, n = 24):** share the phone's location 19 (79 %), only by marking the place on the venue map myself 5 (21 %), no 0.

**Q11 (optional, open):** 2 answers, neither about the product.

**Against the validation targets** ([`product-vision.md`](../Product/product-vision.md) §5):

| # | Target | Result | Reading |
|---|---|---|---|
| V1 | ≥ 60 % *yes, definitely* or *probably yes* in Q7 | **88 %** (21/24); wave 1 alone 85 % | Met. It is a stated intention, not observed use |
| V2 | Q6 average ≤ 3 confirms that finding information is a problem | **3.29** (wave 1: 3.7; wave 2: 1.25) | **Not met; not confirmed by this survey.** Wave 1 found information fairly easy to find; the 4 answers of wave 2 found it very hard. The answers were given away from the event (see *Limitations*), so the result is read as open, not as evidence against the problem. The pain reported most is the wait at the entrance (88 %) |

**What this says about the assumptions**

- **A2** (they use the app during the night): supported as an intention (88 %). The barriers are physical, not of interest: battery (50 %) and weak network (38 %).
- **A3** (one app, opened by link or QR): 29 % name installing another app as a barrier, which supports the no-installation choice. The survey did not ask about one app for several events directly.
- **A4** (help through the app is faster): **not validated.** 5 of 24 needed help without knowing whom to ask (3 of them in wave 2), and the first reaction is people nearby (friends, staff, first aid), not a phone. 79 % would share location with a request. The volunteer interview is still missing.
- **Root cause 1** (no single source): supported. Information comes from social media and WhatsApp groups (79 % and 63 %); official channels (website, FAP app, signs) are each below 30 %.

### Limitations

- **Small sample.** n = 24 against a target of 100 or more (§4). Each answer is about 4 percentage points; the percentages show direction, not precise values, and no statistical test was done.
- **Convenience sample.** Answers came from the group's own reach, not from a random sample of students. 23 of 24 went to the Queima do Porto and 22 of 25 have been several times. First-timers and other cities are almost absent, and the survey did not ask for age, course or institution.
- **Two waves, different recruitment.** Wave 2 (4 answers) are friends of a team member, collected on 30 October after the team had seen that V2 was not met. Their Q6 answers (average 1.25) are very different from wave 1 (3.7), and with n = 4 they move the overall average by 0.4. Both values are reported so the reader can judge.
- **Answered away from the event.** People answered at home, calmly, not during the night with noise, a crowd, low battery and a weak network. Q6 probably underestimates how hard it is to find information on the spot. It needs a test in night conditions (usability test of SC1 and SC11).
- **Memory.** Q3 to Q6 ask about *"the last time"*, which may be one or more years ago.
- **Intention is not behaviour.** Q7 measures what people say they would do. The form describes the app in a positive way before Q7 (*opens by link or QR, no installation required*), which may push answers up.
- **Forced choices.** Q8 allows at most 3 options and Q9 at most 2; a low count means *less chosen*, not *not wanted*.
- **Cleaning.** *Outro* answers were excluded (one joke and two free-text answers). One respondent gave a joke in Q2 but their closed answers were kept; without them, Q7 is 91 % (21/23) and the Q6 average 3.3, so the conclusions do not change.
- **Only one side.** The survey covers participants. The event team's side (assumptions A1, A4, A6) has no primary data yet: the security-volunteer interview has not been done and the group has no access to FAP.

### Resulting product decisions

Proposed by Tiago from the data on 30 October 2026. Decisions 2, 3 and 4 change the backlog; they are applied in [`features.md`](../Product/features.md) and on the board once the team confirms them. Until then the backlog keeps its current priorities.

| # | Decision | Data | Before → after |
|---|---|---|---|
| 1 | **The venue map stays the centre of the participant app** (F5, Must) | Map is the most chosen feature: 16 of 24 (67 %) | Confirmed |
| 2 | **Entrance status per gate (F17) moves from Could to Should.** It stays a manual status set by the team; no automatic prediction (out of scope; assumption A6) | Waiting more than 30 min is the most common problem, 21 of 24 (88 %); waiting time chosen by 10 (42 %) | **Changed:** Could → Should |
| 3 | **Weak network and battery become design constraints of the participant pages:** light pages, few requests, the last-update time always visible. Weak-network mode (F11) goes back to sprint planning as a candidate for Should | Battery is the top barrier, 12 of 24 (50 %), then weak network, 9 (38 %); install 7 (29 %) | **Changed:** F11 reopened (Could, to review) |
| 4 | **The help request keeps the place marked on the map as the way that always works; the phone's location becomes an optional shortcut.** The help screen also shows the nearest first-aid and staff points | 19 of 24 (79 %) accept phone location and the other 5 accept the map; nobody refuses. 13 of 24 would first go to staff or first aid | **Changed:** map only → map plus optional phone location |
| 5 | **AI stays on help-request triage, not on a participant assistant** (decision of 8 October 2026, `features.md` §5) | The assistant is the least chosen feature, 2 of 24 (8 %) | Confirmed |

**Problem statement:** the report keeps the fragmented-information problem (root cause 1 is supported by Q4). V2 does not confirm that participants struggle to find information during the night, but the two waves disagree and the survey was answered away from the event, so this stays open until a test in night conditions. The strongest pain participants reported is the entrance wait, which is why decision 2 brings live entrance information forward. The statement is reviewed again after the volunteer interview.

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
