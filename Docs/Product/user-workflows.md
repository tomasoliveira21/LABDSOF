# Main User Workflows

> LABDSOF 2026/27 · Sprint 1 · Assignment §5.4 and §7.1.2 (deliverable 4, *Principal user journeys*)
> **Status:** draft. The three journeys in the report (*Principais jornadas do utilizador*) are expanded here into step-by-step flows with diagrams. Steps that are not in the report are marked **[PROPOSED]**.
> **Personas:** [`personas.md`](personas.md) · **Features:** [`features.md`](features.md) · **Domain terms:** [`domain-model.md`](../Technical/domain-model.md)
> Diagrams use Mermaid, which GitHub renders directly.

## Overview

| ID | Journey | Persona | Source in the report | Demo priority |
|---|---|---|---|---|
| **W2** | **Participant asks for help** | P1 → P2 | Third journey (help request) | **1, the core journey** (links both sides of the product) |
| W3 | Operator handles a help request | P2 | Third journey | 1 (continuation of W2) |
| W1 | Participant finds information | P1 | First journey | 2 |
| W4 | Admin sets up an event | P3 | Second journey | 3 |
| W5 | Team publishes a live update | P3 → P1 | Second journey ("atualiza informação durante a noite") | 4 |
| W6 | Participant loses the network | P1 | Risks: congested network, offline mode (report §5) | Required by assignment §6.8 |

For the 5–7 minute presentation, **W2 + W3 tell the whole story**: a participant asks for help, and the event team sees it on the map, prioritises it and routes it.

---

## W1 · Participant finds information

Source: report, first journey: *"abre a aplicação web através de um link ou QR code, seleciona o evento, consulta a planta, encontra entradas, serviços, opções de transporte e informação de segurança"*.

```mermaid
flowchart TD
    A["Scans QR code or opens link"] --> B{"Link already points to an event?"}
    B -- Yes --> D["Event home: map + latest announcements"]
    B -- No --> C["Chooses the event from the list"] --> D
    D --> E["Map: entrances, stages, WC, first aid, stops"]
    D --> F["Transport home: shuttles, temporary stops, link to Google Maps"]
    D --> G["Safety information and Ask for help"]
    E --> H["Every item shows 'updated at HH:MM'"]
    F --> H
```

**[PROPOSED] details:** an event-specific QR code skips the selection step; the last-update time on every item comes from the risk "information that is out of date can make things worse" (report §5).

## W2 · Participant asks for help (core journey)

