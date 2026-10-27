# ADR-009 · Architecture documentation

## Status

**Accepted**: decided by the team on 11 and 15 October 2026; the diagram layout was reviewed on 21 October 2026; the diagrams were updated on 26 October 2026 for the decisions since then (one web app, API gateway, Keycloak, local delivery; #<issue>). Source: [`architecture-decisions-guide.md`](../architecture-decisions-guide.md) B1 to B7.

## Context

- The Sprint A *Technical Design* deliverable asks for the high-level architecture, the main views and the decisions behind them (assignment §7.1.2).
- The architecture is microservices from the start ([ADR-001](0001-architectural-style.md)): one web app, the API gateway, Keycloak, five services, RabbitMQ, one database per service and two external systems. One picture cannot show all of it at a readable size.
- The diagrams must stay editable by the whole team, live in the repository next to the code and be reviewed in pull requests like any other change.
- The asynchronous workflows (help-request triage, transport import) are easier to follow as sequences than as boxes (assignment §6.3).
- Delivery is local only, on one machine with Docker Compose ([ADR-011](0011-deployment-and-infrastructure.md), guide G1), so the physical view shows that machine.

## Decision

1. **Notation: C4 inside the 4+1 views, without scenarios** (B6). The user workflows W1–W8 ([`user-workflows.md`](../../Product/user-workflows.md)) already play the role of the scenarios.
   - **Logical view:** C4 boxes and the [domain model](../domain-model.md). Level 1 is the system context, level 2 groups Farol into frontend, backend and infrastructure, level 3 shows the containers (web app, API gateway, Keycloak, five services, RabbitMQ, one database per service and Keycloak's) and level 4 the components inside one container.
   - **Process view:** dynamic diagrams of the help request with asynchronous triage and of the transport import, levels 1 to 4.
   - **Implementation (development) view:** repository, top-level folders, projects (one Maven project per service) and the hexagonal packages (`domain`, `application`, `adapters/in`, `adapters/out`, B5).
   - **Physical view:** where each part runs on the local machine of the local delivery (ADR-011), and the browser that uses it.
2. **Four zoom levels per view, three in the physical view.** The physical view stops at level 3, which shows one container per service and one PostgreSQL server with six databases, one per service and one for Keycloak (professor, 22 October 2026). Level 4 has **one diagram per service** (Reports, Transports). The web app is opened at level 3 instead (*3 web*: the level-3 diagram with the web app's components, or `frontend/`'s folders, in place of its box), so it is seen next to the gateway and Keycloak it calls. Every service follows the same hexagonal layout, so Reports and Transports are the examples; the level-4 diagrams of Auth & Users, Events and Badges are written with each service.
3. **View mappings:** for each level, one diagram puts the logical diagram next to the implementation diagram and another puts it next to the physical diagram (levels 1 to 3), with dashed arrows from each logical element to its counterpart.
4. **Tool: draw.io** (B1), drawn with the draw.io MCP or by hand. Each diagram is one **`.drawio.svg`** file: an SVG that renders on GitHub and in Markdown and also embeds the editable draw.io source. There are no separate `.drawio` and image files to keep in sync.
5. **Layout rules:** one title per diagram (`[<View>, level N] Farol`), C4 colours, no legend, every arrow labelled with its purpose and, where useful, its protocol. Persons are a single **User** at logical levels 1 and 2 and split into personas from level 3.
6. **Location:** `Docs/Technical/diagrams/<view>-view-level-<N>[-<part>].drawio.svg` and `mapping-logical-<view>-level-<N>[-<part>].drawio.svg`, linked from [`architecture.md`](../architecture.md) §14. Service-specific diagrams written later go in `backend/<service>/docs/` (B4).
7. **Decisions:** ADRs in `Docs/Technical/adr/NNNN-title.md`, Michael Nygard format plus *Alternatives considered* (B7).

## Consequences

- Each level answers one question at a readable size; the mappings show how the views fit together without merging them into one diagram.
- One file per diagram: renders in the browser, opens in draw.io, and its diff in a pull request is still text (though hard to read).
- More files to keep consistent: 32 diagrams today (logical 6, process 8, implementation 6, physical 3, mappings 9). A change to a logical diagram also needs the two mapping diagrams of that level.
- The Mermaid diagrams in `architecture.md` become working sketches; the draw.io diagrams are the reference.
- **[PENDING]** markers in the diagrams must be removed when the decision they wait for is taken. Since 27 October 2026 only the `frontend/` folder layout is left (Tomás); the AI model is marked *to review* until the team accepts K1.

## Alternatives considered

| Option | Why not chosen |
|---|---|
| Mermaid C4 or C4-PlantUML (diagrams as code) | Easy diffs, but little control over layout; large diagrams become unreadable (B1) |
| Structurizr (one model, many views) | Keeps the views consistent automatically, but one more tool and DSL for the team to learn |
| Separate `.drawio` source plus exported PNG | Two files per diagram that can drift apart; `.drawio.svg` holds both |
| 4+1 with a scenarios view | The workflows W1–W8 already cover the scenarios (B6) |
| One level-4 diagram for all containers | Tried first; too dense, and it mixed apps and services that share nothing at that level |

## Open points

- Level-4 diagrams for Auth & Users, Events and Badges: written with each service (B4).
- Physical view: ✅ closed on 26 October 2026 with the local delivery (ADR-011, [`local-delivery.md`](../local-delivery.md)).
- Identity provider in logical level 1: ✅ closed; Keycloak is self-hosted ([ADR-006](0006-identity-and-access.md)), so it is a container from level 2, not an external system.
