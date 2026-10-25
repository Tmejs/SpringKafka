# Checkpoint 10 verification: interview demo and contract validation

Date: 2026-10-25
Baseline: fa0084d77a1ac0e13832350cce97118bc312a5b5
Preserved user cleanup: 3870675 (unused import only)
Verified implementation: 7fb9572b0de72c3ba15398207ce55e0587eae8b4

## Deliverables

- docs/demo.md: 10–12 minute presentation, Compose startup, curl walkthrough, DLQ observation, real batch proof, operational discussion and shutdown instructions.
- scripts/demo.sh: health/authentication, typed/raw publishing, asynchronous reads, listing, invalid typed/raw input and duplicate publication. Fresh IDs, bounded reads and expected HTTP status checks. No credentials printed; no automatic data deletion.
- scripts/validate-contracts.cjs: optional schema-aware contract validation, with pinned temporary tool installation instructions. No Node dependencies added to the Java build.
- README links to the demo guide. Existing application behavior and API documents are unchanged; user import cleanup is committed separately.

## Verification

- Initial main/origin/main/remote main SHA matched baseline; remaining known user cleanup was preserved on the checkpoint branch before restoring clean main and starting isolated work.
- Docker Compose build/start with wait: PASS, real app/Kafka/PostgreSQL healthy. Used disposable project nordea-demo-checkpoint-10 on 18080/19093 because older user stack occupied 8080/9092.
- Live scripts/demo.sh all: expected readiness 200, unauthenticated API 401, publication 202, consumed reads 200, typed unknown status 400 and absent ID 404, raw malformed publication 202, list 200.
- Independent Kafka console consumer confirmed the exact malformed raw payload and unique demo marker in the DLQ.
- Consumer-group inspection confirmed lag zero on all three source partitions. Database query confirmed three distinct stored events after four valid publications including a duplicate. No invalid event was stored.
- Minor review regression: with an already-stored exported EVENT_ID, pre-fix invalid-typed failed (expected404/actual200). Fixed script generated a fresh ID and returned expected400 then404. Live RED/GREEN verified.
- Temporary Compose resources, including only their newly created volume, were removed after verification. Existing user containers and volumes were not modified.
- bash -n, Node --check and git diff --check: PASS. Script help exercised.
- Full Java21 ./mvnw -q clean verify: PASS, 52 unit and16 integration tests, zero failures/errors/skips. The later fix changed only the demo script and guide and received its own live verification.
- Fresh coverage target/site/jacoco/index.html:167/176 lines (94.9%),24/32 branches (75.0%). No report copied under codex.

## API contracts

- docs/api/openapi.yaml: OpenAPI3.0.3 schema and reference validation PASS with @apidevtools/swagger-parser13.1.0. This is schema/reference validation, not a claim that every runtime response was exhaustively validated against the document.
- docs/api/asyncapi.yaml: AsyncAPI2.6.0 validation PASS with official @asyncapi/parser3.6.6, zero errors.13 optional warnings concern default content type, document identifier, tags, contact/license, operation IDs and message IDs. One informational recommendation suggests a newer specification. Existing explicit per-message content types remain valid. No license ownership claim or specification migration was invented to suppress recommendations.
- Reproducible commands and pinned versions are in docs/demo.md. Both documents remained unchanged.

## Independent review

checkpoint_10_review: PASS. No Critical/Important findings. One Minor fixed-ID issue corrected and re-reviewed; final review has no remaining findings. Reviewer performed no builds or modifications.

## Release procedure

Push checkpoint and compare local/tracking/remote SHAs. Merge --no-ff into clean synchronized main, rerun full applicable verification on merged main, push main and compare all main SHAs. Preserve checkpoint branch. No hosted CI added.
