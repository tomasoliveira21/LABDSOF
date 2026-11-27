# US-14 · Keep event data private

[Issue #64](https://github.com/tomasoliveira21/LABDSOF/issues/64) · Sprint B · [API outline §1.5](../Technical/api-outline.md#15-authorisation)

| Criterion | Coverage |
|---|---|
| US-14 AC-1 · Refuse and audit access to another event | Events and Auth & Users already record refusals. Reports and Transports now record the target event, authenticated subject, time and reason without logging request contents. |
| US-14 AC-2 · Keep EventAdmin authority within its event | Authorization uses the membership and permissions for the target event. Reports and transport integration tests cover an EventAdmin of X who is a RegularUser of Y trying to write in Y. |
| US-14 AC-3 · Test isolation wherever event data is stored | Events and Auth & Users have cross-event integration tests. Reports and Transports verify read and write refusals. Badges has a self-only endpoint and its integration test verifies that an account cannot read another account’s event badge. |

A non-member gets the same `404` as a caller requesting an unknown event. A member without permission gets `403` for a privileged operation. The audit uses a structured message and the existing request correlation ID where present. Reports reads and writes continue to scope resource lookups by event ID. Badges has no event-addressable operations and badge progress is scoped by account.

The create-event flow (#61) is a dependency for the end-to-end user journey. The isolation tests build their own event and membership fixtures so they can verify authorization independently of that flow.