Source: report, third journey: *"Quando um participante envia um pedido de ajuda, a equipa do evento recebe esse pedido com contexto e localização, avalia a sua prioridade e encaminha-o para a equipa operacional adequada."* The AI step assumes **Option A (triage)** from [`features.md` §4](features.md#4-ai-assisted-workflow-decision-needed), which is still a **[DECISION NEEDED]**.

```mermaid
sequenceDiagram
    actor P as Participant (Rita)
    participant App as Participant web app
    participant Inc as Incident service
    participant AI as AI triage
    participant BO as Backoffice
    actor O as Operator (Miguel)

    P->>App: Taps "Ask for help"
    App-->>P: Shows "Call 112" first for emergencies
    P->>App: Chooses category, marks location on map, optional short text
    App->>Inc: Submit help request
    Inc-->>App: Request received, status Submitted
    App-->>P: Confirmation and live status
    Inc-)AI: Request submitted (asynchronous)
    alt AI available
        AI--)Inc: Suggested category and priority
    else AI unavailable or times out
        Inc->>Inc: Fallback: participant's category, order of arrival
    end
    Inc-)BO: New request for this event
    BO-->>O: Request appears on list and map
    O->>BO: Acknowledges, sets priority, routes to response team
    BO->>Inc: Status update
    Inc-->>App: Status changed
    App-->>P: "Help is on the way" (status update)
```

**Rules from the repository:**
- "Call 112" comes first; the app does not replace emergency services (report §5, regulatory risks; annex C6).
- Location is marked on the map by the participant and shared only with the request (annex C6, SafeZone pattern; survey Q10 tests acceptance).
- The AI only **suggests**; it never lowers a priority without a person (annex C9).

**[PROPOSED]:** the participant sees the status of their request (the market annex notes that no current channel shows the status of a request, §4 *UX* row).

## W3 · Operator handles a help request

Lifecycle of a help request **[PROPOSED]**. The report says the team *"avalia a sua prioridade e encaminha-o"*, but does not define statuses.

```mermaid
stateDiagram-v2
    [*] --> Submitted
    Submitted --> Triaged: AI suggestion stored
    Submitted --> Acknowledged: operator opens it (fallback, no AI)
    Triaged --> Acknowledged: operator accepts or changes suggestion
    Acknowledged --> Dispatched: routed to a response team
    Acknowledged --> Resolved: handled without dispatch
    Dispatched --> Resolved: response team closes it
    Submitted --> Cancelled: participant cancels
    Acknowledged --> Duplicate: merged with another request
    Resolved --> [*]
    Cancelled --> [*]
    Duplicate --> [*]
```

Steps:
1. A new request appears on the operator's list and on the venue map, with category, location, time and, if available, the AI suggestion.
2. The operator acknowledges it and confirms or changes the priority.
3. The operator routes it to a response team (medical, security, PCR) or handles it directly.
4. Every change is recorded with who and when (audit, assignment §6.6).
5. The response team, or the operator, closes it as resolved.

> ⚠️ **GAP:** the statuses, the list of response teams and who closes a request are assumptions. Needed: the interview with the security volunteer (report §4) to confirm how requests are handled today and which teams exist.

## W4 · Admin sets up an event

Source: report, second journey: *"cria ou seleciona um evento no backoffice, carrega ou configura a planta do recinto, adiciona pontos importantes como entradas, palcos, casas de banho, primeiros socorros e paragens de transporte"*.

```mermaid
flowchart TD
    A["Admin signs in to the backoffice"] --> B["Creates event: name, dates, nights"]
    B --> C["Uploads venue map: image or PDF"]
    C --> D["Places points of interest: entrances, stages, WC, first aid, stops"]
    D --> E["Adds transport: shuttles, temporary stops, schedules"]
    E --> F["Invites team members with a role: admin, operator"]
    F --> G["Defines response teams: medical, security, PCR"]
    G --> H["Publishes the event: QR code and link generated"]
```

**[PROPOSED]:** the steps *invite team members*, *define response teams* and *publish with QR code* are not in the report but are needed for per-event permissions (report, *Visão do produto*) and for routing (W3).

## W5 · Team publishes a live update

Source: report, *"atualiza informação durante a noite"*; root cause 2.

```mermaid
sequenceDiagram
    actor A as Admin (Sofia)
    participant BO as Backoffice
    participant Ev as Event service
    participant App as Participant web app
    actor P as Participant

    A->>BO: Publishes "Gate 3 closed, use Gate 1"
    BO->>Ev: Save announcement with timestamp
    Ev-)App: New announcement for this event
    App-->>P: Banner with the message and "updated at HH:MM"
```

## W6 · Participant loses the network (degraded mode)

Source: report §5, technical risks (*"Rede móvel congestionada no recinto: exige cache, modo offline e mensagens curtas"*); assumption A8; assignment §6.8.

```mermaid
flowchart TD
    A["Participant opens the app"] --> B{"Network available?"}
    B -- Yes --> C["Fresh data, saved to the device cache"]
    B -- No --> D{"Event cached on this device?"}
    D -- Yes --> E["Map and information from cache, labelled 'data from HH:MM'"]
    D -- No --> F["Message: no connection, look for staff or call 112"]
    E --> G{"Participant asks for help?"}
    G -- Yes --> H["Request cannot be sent: show nearest first aid on the cached map and 'Call 112'"]
```

**[PROPOSED]:** help requests are **not** queued silently while offline, because a request that looks sent but is not is worse than an honest failure ("fail safely", assignment §6.8).

> ⚠️ **GAP: degraded-mode scenario to demonstrate.** The assignment requires the presentation to show what happens when a dependency is unavailable (§6.8). Needed: the team picks which scenario to demo (network loss, AI unavailable, transport source unavailable) and when.
