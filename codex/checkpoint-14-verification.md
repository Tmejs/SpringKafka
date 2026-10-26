# Checkpoint 14 verification: public Swagger

Date: 2026-10-26
Baseline: 0558b4570ec1448ada7705a7e42337bfd0c689bb

Both authentication profiles now permit anonymous access to /swagger-ui.html, /swagger-ui/**, /v3/api-docs, /v3/api-docs/** and /v3/api-docs.yaml. API authentication, Basic credentials, JWT scope checks and generated API security requirements remain intact. Kubernetes still disables documentation unless explicitly enabled. Active README, demo and JWT guides describe public browsing and using Authorize for API requests. Corrected one stale demo-guide statement claiming JWT was unimplemented.

## Evidence

- Clean synchronized local/origin/remote main before a fresh isolated checkpoint branch.
- RED: both modified real HTTP tests failed expected200/actual401 before the security rule changes; /tmp/nordea-checkpoint-14-red.log.
- Final Java21 ./mvnw -q clean verify: PASS; 90 unit tests and 33 integration tests, zero failures/errors/skips; /tmp/nordea-checkpoint-14-verify.log.
- Real Basic and JWT HTTP tests verify anonymous UI HTML, bundled JavaScript, Swagger config, JSON/YAML contracts, entrypoint redirect and protected business API401. Generated contracts retain Basic/bearer schemes; existing tests exercise authenticated operations and JWT scopes.
- Both exported OpenAPI files passed @apidevtools/swagger-parser13.1.0 schema/reference validation.
- git diff --check: PASS.
- Independent checkpoint_14_review: no Critical or Important findings.

Push/compare checkpoint SHAs, no-ff merge into clean synchronized main, rerun full gate and contract validation, then push/compare main SHAs. Preserve branch and update only the local app container for the browser demonstration; keep its default Basic profile.
