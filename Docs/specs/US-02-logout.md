# US-02 · Log out

[Issue #60](https://github.com/tomasoliveira21/LABDSOF/issues/60) · Sprint B

| Criterion | Verification |
|---|---|
| US-02 AC-1 · End the browser session | The Compose smoke test performs RP-initiated logout and confirms that silent login requires credentials again. |
| US-02 AC-2 · Refuse the previous access token | The Compose smoke test presents the same signed token before and immediately after logout. The gateway tests cover an inactive session and identity-provider failure. |

The app keeps using Keycloak's RP-initiated logout. The only public API entry point checks each protected token's active state at Keycloak after JWT validation and account rate limiting. An inactive token gets `401`. If introspection fails, the gateway fails closed with `503` and `Retry-After`. Active responses are not cached, so a previous success cannot keep the token usable after logout. Internal services still check JWT signatures and claims.

The confidential `farol-gateway` client has no interactive flows, service account or administrative roles. Web access tokens carry both the existing `farol-api` audience and the introspection client's audience. Set `FAROL_GATEWAY_CLIENT_SECRET` in `infra/.env` to a private value. Fresh Compose realms import the client and mapper from the realm file. For an existing realm, add the confidential client and the audience mapper described in the realm file before upgrading. Existing signed-in users must log in again to obtain tokens with the new audience.

[Keycloak token introspection](https://www.keycloak.org/securing-apps/oidc-layers#_token_introspection_endpoint)
