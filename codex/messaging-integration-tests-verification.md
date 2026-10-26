# Messaging integration tests — verification

Date: 2026-10-26. Baseline: `39d546a9ec394ef71709ccf44121963dea823d3c`.

## Implemented

- `EventPublishControllerIT`: typed HTTP publication; raw JSON including whitespace; raw malformed Unicode text. Native Kafka consumers verify actual topic, partition, offset, key and payload against HTTP receipts.
- `FundOrderConsumerIT`: native Kafka publication verified directly in PostgreSQL; malformed JSON and unsupported schema version verified in the DLQ, with original source metadata and preserved payload/key. A following valid event in the same partition must persist; invalid event IDs must remain absent.
- `MessagingIntegrationTestSupport`: shared Spring-managed Kafka/PostgreSQL containers and service connections. Unique event IDs and explicit observation offsets isolate assertions. Both new classes reuse one application context and container pair.
- Reviewer guide updated to link current integration tests. Existing user changes and test deletions preserved; no production code changes.

## Checks

Java 21.0.8, Docker, Kafka `apache/kafka:4.0.0`, PostgreSQL `postgres:16-alpine`.

1. Focused run: `./mvnw -q test-compile dependency:properties -Dit.test=EventPublishControllerIT,FundOrderConsumerIT failsafe:integration-test failsafe:verify` — exit 0; six cases passed.
2. Complete gate: `./mvnw clean verify` — BUILD SUCCESS; 65 unit tests and 14 integration tests, zero failures/errors/skips. Failsafe XML reports confirm three cases in each new class. JaCoCo HTML/XML generated at `target/site/jacoco/`.
3. `git diff --check` — passed.
4. Independent static review by `messaging_it_review` — no Critical or Important findings. Reviewed actual IO assertions, container/context lifetime, isolation, flakiness and requirement coverage.
5. Before commit, fetch confirmed local `main` and `origin/main` matched the baseline above. Changes committed directly to `main` under the user's project-specific workflow override.

Build logs for this local run: `/tmp/nordea-messaging-focused-it.log` and `/tmp/nordea-messaging-verify.log`. Maven XML reports and coverage output are generated build artifacts.

## Follow-up: repository-based assertions

At the user's request, `FundOrderConsumerIT` now injects the existing `ProcessedEventRepository` bean instead of `JdbcTemplate`. `findByEventId` verifies persistence and absence; typed model accessors retain all field assertions. Kafka clients, DLQ assertions and test scenarios are unchanged.

- Java 21 `./mvnw clean verify` — BUILD SUCCESS; 65 unit tests and 14 integration tests, zero failures/errors/skips. Coverage reports regenerated. Log: `/tmp/nordea-repository-it-verify.log`.
- Independent static follow-up review by `messaging_it_review` — no Critical or Important findings; confirmed repository reads occur anew on each Awaitility poll without an enclosing test transaction.
- `git diff --check` — passed. Direct `main` commit under the user's workflow override.

## Follow-up: MockMvc publication tests

`EventPublishControllerIT` now performs requests through autowired `MockMvc` with Basic credentials and explicit UTF-8 encoding. Shared test support uses `@SpringBootTest(webEnvironment = MOCK)` and `@AutoConfigureMockMvc`; actual security filters, publisher, Kafka and PostgreSQL remain active. Both new classes reuse the same context and containers. Kafka payload/key/receipt assertions are unchanged. `FundOrderFlowIT` continues to exercise network HTTP.

- Java 21 `./mvnw clean verify` — BUILD SUCCESS; 65 unit tests and 14 integration tests, zero failures/errors/skips; coverage reports regenerated. Log: `/tmp/nordea-mockmvc-it-verify.log`.
- Independent static follow-up review by `messaging_it_review` — no Critical or Important findings; confirmed security filters, UTF-8 preservation, context sharing and actual broker assertions.
- Reviewer guide updated; `git diff --check` passed. Direct `main` commit under the user's workflow override.
