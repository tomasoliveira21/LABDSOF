# ADR-002 · Backend and frontend technology

## Status

**Accepted**: decided by the team on 11 October 2026 (Spring Boot, React) and 15 October 2026 (versions, Maven, React Native); updated on 21 October 2026 (services deployed independently from the start, guide A4) and on 22 October 2026 (the React Native app is removed; one React web app, meeting with the professor P9, #125). Source: [`architecture-decisions-guide.md`](../architecture-decisions-guide.md) §0, F1 and H3.

## Context

Farol needs a backend made of five services, deployed independently from Sprint A (guide A2, A4), a client for event management (P3, on a laptop) and for participants and responders, who use it on their phones at the venue. The assignment asks the team to justify its technology choices (§7.1.2, item 8). The team is small and has three sprints (architecture driver D9), so learning a new stack would take time away from the product.

## Decision

- **Backend:** **Java 21 (LTS)** and **Spring Boot 4.x**, built with **Maven** (guide §0, H3). Each service is its own Maven project in `backend/<service>/` (guide A3), with its own `main`, Dockerfile and deployment.
- **Frontend:** **one React web app** for every user (guide §0, F1). Each page is built for one use: **on the phone** (participants, field teams) or **on a laptop** (event management, control room). It runs in the browser; there is no app to install.

**Why:** Spring Boot and React are the stacks the team already knows and uses. One web app means one frontend codebase, nothing to install for participants (personas, P1: *"No installation"*), and no app-store distribution to the professors.

## Consequences

- No time spent learning a new language or framework; the team can start building features in the walking skeleton.
- Spring has ready-made support for the other decisions: Spring AMQP for RabbitMQ ([ADR-004](0004-service-communication.md)), Spring Data JPA and Flyway for PostgreSQL ([ADR-003](0003-database-and-data-ownership.md)), and `openapi-generator` server stubs for the contract-first APIs (guide C7).
- One frontend to build and maintain. Phone pages and laptop pages share the components, the generated API client and the login.
- A JVM service uses more memory and starts more slowly than, for example, a Node.js or Go service. With five services running from Sprint A (no monolith phase) this raises hosting cost from the start; acceptable while each service runs as a single instance (guide C5).
- What a browser on a phone cannot do well: reliable push notifications and background location. The product needs neither (polling, guide C5; the location is marked on the map, US-23). Working offline (US-46, Could) needs a service worker, decided with that story.

## Alternatives considered

| Option | Why not chosen |
|---|---|
| .NET (ASP.NET Core) | Comparable to Spring Boot in features, but the team is less used to it |
| Node.js (e.g. NestJS) | One language across backend and frontend, but less familiar to the team for backend work, and weaker typing and tooling for a domain with strict rules (per-event isolation, audit) |
| Python (e.g. FastAPI) | Good for AI work, but the team has less experience building services with it |
| Angular or Vue for the web app | No advantage over React, which the team already knows |
| React web app for event management plus a **React Native** mobile app for the rest | Decided on 15 October 2026 and dropped on 22 October 2026 (#125): two frontends, a setup (Expo or bare) and a way to deliver the app to the professors without the app stores, for features that a web app also covers |
| Native Android and iOS apps | Two codebases for a team of four; not realistic in three sprints |

## Open points

- Venue map in the web app: guide F3 / [ADR-007](0007-venue-map-representation.md), rewritten for the web, waits for Tiago's review.
