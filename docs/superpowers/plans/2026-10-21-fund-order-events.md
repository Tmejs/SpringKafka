# Fund Order Events Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a runnable Java 21 Spring Boot application that publishes fictional fund order events, consumes them in Kafka batches, persists valid events, isolates invalid records in a DLQ, and exposes authenticated reads.

**Architecture:** Both producer endpoints publish UTF-8 strings to one Kafka topic. A batch listener parses and validates each record, uses an idempotent PostgreSQL insert, and identifies a failed record to Spring Kafka's error handler. A Basic-authenticated query API reads the PostgreSQL view.

**Tech Stack:** Java 21, Spring Boot 4.1.1, Maven, Spring Kafka, Spring Web, Spring Validation, Spring JDBC, Spring Security, Spring Actuator, PostgreSQL, Flyway, JUnit 5, Testcontainers, JaCoCo, Docker Compose.

**Spec:** [Fund order event design](../specs/2026-10-21-fund-order-events-design.md)

## Global Constraints

- Java 21 and Spring Boot; no real trading, tax, or customer data.
- `FundOrderStatusEvent` has exactly the seven fields and enum values in the spec; `schemaVersion` is `1`.
- The typed and raw POSTs publish to the same configurable topic as strings; raw consumes `text/plain` unchanged.
- Use at-least-once semantics and a PostgreSQL unique `eventId` constraint. Do not claim cross-system exactly-once processing.
- Invalid content is nonretryable and goes to the configurable DLQ; infrastructure errors retry with a delay and do not advance the failed offset.
- Only Basic authentication is implemented; JWT is a documented extension point.
- Tests are local; do not add GitHub Actions. Preserve the user's checkpoint, review, push, and `--no-ff` merge workflow when a remote is available.

## File map

- `pom.xml`, `.mvn/wrapper/*`, `mvnw`: build, test phases, coverage; Spring Boot 4.1.1 dependency management.
- `src/main/java/com/example/fundorders/FundOrdersApplication.java`: entry point.
- `event/FundOrderStatusEvent.java`, `event/OrderStatus.java`, `event/InvalidEventException.java`, `event/FundOrderEventCodec.java`: public event contract, JSON conversion, and validation.
- `publish/EventPublisher.java`, `publish/PublishReceipt.java`, `publish/EventPublishController.java`: Kafka publication and two POST endpoints.
- `store/ProcessedEventRepository.java`, `store/ProcessedEventService.java`, `store/ProcessedFundOrderEvent.java`: idempotent insert and query model.
- `consume/FundOrderBatchListener.java`, `consume/KafkaBatchConfiguration.java`: batch processing and DLQ/retry configuration.
- `read/FundOrderQueryController.java`, `web/ApiExceptionHandler.java`: paginated reads and HTTP errors.
- `security/SecurityConfiguration.java`, `security/BasicCredentialsProperties.java`: Basic security and validated external credentials.
- `src/main/resources/application.yml`, `db/migration/V1__create_fund_order_events.sql`: externalized defaults and schema.
- `src/test/java/com/example/fundorders/`: unit and integration tests mirroring ownership above.
- `compose.yaml`, `Dockerfile`, `.env.example`, `deploy/kubernetes/*.yaml`: local and probe-ready deployment.
- `docs/api/openapi.yaml`, `docs/api/asyncapi.yaml`, `README.md`, `codex/reports/coverage/`: contracts, demonstration, and final verification evidence.

## Review Focus

1. Malformed raw text reaches Kafka and exactly one DLQ record; add this to Task 5 integration tests.
2. Well-formed JSON with an invalid enum, missing required field, or unsupported schema version goes to DLQ; `REJECTED` persists as valid; add this to Task 3 tests and Task 5 integration tests.
3. A repeated `eventId` with identical content is a no-op, while different content is rejected; add this to Task 2 tests and Task 5 integration tests.
4. A failed DLQ publish leaves the source record pending; add this to Task 3 tests.
5. A PostgreSQL outage causes delayed retry with no DLQ publication; add this to Task 3 tests.

---

### Task 1: Event contract and producer APIs

**Files:** Create `pom.xml`, wrapper, application entry point, `event/*`, `publish/*`, `web/ApiExceptionHandler.java`, baseline `application.yml`; test `event/FundOrderEventCodecTest.java`, `publish/EventPublisherTest.java`, `publish/EventPublishControllerTest.java`.

**Interfaces:** Produce `FundOrderStatusEvent(int schemaVersion, UUID eventId, UUID orderId, String partnerCode, String fundIsin, OrderStatus status, Instant occurredAt)`, `FundOrderEventCodec.encode(FundOrderStatusEvent): String`, `decode(String): FundOrderStatusEvent`, `EventPublisher.publish(FundOrderStatusEvent): PublishReceipt`, `publishRaw(String): PublishReceipt`. `PublishReceipt` contains topic, partition, offset, and key. Later tasks consume the codec and event type.

