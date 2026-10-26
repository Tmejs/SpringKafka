# Checkpoint 13 verification: JWT deployment profile

Date: 2026-10-26
Baseline: 34823c1ab5a6fdf1e0a2d702a7eb0c302b643140
Implementation: 3fa9a551f45e42be3f5486f24d839a8513618e57

## Scope

Basic remains the default local profile. Selecting jwt excludes the Basic chain, credential properties and user/password beans. JWT trust configuration requires issuer, audience and explicit JWK endpoint. RS256 signatures, issuer, audience, expiration presence/time and not-before are validated. GET/HEAD require fund-orders.read; both POST paths require fund-orders.write. Health probes remain public; other routes are denied. Kubernetes explicitly selects jwt and disables Swagger; Compose defaults to Basic and passes optional JWT settings. The curl helper supports bearer tokens for both publication and asynchronous polling. No token issuer or external identity-provider deployment was added.

## Verification evidence

- Main was clean and local/origin/remote synchronized at baseline before the isolated worktree checkpoint.
- Initial RED: selecting jwt still accepted Basic credentials (expected401, actual200); profile isolation fixed this.
- Focused security tests passed before end-to-end integration work.
- Final Java21 ./mvnw -q clean verify: PASS, 90 unit tests and 33 integration tests; zero failures, errors or skips. Log: /tmp/nordea-checkpoint-13-reviewed-verify.log (local temporary evidence).
- Real RSA/JWK decoder tests cover invalid/missing issuer and audience, expired/future/missing expiration, wrong signature, unknown key, malformed/unsigned tokens, disallowed algorithm, key rotation and provider outage/cache behavior.
- MVC scope tests cover both read routes and both publication routes, missing/wrong scopes and absent authentication. Profile tests verify one selected chain and no Basic fallback in JWT mode, while default Basic remains functional.
- JWT HTTP integration tests exercise signed tokens through typed/raw HTTP -> Kafka -> PostgreSQL -> scoped reads, malformed raw -> DLQ with source headers, invalid tokens, public probes, scope restrictions, no session cookie, protected JWT docs and the bearer curl script without Basic environment variables.
- Provider503 regression: original defaults returned401 via a masked error dispatch while logging AuthenticationServiceException/Servlet.service(). Explicit bearer failure handling now returns401 directly; integration test also asserts no servlet/service exception output.
- Runtime-generated Basic and JWT OpenAPI artifacts passed @apidevtools/swagger-parser13.1.0 schema/reference validation. JWT document advertises bearer authentication, required scopes and403 responses.
- Fresh JaCoCo: 230/239 covered lines (96.2%); 41/52 covered branches (78.8%). Reports generated under target/site/jacoco. Existing tracked coverage snapshot is historical, not overwritten by this feature.
- git diff --check, Bash syntax check, Basic/default Compose configuration and JWT Compose configuration: PASS.
- Existing local Basic stack: anonymous readiness200 and anonymous business API401. Local runtime profile unchanged.

## Independent review

checkpoint_13_review found one Important issue: provider key-fetch failures were not explicitly handled and could trigger servlet error dispatch. Implemented an explicit authentication failure handler, added HTTP outage regression coverage and re-reviewed. Final reviewer result: no remaining Critical or Important findings.

## Integration procedure

Push checkpoint and compare local/tracking/remote SHAs; no-ff merge into clean synchronized main; rerun the full local gate and generated-contract validation on merged main; push main and compare SHAs. Preserve the checkpoint branch. No GitHub Actions added. A real identity provider and Kubernetes cluster are not available locally; tests use ephemeral signing keys, a local JWK server, real Kafka and PostgreSQL.
