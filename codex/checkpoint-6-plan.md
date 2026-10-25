# Event ID deduplication simplification

Goal: remove hash comparison and treat every valid replay of an existing eventId as a successful no-op, preserving the first stored version.

Approved scope: user explicitly requested dropping the hash double check. Keep event validation, the unique event_id constraint, native ON CONFLICT DO NOTHING, per-record transactions, poison-message handling, and infrastructure retry/DLQ behavior.

- [x] Add a failing repository test for changed-content replay. Update the HTTP/Kafka replay test to assert no DLQ publication and later-record progress.
- [x] Remove hash generation/comparison, repository hash parameter/lookup, and entity mapping. Keep codec.validate at the service boundary. Remove the hash column directly from V1, as explicitly requested by the user; add no V2 migration.
- [x] Update tests for the simplified insert signature and document first-stored-version-wins semantics.
- [x] Run full clean verify, fresh-schema validation and diff checks; obtain independent review. Generate fresh coverage under target/site/jacoco without refreshing the historical codex report snapshot.
- [x] Record evidence, push checkpoint, merge --no-ff into clean synchronized main, verify merged main, push and compare local/tracking/remote SHAs.

Baseline: clean main/origin/main 9a317c51b67758c5be64e509871f5c33ddc26d48. Work occurs in a managed isolated worktree on codex/checkpoint-6-event-id-deduplication.

Review focus: duplicates never overwrite existing row, no conflict DLQ, malformed duplicates still fail validation, no pre-insert existence check/race, fresh V1 schema has no hash column, existing installations require database recreation, poison timestamp and transient failure behavior preserved.

## Verification results

- Baseline 49 unit tests passed.
- Red repository regression failed with the former InvalidEventException on changed-content replay.
- Implementation 5383870: shared validation retained, hash removed throughout production persistence and V1 schema, no V2 added.
- Complete clean verify: 49 unit tests and 14 integration tests, zero failures/errors/skips.
- Independent read-only review: PASS, no Critical or Important findings.
- Fresh coverage in target/site/jacoco: 187/205 lines (91.2%), 31/44 branches (70.5%). Historical committed snapshot unchanged.
- Source/reference scan and diff checks passed; historical checkpoint records remain historical. Release steps follow the user's merge workflow.
