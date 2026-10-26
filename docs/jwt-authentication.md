# JWT authentication

## Select authentication

The default profile uses the existing Basic user, configured with `APP_BASIC_AUTH_USERNAME` and `APP_BASIC_AUTH_PASSWORD`. Local Compose defaults to this mode.

Set these environment variables for JWT:

```text
SPRING_PROFILES_ACTIVE=jwt
JWT_ISSUER_URI=https://identity.example/realms/fund-orders
JWT_AUDIENCE=fund-orders-api
JWT_JWK_SET_URI=https://identity.example/realms/fund-orders/protocol/openid-connect/certs
```

These URLs are illustrative; use the issuer and public JWK endpoint supplied by your identity provider. The issuer must match the token's `iss` exactly. The configured audience must occur in `aud`. The JWK endpoint must be reachable from the application container; `localhost` inside a container refers to that container. Use HTTPS in real deployments. HTTP is accepted to support isolated local environments and tests.

The JWT profile requires all three settings at startup and excludes Basic authentication, the in-memory user and its password encoder. Basic credentials can be absent. Switching requires restarting the application. Without the `jwt` profile, Basic remains selected, so the Kubernetes manifest explicitly sets `jwt`.

Compose passes these environment variables through. Put the selected profile and provider settings in your local `.env` before recreating the app if demonstrating JWT locally. Keep `SPRING_PROFILES_ACTIVE=default` for the ordinary Basic demo.

## Authorization

| Request | Required scope |
| --- | --- |
| GET/HEAD `/api/v1/fund-order-events` and event lookup | `fund-orders.read` |
| POST `/api/v1/fund-order-events` | `fund-orders.write` |
| POST `/api/v1/fund-order-events/raw` | `fund-orders.write` |
| Health, readiness and liveness | Anonymous access |
| Swagger and OpenAPI, when enabled | Any valid authenticated bearer token |

Spring Security maps the standard `scope` or `scp` claim to `SCOPE_` authorities. Configure the identity provider to issue these scopes for this audience. Provider-specific role claims are not mapped. A read token cannot publish; a write token cannot read. Missing or invalid authentication returns 401; valid authentication without the required scope returns 403. Other routes are denied. Security failures use Spring Security's responses, not the controller error schema.

## Token validation and keys

`JwtSecurityConfiguration` configures an OAuth2 resource server. It verifies RS256 signatures using the trusted JWK endpoint and validates issuer, audience, expiration and not-before time. An `exp` claim is required. Spring's default timestamp clock tolerance applies (60 seconds); keep clocks synchronized. Unsigned tokens, other algorithms, wrong signatures and unknown keys are rejected.

The application accepts access tokens; it does not issue tokens or implement login. No client secret or private signing key belongs in this service. Configure the identity provider separately and obtain tokens there.

The explicit JWK URI avoids issuer discovery and startup dependency on the provider. Signature validation fetches and caches public keys; rotation to a new key causes a refresh. Keep old public keys available until their tokens expire. During provider outages, cached known keys can continue to validate; tokens requiring unavailable keys fail closed. Connect and read timeouts are three seconds each. Token rejection returns 401, including key-fetch failures; monitor identity-provider availability separately. Liveness and readiness do not call the provider.

Validation is stateless and does not provide immediate revocation or introspection. Use short token lifetimes. TLS termination, ingress request limits, issuer administration and token acquisition are deployment responsibilities.

## Deployment and API documentation

The Kubernetes example selects `jwt`, reads `jwtIssuerUri`, `jwtAudience` and `jwtJwkSetUri` from `fund-orders-config`, and retains database secrets in `fund-orders-secrets`. Configure those resources before deployment. Swagger and OpenAPI are disabled there via Springdoc environment settings.

When enabled, the generated JWT contract describes bearer authentication, required scopes using `x-required-scope`, and 403 responses. Documentation assets are themselves bearer protected. There is no browser OAuth login flow: use the Basic local UI for an interactive demo, or fetch the JWT contract using a bearer header. `./mvnw clean verify` exports both `target/openapi/openapi.json` and `target/openapi/openapi-jwt.json`.

```bash
curl -i -H "Authorization: Bearer $ACCESS_TOKEN" \
  https://your-deployment.example/api/v1/fund-order-events
AUTH_MODE=jwt ./scripts/demo.sh raw-valid
```

The script expects `ACCESS_TOKEN` to be exported. The raw-valid demo needs both scopes because it publishes and then polls the read API. Do not store tokens in tracked files or share logs containing them.

## Verification

Unit and MVC tests check profile isolation, scope boundaries, required settings and Basic compatibility. Real-token decoder tests use a local public-key endpoint for bad signatures, invalid claims, algorithm restrictions, rotation and outages. JWT integration tests exercise real HTTP authentication, typed/raw publication, Kafka consumption, PostgreSQL reads and DLQ delivery. Tokens are signed with ephemeral test keys; no external identity provider is required for the test suite.
