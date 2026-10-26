# Checkpoint 20 verification: supported timestamp contract

Date: 2026-10-26
Baseline: ae658eb6278f4d4998c185429f5c0a5c6e6313af
User edits preserved separately: 5e013c5
Implementation: 12e0546

## Changes

User approved replacing persistence arithmetic checks with a shared input contract for UTC years0001–9999. FundOrderEventCodec now enforces inclusive bounds0001-01-01T00:00:00Z and9999-12-31T23:59:59.999999Z, after required-field validation. The upper endpoint uses PostgreSQL microsecond precision to avoid rounding into year10000. Ordinary nanosecond inputs inside the range remain accepted; UTC-normalized Instant value is validated, without a custom lexical date parser.

Typed HTTP requests outside the range return400 before Kafka publication; raw/direct Kafka content is rejected during consumption and recovered to the DLQ. Instant remains the model type. Removed toEpochMilli/multiplyExact checks from ProcessedEventService and retained defensive SQLSTATE22008 handling. Updated generated OpenAPI metadata, AsyncAPI and study notes.

Found existing main edits removing the retry listener and a timestamp comment. User explicitly authorized separate preservation and inclusion. Saved exact patch, committed on checkpoint, verified primary diff equality and reversed only the committed patch to restore clean main. No unexpected user work overwritten.

## Verification

- RED focused Java21 codec/controller/service tests:6failures across30tests; out-of-range codec inputs accepted, typed request expected400/actual202, old service error differed from the new contract.
- Final full Java21 ./mvnw clean verify: PASS,94unit/27integration tests, zero failures/errors/skips; /tmp/nordea-checkpoint-20-java21-verify.log.
- Final full Java25 mvn clean verify: PASS,94unit/27integration tests, zero failures/errors/skips; /tmp/nordea-checkpoint-20-java25-verify.log.
- Codec tests cover inclusive bounds, UTC-offset input, ordinary nanosecond precision and rejected range/upper-precision inputs. Service test checks no repository access for invalid timestamps; unrelated database failures retain tests.
- Existing FundOrderFlowIT extended with one out-of-range payload: typed400, raw202 followed by exact original-value DLQ record, source headers, absent stored event and successful following valid event. Deleted parameterized timestamp integration scenarios were not restored.
- Generated OpenAPI tests check bounds in field description; both Basic/JWT exports passed swagger-parser schema/reference validation. Edited AsyncAPI passed its parser. git diff --check passes.
- Independent checkpoint_20_review: no Critical or Important findings. Minor study-guide spacing corrected. Optional additional boundary database scenarios were not added; prior user removals respected.

Push checkpoint/compare SHAs, merge no-ff into clean synchronized main, run exact mvn verify on merged main and validate contracts, push/compare main. Preserve branch. No Docker service update required for a source/build change; local image can be rebuilt through the documented Compose command.
