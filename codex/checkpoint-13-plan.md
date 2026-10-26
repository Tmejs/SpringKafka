# Checkpoint 13: JWT deployment profile

User approved local Basic by default and a deployment jwt profile with fund-orders.read for GET and fund-orders.write for both POST routes. JWT requires trusted issuer, audience, JWK endpoint, RS256 signature and expiration. Keep public health probes and stateless requests. Basic beans must be absent in JWT mode; missing JWT configuration fails startup. Kubernetes explicitly selects jwt and drops Basic Secret requirements; Compose defaults to Basic and can pass JWT settings.

Implement in order:
1. Reproduce that the current jwt profile incorrectly accepts Basic credentials.
2. Add resource-server dependencies, profile isolation, validated JWT properties, strict decoder and scoped chain.
3. Unit-test configuration and profile guards, MVC authorization for every endpoint, real RSA/JWK decoding including invalid claims/algorithms/signatures, rotation and cached/uncached outage behavior.
4. Add real JWT HTTP→Kafka→PostgreSQL/read and raw→DLQ integration tests, public probes and mode isolation. Generate separate Basic/JWT OpenAPI artifacts with matching security/scopes/errors.
5. Update Compose/Kubernetes, curl helper, README and a maintained JWT guide. Local Swagger stays on Basic; JWT docs require a bearer header and do not implement browser login.
6. Run focused tests, complete cleanverify, schema/config/script checks and independent review. Fix/re-review findings, record evidence, push checkpoint, no-ff merge, verify merged main, push/compare SHAs. Keep local running stack on Basic; no identity-provider deployment or token issuer is added.
