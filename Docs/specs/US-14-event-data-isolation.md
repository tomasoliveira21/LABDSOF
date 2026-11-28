# US-14 · Keep event data private

[Issue #64](https://github.com/tomasoliveira21/LABDSOF/issues/64) · Sprint B · [API outline §1.5](../Technical/api-outline.md#15-authorisation)

| Criterion | Coverage |
|---|---|
| US-14 AC-1 · Refuse and audit access to another event | Events and Auth & Users already record refusals. Reports and Transports follow the Events audit-port pattern. Their adapters record the target event, authenticated subject, time and typed refusal. |
| US-14 AC-2 · Keep EventAdmin authority within its event | Authorization uses the membership and permissions for the target event. Reports and transport integration tests cover an EventAdmin of X who is a RegularUser of Y trying to write in Y. |
| US-14 AC-3 · Test isolation wherever event data is stored | Events and Auth & Users have cross-event integration tests. Reports and Transports verify read and write refusals. Badges coverage already existed and was verified. The self-only endpoint has an integration test proving a caller cannot read another account’s event badge. |

A non-member gets the same `404` as a caller requesting an unknown event. A member without permission gets `403` for a privileged operation. Both services record membership, visibility and permission refusals with the request correlation ID. In Reports, a missing request after authorization is a routine `404` and creates no audit entry. Reports uses its local membership copy to determine non-membership. In Transports, an event absent from the local event copy also returns a routine `404`. The API uses the same response for a concealed event. Badges exposes badge progress only for the caller.

The [create-event flow (#61)](US-06-create-event.md) is available through the API. The isolation tests use their own event and membership fixtures to exercise authorization directly.