- [ ] **Step 1: Bootstrap the minimal Maven build and write failing contract tests.** `decode(validJson)` returns all fields; malformed JSON, unknown status, missing `eventId`, bad ISIN format, and wrong `schemaVersion` throw `InvalidEventException`. `encode` round-trips a valid event.
- [ ] **Step 2: Run `./mvnw -q -Dtest=FundOrderEventCodecTest test`.** Expect test failure before implementation.
- [ ] **Step 3: Implement the event model and codec.** Use Jackson and Jakarta Validation; accept only schema version `1`; keep error text safe for API/DLQ metadata.
- [ ] **Step 4: Write failing producer/API tests.** Assert typed invalid body returns `400` without sending; valid typed and raw text return `202` after broker acknowledgment; raw `not-json` is sent unchanged; send failure returns a service error.
- [ ] **Step 5: Run focused producer/API tests.** Expect failures before implementation.
- [ ] **Step 6: Implement publisher and POST controller.** Use `KafkaTemplate<String,String>`, key typed events by `orderId`, give raw records a generated key, and bound send wait and raw body size.
- [ ] **Step 7: Run Task 1 tests and `git diff --check`.** Expect all focused tests passing and no whitespace errors.
- [ ] **Step 8: Commit this focused deliverable on the checkpoint branch.**

### Task 2: PostgreSQL view and normal batch consumption

**Files:** Create migration, `store/*`, `consume/FundOrderBatchListener.java`; test `store/ProcessedEventRepositoryIT.java`, `store/ProcessedEventServiceTest.java`, `consume/FundOrderBatchListenerTest.java`.

**Interfaces:** `ProcessedEventService.process(FundOrderStatusEvent): void` inserts or checks an event; `ProcessedEventRepository.findByEventId(UUID)` and `findPage(Pageable)` serve reads; `FundOrderBatchListener.onMessages(List<ConsumerRecord<String,String>>): void` calls codec then service in record order. Task 3 supplies its error handler.

- [ ] **Step 1: Write failing persistence tests.** A PostgreSQL container verifies that first insert stores one row, identical replay stores no second row, reused `eventId` with different content throws `InvalidEventException`, and pages have a stable `occurredAt, eventId` order.
- [ ] **Step 2: Run focused persistence tests.** Expect failures before implementation.
- [ ] **Step 3: Implement migration and idempotent repository/service.** Use PostgreSQL `INSERT ... ON CONFLICT DO NOTHING`, compare stored canonical content on conflict, and transact each record. Keep the database unique constraint as the concurrency guard.
- [ ] **Step 4: Write failing listener tests.** For valid A/B, service is called in order; for valid A, invalid B, valid C, the listener throws `BatchListenerFailedException` identifying B and never calls service for C in that invocation; for a storage exception it identifies the same record without classifying it as invalid content.
- [ ] **Step 5: Run focused listener tests.** Expect failures before implementation.
- [ ] **Step 6: Implement the batch listener.** Receive `List<ConsumerRecord<String,String>>`, parse and validate per record, then persist. Wrap failures with the exact failing `ConsumerRecord` and cause.
- [ ] **Step 7: Run Task 2 tests and `git diff --check`.** Expect pass and clean diff.
- [ ] **Step 8: Commit this focused deliverable.**

### Task 3: Kafka batch error handling and DLQ

**Files:** Create `consume/KafkaBatchConfiguration.java`, Kafka settings in `application.yml`; test `consume/KafkaBatchConfigurationTest.java` and extend listener tests.

**Interfaces:** A batch listener factory uses `DefaultErrorHandler` and `DeadLetterPublishingRecoverer`. `InvalidEventException` causes immediate recovery; other exceptions use configurable delayed unlimited attempts. Recovery targets `${app.kafka.dlq-topic}`. Later integration tests assert public behavior.

- [ ] **Step 1: Write failing handler tests.** Assert the batch factory is enabled; malformed JSON, unknown status, missing required fields, unsupported schema version, and conflicting duplicate IDs are nonretryable; infrastructure and unexpected exceptions remain retryable; a failed DLQ send is surfaced rather than treated as recovered.
- [ ] **Step 2: Run focused Kafka configuration tests.** Expect failures before implementation.
- [ ] **Step 3: Implement the factory and recoverer.** Disable Kafka auto commit, use container batch acknowledgment, preserve source partition where DLQ topology permits, wait for DLQ send result, and externalize topic/group/max-poll-records/concurrency/backoff/send timeout. Configure retry telemetry after repeated failures.
- [ ] **Step 4: Run Task 3 tests and `git diff --check`.** Expect pass and clean diff.
- [ ] **Step 5: Commit this focused deliverable.**

