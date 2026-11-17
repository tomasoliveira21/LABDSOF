# #212 · RabbitMQ topology and per-service users

> **Issue:** [#212](https://github.com/tomasoliveira21/LABDSOF/issues/212) (opened on 16 November 2026: no issue covered it) · **Type:** chore · **Owner:** Tiago Teixeira · **Spec rules:** [README](README.md)
> **Sources:** [API outline](../Technical/api-outline.md) §7.2 and §7.5, decision C4 (#177), [ADR-004](../Technical/adr/0004-service-communication.md), [ADR-006](../Technical/adr/0006-identity-and-access.md), [ADR-011](../Technical/adr/0011-deployment-and-infrastructure.md), [`local-delivery.md`](../Technical/local-delivery.md) §1. Stacked on #189.

## 1. Acceptance criteria

| # | Given / when / then | Covered by |
|---|---|---|
| AC-1 | Given the stack, when it starts, then the 4 exchanges, the 10 queues of §7.5 and their 10 dead-letter queues exist, durable, with their bindings | CI step *Check the broker topology and the service users* (10 + 10 durable quorum queues, 37 bindings) |
| AC-2 | Given a service user, then it writes only to its own exchange and the dead-letter exchange, reads only its own queues, declares nothing, has no management access | Same CI step: exact permissions of the five users, no tags |
| AC-3 | Given messages waiting, when the stack starts again, then they are kept, and a changed password is applied | Checked by hand on 16 Nov (below); setup re-declares users on every `up` |
| AC-4 | Given CI, then the topology and the permissions are checked against the running broker | CI step above, after `rabbitmq-setup` exited 0 |

## 2. Decisions

| # | Decision | Why |
|---|---|---|
| D1 | **Post-boot setup**: a one-shot `rabbitmq-setup` container (the broker's own image, read-only, no capabilities) imports `infra/rabbitmq/topology.json` and declares the users, right after the broker is healthy and on every `up` | Measured on 16 Nov: a definitions file loaded at boot on a blank broker creates no user and no vhost, so the users would have to be in the file as password hashes, which cannot come from `infra/.env` without a rendering step. Council of 16 Nov, unanimous over *boot-time file rendered by an init container*, *services declare their own queues* (breaks C4: a late consumer loses events) and *committed hashes of example passwords* (breaks per-machine passwords). Wording change of C4 proposed for the team in the PR |
| D2 | Services **do not wait** for `rabbitmq-setup` | ADR-011: services do not wait for the broker. A producer cannot publish before its exchange exists, and the exchange and its consumers' queues arrive in the same import, so no event is published before its queues exist; the outbox keeps events until then |
| D3 | The broker's admin (`RABBITMQ_ADMIN_USER`) is created from `infra/.env` on the first start; only the setup container (through `RABBITMQADMIN_USERNAME`/`PASSWORD`, not in its arguments) and the operator use it | Least privilege: no service holds it. The management UI stays on `127.0.0.1` |
| D4 | Service users: configure `^$`, write `^(farol\.<service>|farol\.dead-letters)$` (Transports and Badges: dead letters only), read `^<service>\..*`, no tags | ADR-006 "one user per service"; a service cannot declare, delete or purge anything, nor read another service's queue |
| D5 | Durable **quorum** queues; each with `x-dead-letter-exchange: farol.dead-letters` and routing key `<queue>.dlq`; dead-letter queues bound to that direct exchange | Data safety on restart; a message rejected without requeue, or republished by Spring's `RepublishMessageRecoverer` (architecture §6), lands in its own DLQ |
| D6 | `topology.json` generated once from §7.5 and kept as the single source; a change goes through a PR | Reviewable; CI checks the counts against the broker |
| D7 | `pids_limit: 256` on the broker (was 128) | Measured: about 45 processes at rest; each CLI run (health check, `rabbitmqctl`) starts an Erlang VM with dozens of threads; two at once aborted with *Failed to create poll thread*. After the change: 0 failures in 16 concurrent runs |
| D8 | The setup waits up to 60 s for the management API | The broker's health check (`ping`) passes before the management API listens (first run failed with a connection error) |

## 3. Edge-case matrix

| Case | Expected | Evidence |
|---|---|---|
| Setup starts before the management API listens | Waits, then imports | First run failed; fixed by D8; second run passed |
| Setup fails | CI fails with its log | CI step checks the exit code of `rabbitmq-setup` |
| Second `up` with a message waiting | Message kept | 16 Nov: `events.auth-users-events` held 1 message before and after |
| Password changed in `infra/.env` | New one accepted, old refused | 16 Nov: `authenticate_user events` with the new password *Success*, with the old one *invalid credentials* |
| Permissions changed by hand | CI fails; the next `up` restores them | 16 Nov: CI step failed with the diff; passed again after re-running setup |
| A queue argument changed in `topology.json` | Setup fails (PRECONDITION_FAILED) | Documented: reset with `down -v` |
| Empty service password | Setup stops naming the user; `up` stops naming the variable | `${VAR:?}` in Compose; check in `setup.sh` |
| Old volume without the admin user | Admin missing | Documented: reset once with `down -v` |
| Concurrent CLI runs in the broker | No abort | D7 |
| A service tries to declare or read another service's queue | Refused by the broker | Permissions of D4; functional AMQP tests come with each service (#191 onwards) |

## 4. Handoff

- **For every service (#191 to #194, #201):** connect as `<service>` with `RABBITMQ_<SERVICE>_PASSWORD`; publish to `farol.<service>` with the routing keys of §7.4; consume `<service>.<producer>-events`; dead letters through `farol.dead-letters` with routing key `<queue>.dlq`. **Do not declare** exchanges, queues or bindings (no `Queue`/`Exchange` beans; the user cannot declare anyway). Use publisher confirms for the outbox.
- **Open for the team:** accept the C4 wording change in the review (Bruno, António and Tomás use it).
