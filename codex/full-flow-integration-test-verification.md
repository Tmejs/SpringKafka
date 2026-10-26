# Full-flow integration test verification

Date: 2026-10-26. Baseline: user's local commit `5d09e39`.

- Added a focused `FundOrderFlowIT`, with two parameterized cases for typed JSON and raw text publication.
- Each case authenticates a MockMvc POST, expects Kafka publication acknowledgement, and waits for the same unique event ID to appear through the real `ProcessedEventRepository`. All persisted business fields and the processing timestamp are verified. No mocks, direct database inserts, or direct test Kafka publication are used.
- Reuses `MessagingIntegrationTestSupport` with the actual publisher, Kafka consumer, PostgreSQL and Spring-managed containers. Requests use MockMvc rather than network HTTP.
- Updated reviewer guide links to match the current test suite. User-deleted tests stay removed; unrelated local JWT test formatting edits are preserved separately.
- Java 21 `./mvnw clean verify`: BUILD SUCCESS; 39 unit tests and 11 integration tests, zero failures/errors/skips. The new class contributed two passing integration cases. JaCoCo HTML/XML regenerated under `target/site/jacoco/`.
- Independent static review by `messaging_it_review`: no Critical or Important findings. Reviewed actual message flow, isolation, fresh repository polling, assertions and container lifetime.
- `git diff --check` passed. Build log: `/tmp/nordea-full-flow-it-verify.log`.
- Commit directly to `main` under the user's project workflow override, preserving their existing local commit and separate JWT edits.
