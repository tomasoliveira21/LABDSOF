# ADR-002 · Backend and frontend technology

## Status

**Accepted**: decided by the team on 11 October 2026 (Spring Boot, React) and 15 October 2026 (versions, Maven, React Native). Source: [`architecture-decisions-guide.md`](../architecture-decisions-guide.md) §0, F1 and H3.

## Context

Farol needs a backend made of five services (guide A2), a web app for event management (P3) and an app for participants, operators and responders, who use it on their phones at the venue. The assignment asks the team to justify its technology choices (§7.1.2, item 8). The team is small and has three sprints (architecture driver D9), so learning a new stack would take time away from the product.

## Decision

- **Backend:** **Java 21 (LTS)** and **Spring Boot 4.x**, built with **Maven** (guide §0, H3). Each service is its own Maven project in `backend/<service>/` (guide A3).
- **Web frontend:** a simple **React** app, only for event management (guide §0, F1).
- **Mobile frontend:** a **React Native** app for every other feature (guide §0, F1).

**Why:** Spring Boot and React are the stacks the team already knows and uses. React Native lets the team reuse what it knows from React on mobile.

## Consequences

- No time spent learning a new language or framework; the team can start building features in the walking skeleton.
- Spring has ready-made support for the other decisions: Spring AMQP for RabbitMQ ([ADR-004](0004-service-communication.md)), Spring Data JPA and Flyway for PostgreSQL ([ADR-003](0003-database-and-data-ownership.md)), and `openapi-generator` server stubs for the contract-first APIs (guide C7).
- Two frontends to build and maintain instead of one. The web app stays small (event management only), which limits the cost.
- A JVM service uses more memory and starts more slowly than, for example, a Node.js or Go service. With five services this raises hosting cost; acceptable while each service runs as a single instance (guide C5).
- The mobile app must reach the professors without the app stores; how is still open (see *Open points*).

## Alternatives considered

| Option | Why not chosen |
|---|---|
| .NET (ASP.NET Core) | Comparable to Spring Boot in features, but the team is less used to it |
| Node.js (e.g. NestJS) | One language across backend and frontend, but less familiar to the team for backend work, and weaker typing and tooling for a domain with strict rules (per-event isolation, audit) |
| Python (e.g. FastAPI) | Good for AI work, but the team has less experience building services with it |
| Angular or Vue for the web app | No advantage over React for a small back-office app, and React knowledge carries over to React Native |
| One responsive web app (PWA) instead of a mobile app | A single codebase, but more limited on phones (background location, offline storage, iOS support) and a weaker experience on a congested network |
| Native Android and iOS apps | Two codebases for a team of four; not realistic in three sprints |

## Open points

- React Native setup (e.g. Expo) and distribution to the professors: guide F4, Bruno.
- Venue map library in React Native: guide F3 / ADR-007, Tiago.
