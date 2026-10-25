# Checkpoint 6 verification: ID-only deduplication

Date: 2026-10-25
Baseline: 9a317c51b67758c5be64e509871f5c33ddc26d48
Verified implementation: 5383870

## Requested behavior

User requested removing hash double checking to simplify the project, and explicitly directed that the schema change be applied to V1 rather than introducing an upgrade migration.

The service validates the event and performs the atomic native insert. The event_id primary key and ON CONFLICT DO NOTHING implement first-stored-version-wins semantics. Valid identical or changed-content replays are successful no-ops; neither changes the original row or creates a DLQ record. Invalid messages remain subject to existing validation and DLQ policy.

Removed hash generation, serialization for hashing, hash lookup/comparison, entity hash mapping and the V1 hash column. No additional migration exists. Existing development databases initialized from old V1 require recreation; this is documented, and no user database was reset during implementation.

## Verification

- Baseline unit run: PASS (49 tests).
- Test-first repository regression: failed on former InvalidEventException for changed-content replay, proving the policy difference before implementation.
- Full Java 21 ./mvnw -q clean verify: PASS, 49 unit tests + 14 integration tests; zero failures, errors or skips.
- Repository integration verifies one persisted row and unchanged complete original model (including processedAt) after changed-content replay.
- HTTP/Kafka integration publishes an original, identical replay, changed replay and subsequent event; confirms original response unchanged, tail persistence and unchanged DLQ end offset.
- Fresh Testcontainers PostgreSQL schema initialization and Hibernate validation pass with the revised V1. No upgrade test is applicable because user explicitly declined upgrade migrations.
- Existing timestamp poison-message, failed DLQ recovery, batch/offset and transient storage-failure tests all remain green.
- Production source, schema and README scan finds no obsolete hash identifiers or conflict-to-DLQ claims. Historical plans/reports are not current source.
- git diff --check and staged checks: PASS.
- Current generated coverage: target/site/jacoco/index.html and jacoco.xml; 187/205 lines (91.2%), 31/44 branches (70.5%). Historical snapshot under codex was not refreshed, respecting the user's coverage directory preference.

## Independent review

checkpoint_6_review: PASS, no Critical or Important findings. Reviewed validation preservation, atomic deduplication/concurrency, replay test completion barriers, V1-only schema change and documentation. Reviewer performed no mutations or builds.

## Release procedure

Push checkpoint and verify SHAs; merge --no-ff into clean synchronized main; run full clean verify against merged main; push main and compare local/tracking/remote SHAs. Preserve the checkpoint branch. No hosted CI added.
