# Explicit listener consumer group

Date: 2026-10-26. Baseline: `dc5487342e37a94a6322c300f64db5d5f904f22c`.

- Added `groupId = "${spring.kafka.consumer.group-id}"` to `FundOrderBatchListener`'s `@KafkaListener`, as requested. Existing environment configuration and integration-test group overrides remain in effect.
- Java 21 `./mvnw clean verify` passed: 64 unit tests and 14 integration tests; no failures/errors/skips. JaCoCo reports regenerated. The batch integration tests verify committed offsets in their explicitly configured consumer group.
- Independent static review by `messaging_it_review`: no Critical or Important findings.
- `git diff --check` passed. Build log: `/tmp/nordea-explicit-consumer-group-verify.log`.
- User's staged deletion of `ProcessedEventServiceTest` is preserved separately. Verification reflects that working-tree deletion. Commit only this listener change and verification record directly to `main`, under the user's workflow override.
