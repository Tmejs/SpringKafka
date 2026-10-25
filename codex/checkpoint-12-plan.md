# Checkpoint 12: Swagger UI and generated OpenAPI

Use springdoc 3.x compatible with Boot4.1. Add Swagger UI and generated /v3/api-docs, secured with the existing Basic-auth chain. Keep the HTTP contract in controller/model annotations and a small OpenAPI metadata/security configuration. Document only the business controller paths; health probes remain documented in README.

Use the existing real HTTP/Kafka/PostgreSQL integration context to validate and export target/openapi/openapi.json during the Maven verify lifecycle. Assert routes, actual202 responses, request media types, enum/required constraints, pagination, Basic security and error schemas before exporting. Skip-tests package builds do not export a file but retain runtime generation. This avoids a second documentation-only application process or stub infrastructure.

Steps:
1. Preserve the staged old-validator removal separately.
2. Add failing HTTP/UI and OpenAPI contract/export integration tests; reproduce404 failures.
3. Add pinned springdoc starter, metadata, controller/model annotations and Failsafe export directory property.
4. Remove handwritten docs/api/openapi.yaml; update README/demo references and remove obsolete validator instructions. Keep AsyncAPI.
5. Run focused checks, full Java21 cleanverify, generated schema/reference validation and Swagger resource smoke checks.
6. Independent review, focused fixes/re-review, evidence, push checkpoint, no-ff merge into clean synchronized main, merged verification, push and compare SHAs.
