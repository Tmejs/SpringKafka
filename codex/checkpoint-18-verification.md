# Checkpoint 18 verification: mvn verify failures

Date: 2026-10-26
Baseline: 2a627d41d0a9d6107c0968b658bae8c93c04264e
User edits preserved separately: 389fae7

## Investigation and changes

The original failed Failsafe report identified a Java25 JDBC Timestamp.from conversion overflow. Hibernate wrapped IllegalArgumentException/ArithmeticException as InvalidDataAccessApiUsageException before PostgreSQL could return SQLSTATE22008. The listener therefore retried forever; subsequent batch scenarios encountered the stuck offset and timed out. Original report copied to /tmp/nordea-verify-original-failure.xml before rerunning.

User explicitly authorized preserving their changes: shortened README coverage paragraph and removal of six parameterized timestamp integration cases. Committed on checkpoint branch separately, returned primary checkout to clean main, then continued in a managed worktree. Deleted cases were not restored. Exact mvn verify with retained tests initially passed on Java25 (90unit/27integration).

ProcessedEventService now checks timestamp epoch-millisecond arithmetic and JDBC's whole-second multiplication before insertion. Only arithmetic overflow in these timestamp checks becomes InvalidEventException and uses the existing permanent-failure DLQ path. PostgreSQL SQLSTATE22008 handling remains, as do retries for unrelated database/application errors. Codec and publication behavior remain asynchronous; no new HTTP timestamp range is imposed.

One focused unit test exercises both an extreme Instant and the negative epoch-millisecond boundary and verifies repository access never occurs. Removed stale demo references to deleted tests and clarified AsyncAPI timestamp failure behavior.

## Evidence

- Initial service regression RED: expected an InvalidEventException but no exception occurred.
- Review found toEpochMilli alone misses Instant.ofEpochMilli(Long.MIN_VALUE): Java25 Timestamp.from multiplies whole seconds first and overflows. Strengthened unit reproduced RED; added multiplyExact check and re-reviewed.
- Final Java25 Maven3.9.6: mvn clean verify PASS,91unit/27integration tests, zero failures/errors/skips. Log: /tmp/nordea-checkpoint-18-java25-final.log.
- Final Java21 wrapper: ./mvnw clean verify PASS,91unit/27integration tests, zero failures/errors/skips. Log: /tmp/nordea-checkpoint-18-java21-final.log.
- AsyncAPI parser validates the edited document; git diff --check passes.
- Independent checkpoint_18_review: no remaining Critical or Important findings after the boundary fix.

## Integration

Push checkpoint and compare local/tracking/remote SHAs, merge no-ff into clean synchronized main, run exact mvn verify on merged main using the user's default Java25, push main and compare SHAs. Preserve branch. No local Compose service changes required.
