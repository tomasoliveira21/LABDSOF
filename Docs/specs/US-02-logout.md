# US-02 · Log out

[Issue #60](https://github.com/tomasoliveira21/LABDSOF/issues/60) · Sprint B

| Criterion | Verification |
|---|---|
| US-02 AC-1 · End the browser session | The Compose smoke test performs RP-initiated logout and confirms that silent login requires credentials again. |
| US-02 AC-2 · Refuse the previous access token | The Compose smoke test presents the same signed token before and immediately after logout. The gateway tests cover an inactive session and identity-provider failure. |

The app keeps using Keycloak's RP-initiated logout. The only public API entry point checks each protected token's active state at Keycloak after JWT validation and account rate limiting. An inactive token gets `401`. If introspection fails, the gateway fails closed with `503` and `Retry-After`. Active responses are not cached, so a previous success cannot keep the token usable after logout. Internal services still check JWT signatures and claims. The gateway logs introspection failures with the request correlation ID, exception type and any HTTP status from Keycloak. Tokens, client credentials and Keycloak response bodies stay out of the logs.

## Performance and availability

Per-request introspection preserves immediate refusal of the old token. A 15–30 second positive cache would allow a previously checked token to work during that interval after logout. The gateway makes one synchronous Keycloak call per authenticated API request that passes its account limiter. At the [NFR-05](../Product/non-functional-requirements.md) peak, this means approximately 1,300 introspections per second in addition to the API workload, sustained for ten minutes. Keycloak latency counts toward API latency, and Keycloak errors or timeouts count toward the API error budget as `503`. The gateway configures two seconds each for connection and response timeouts.

Sprint C's k6 run should use the production token-check path with one instance per service and the expected polling mix. Record API p95 and p99, the total API error rate, Keycloak introspection latency and rate, and the gateway's introspection-related `503` rate. The thresholds remain p95 < 500 ms, p99 < 1.5 s and errors < 1%. Revisit the revocation design if those targets are missed.

## Keycloak setup

The confidential `farol-gateway` client has no interactive flows, service account or administrative roles. Keycloak 26.8.2 returns `active: false` when the introspecting client is absent from the token's audience, including when the browser session is still active. A local check against the project image reproduced this with the mapper removed and saw `active: true` after restoring it. Web access tokens carry both the existing `farol-api` audience and the introspection client's audience. Set `FAROL_GATEWAY_CLIENT_SECRET` in `infra/.env` to a private value. Fresh Compose realms import the client and mapper from the realm file. For an existing realm, add the confidential client and the audience mapper described in the realm file before upgrading. Existing signed-in users must log in again to obtain tokens with the new audience.

[Keycloak upgrade guide on introspection audience validation](https://www.keycloak.org/docs/26.8.2/upgrading/#_token_introspection_now_validates_audience_claim)
