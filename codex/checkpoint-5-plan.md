# Poison-message handling implementation plan

Goal: prevent otherwise valid events with unsupported occurredAt timestamps from entering an infinite database retry loop.

Design: the shared codec accepts UTC instants from 0001-01-01T00:00:00Z through 9999-12-31T23:59:59.999999Z inclusive. This fits four-digit event dates and PostgreSQL storage; the microsecond upper bound avoids rounding into year 10000. Typed publication rejects invalid dates with HTTP 400; raw and direct Kafka records fail as InvalidEventException and use existing DLQ recovery. Keep transient storage errors retryable and failed DLQ publication pending.

User authorized preserving existing edits separately: commit 4dd97a4 contains the original four modified files. Main/origin baseline 896d2d1 was synchronized; main checkout is now clean. Work continues on codex/checkpoint-5-poison-messages in an isolated worktree.

- [x] Add failing codec boundary/overflow tests, typed HTTP rejection, and actual batch poison-message regressions using Kafka/PostgreSQL.
- [x] Add the shared codec range check after existing null/field validation; update API contracts and README.
- [x] Prove rejected poison records are absent from storage, original payload and source metadata reach DLQ, subsequent records persist, and offsets advance. Cover positive and negative PostgreSQL-overflow dates. Verify valid endpoint dates actually persist.
- [x] Run complete clean verify and independent review; preserve existing database-outage and failed-DLQ recovery tests.
- [x] Record evidence, generate fresh coverage under Maven target/site/jacoco, push checkpoint, merge --no-ff into clean synchronized main, verify merged main, push and compare SHAs.

Review focus: inclusive bounds, missing timestamps, typed HTTP 400 versus raw acceptance, real multi-record delivery, database non-persistence of invalid event, DLQ identity and metadata, later record progress, and no broad database-exception reclassification.

## Completed implementation evidence

- User edits preserved first in 4dd97a4; fix and regression tests committed in fd8f77c.
- Red unit run: 6 intended failures (5 unsupported timestamps plus typed HTTP acceptance).
- Red Kafka run: PostgreSQL SQLSTATE 22008 repeats and later-record assertion times out; the pending poison also prevents the next parameter case from making progress.
- Green complete clean verify: 49 unit tests and 14 integration tests, no failures/errors/skips.
- Independent read-only review: PASS, no Critical or Important findings.
- Fresh coverage: target/site/jacoco, 197/217 lines (90.8%), 35/48 branches (72.9%). The dated committed snapshot under codex is left unchanged, honoring the user's objection to publishing new coverage there and keeping this checkpoint focused on poison-message handling.
- OpenAPI/AsyncAPI YAML parsing and diff checks passed. Remaining release operations follow the existing Git workflow.