### Task 4: Authenticated query API and health

**Files:** Create `read/FundOrderQueryController.java`, `security/*`; extend `web/ApiExceptionHandler.java`, `application.yml`; test `read/FundOrderQueryControllerTest.java`, `security/SecurityConfigurationTest.java`.

**Interfaces:** `GET /api/v1/fund-order-events?page=0&size=20` returns a page (max size 100); `GET /api/v1/fund-order-events/{eventId}` returns one persisted event or `404`. Basic credentials are externally configured. Only `/actuator/health`, `/actuator/health/liveness`, and `/actuator/health/readiness` are anonymous.

- [ ] **Step 1: Write failing web/security tests.** No credentials yield `401`; valid credentials read a page; wrong credentials yield `401`; unknown event yields `404`; typed invalid request yields `400`; pagination is stable and capped; only probe paths are public.
- [ ] **Step 2: Run focused web/security tests.** Expect failures before implementation.
- [ ] **Step 3: Implement Basic security and query controller.** Validate required credential properties at startup, hash the configured password for the in-memory user service, and expose only designated health paths anonymously.
- [ ] **Step 4: Run Task 4 tests and `git diff --check`.** Expect pass and clean diff.
- [ ] **Step 5: Commit this focused deliverable.**

### Task 5: Container-backed end-to-end verification

**Files:** Create `src/test/java/com/example/fundorders/FundOrderFlowIT.java` and test fixtures; configure Maven Failsafe and JaCoCo in `pom.xml`.

**Interfaces:** Testcontainers supplies Kafka and PostgreSQL properties dynamically; HTTP calls hit a random server port. The test consumes the DLQ with its own group to inspect raw values and metadata.

- [ ] **Step 1: Write failing integration tests.** Typed POST becomes visible through authenticated GET; valid raw JSON and a `REJECTED` business status also become visible; malformed raw text and semantically invalid JSON each create one DLQ record; valid-invalid-valid processes both valid events; identical replay yields one row and conflicting replay reaches DLQ.
- [ ] **Step 2: Run `./mvnw -q -Dit.test=FundOrderFlowIT verify`.** Expect failure before wiring is complete.
- [ ] **Step 3: Complete test wiring and resolve real broker/database behavior.** Assert DLQ value, source topic/offset/error headers, and no invalid row. Exercise DLQ publication failure and PostgreSQL retry at the narrowest reliable layer if container fault injection is impractical.
- [ ] **Step 4: Run `./mvnw verify`.** Expect unit and integration tests passing plus JaCoCo HTML/XML reports.
- [ ] **Step 5: Commit focused integration and coverage configuration.**

### Task 6: Deployment, contracts, and submission record

**Files:** Create `compose.yaml`, `Dockerfile`, `.env.example`, `deploy/kubernetes/deployment.yaml`, `docs/api/openapi.yaml`, `docs/api/asyncapi.yaml`, `README.md`, final coverage copy under `codex/reports/coverage/`; adjust application configuration where deployment reveals gaps.

**Interfaces:** `docker compose up --build` starts app, Kafka, and PostgreSQL; health probes respond on configured Actuator paths; README shows exact valid/invalid publish and read commands.

- [ ] **Step 1: Add runnable Compose and image configuration.** Verify a clean Compose startup, authenticated read, typed publish, raw malformed publish, and DLQ inspection from documented commands.
- [ ] **Step 2: Add Kubernetes Deployment example.** Use startup/readiness/liveness probes and externalized secrets/settings; verify manifest syntax and probe paths against the running app.
- [ ] **Step 3: Add OpenAPI and AsyncAPI contracts.** Check they match actual endpoints, security, event fields, Kafka topic, and DLQ semantics.
- [ ] **Step 4: Run full `./mvnw verify`, inspect JaCoCo HTML/XML, and copy the generated report into `codex/reports/coverage/` with the tested commit identified.**
- [ ] **Step 5: Complete README, `git diff --check`, independent review, and any focused fixes/re-review.** Record verification and review results under `codex/`.
- [ ] **Step 6: Commit the final records.** Follow the user's checkpoint workflow: confirm local and remote state, push checkpoint, compare SHAs, merge with `--no-ff`, verify merged `main`, push, and compare SHAs. If no remote exists, report the push/merge blocker without inventing one.

## Self-review

The six tasks cover every design section. Task 1 owns HTTP validation and transport; Tasks 2–3 own normal and failure processing; Task 4 owns secure reads and probes; Task 5 proves end-to-end behavior; Task 6 packages deployment, contracts, coverage, and interview evidence. Review Focus items map to named tests in Tasks 2, 3, and 5. The raw endpoint's generated key is intentionally separate from typed per-order ordering. The plan makes no time estimate.
