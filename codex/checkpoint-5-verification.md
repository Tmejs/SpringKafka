# Checkpoint 5 verification: poison-message handling

Date: 2026-10-25
Baseline: 896d2d18501099fa88d80870522d8ffbf345cd63
User edits preserved separately: 4dd97a4
Verified fix implementation: fd8f77c

## Root cause and fix

The codec accepted Instant values beyond PostgreSQL TIMESTAMPTZ range. PostgreSQL rejected the insert with SQLSTATE 22008, while the listener retried the deterministic database error indefinitely. The shared codec now rejects timestamps outside 0001-01-01T00:00:00Z through 9999-12-31T23:59:59.999999Z inclusive before publishing or persistence. The upper endpoint avoids microsecond rounding into year 10000.

Typed HTTP requests reject these events with 400 before publication. Raw/direct Kafka events raise InvalidEventException and use existing DLQ recovery. The original payload/source metadata is retained. Database-outage retries and failed-DLQ recovery are unchanged; no broad database exception reclassification was introduced.

## Verification evidence

- Original user edits: baseline unit suite passed before their separate preservation commit. User expressly authorized committing these edits before the fix.
- New unit tests first failed on five unsupported timestamp inputs and typed HTTP acceptance (6 failures, no errors).
- Kafka regression before fix: repeated PostgreSQL SQLSTATE 22008, timeout waiting for later-record processing. The next parameter case also could not deliver its batch because the earlier poison remained pending. Initial direct Failsafe invocation lacked the Mockito agent property; rerun with dependency:properties resolved this harness issue before reproducing the application defect.
- Full Java 21 ./mvnw -q clean verify after fix: PASS, 49 unit tests and 14 integration tests; zero failures, errors or skips.
- New integration coverage: positive and negative PostgreSQL-overflow timestamps in actual three-record batches; earlier/later valid records persist, poison row absent, exactly one DLQ record with unchanged payload and original offset metadata, committed source offset advances past the tail.
- Additional integration coverage: exact lower and upper accepted timestamps persist and round-trip through PostgreSQL without DLQ publication.
- Existing infrastructure outage and failed-DLQ publication tests still pass.
- Codec tests cover both publication/consumption, rejected bounds and inclusive accepted bounds. Controller regression verifies 400 and no publisher invocation.
- OpenAPI and AsyncAPI YAML parsing: PASS. git diff --check and staged checks: PASS.
- Fresh generated coverage under target/site/jacoco: 197/217 lines (90.8%), 35/48 branches (72.9%). The historical committed snapshot is tied to its documented source commit and was not refreshed under codex, per the user's directory preference.

## Independent review

checkpoint_5_review reviewed source, tests, contracts, validation/null handling, DLQ routing, offsets and preserved user edits against the baseline. PASS: no Critical or Important findings. No additional source fixes requested.

## Release procedure

Push checkpoint and compare local/upstream/remote SHAs; merge --no-ff into clean synchronized main; run full clean verify on merged main; push main and compare local/origin/remote SHAs. Preserve completed checkpoint branch. No hosted CI added.
