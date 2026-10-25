# Checkpoint 9 verification: integrate user simplifications

Date: 2026-10-25
Baseline: 3dcfe7a3f1583abe91b77f65c7f5110c7472d401
User changes preserved: 45eaa4e
Verified implementation: 7fb1dee9fae30bbfd0e32dc493c19be7a229ff09

## Changes

Preserved all three intentional user edits in their own commit: removed raw controller 64 KiB size check, HTTP 413 mapping, and codec timestamp bounds. Tests now expect raw text above the former limit to publish unchanged and parseable extended timestamps to pass codec and HTTP publication validation. README, OpenAPI and AsyncAPI reflect this behavior; ingress and Kafka limits still apply.

ProcessedEventService translates only PostgreSQL datetime overflow (SQLSTATE 22008) into InvalidEventException. That retains existing permanent-message DLQ handling without restoring a date-range policy. Other integrity errors and infrastructure outages retain retries. Existing batch-tail, offset and failed-DLQ publication tests are retained.

## Verification

- Main and origin/main matched the baseline SHA before the checkpoint. The intentional dirty edits were committed on the checkpoint branch, then main was restored to its clean baseline before isolated work.
- Initial changed-source unit audit: 8 expected failures against removed contracts (5 codec timestamp cases, typed timestamp HTTP 400 case, 2 raw HTTP 413 cases).
- New service regression RED: 8 focused service cases, 1 failure; timestamp overflow escaped as DataIntegrityViolationException instead of InvalidEventException.
- Full final Java 21 ./mvnw -q clean verify: PASS, 52 unit tests and 16 real Kafka/PostgreSQL integration tests, zero failures/errors/skips.
- Permanent error tests include PostgreSQL overflow at positive/negative year 300000 and Java Instant.MIN/MAX. Each verifies the exact original DLQ message/source metadata, absent poison row, successful tail processing and offset progress.
- Independent review raised a possible Java conversion failure before SQL. A focused real integration run of all four extrema passed and demonstrated SQLSTATE 22008 for all four with current dependencies; the reviewer withdrew the finding. The extra extrema remain covered in the final full gate.
- Service tests verify unrelated SQLSTATE values, absent SQLSTATE, absent SQL cause, and database outage exceptions are not reclassified as permanent message defects.
- HTTP integration verifies a 65,537-character malformed raw body is acknowledged by Kafka and reaches the DLQ unchanged. Unit test also covers multibyte UTF-8 content above the removed limit.
- OpenAPI and AsyncAPI YAML parsing: PASS. git diff --check: PASS.
- Fresh generated coverage target/site/jacoco/index.html: 167/176 lines (94.9%), 24/32 branches (75.0%). No generated report was copied under codex; earlier committed snapshots remain historical.

## Independent review

checkpoint_9_review: PASS after evidence review, no Critical or Important findings remain. No source change was necessary for the withdrawn finding; the final full gate includes the added integration cases. Reviewer performed no builds or modifications.

## Release procedure

Push checkpoint and compare local/tracking/remote SHAs, merge --no-ff into clean synchronized main, rerun clean verify on merged main, push main and compare local/tracking/remote SHAs. Preserve the checkpoint branch. No hosted CI added.
