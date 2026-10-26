# Recruiter review and interview study guide

Originally reviewed on 26 October 2026 against commit `ecbb650`; retry-logging notes updated with subsequent changes.

Use this alongside the [requirements map](../readme-reviewer.md). The answers below describe this implementation; improvement proposals are explicitly identified and are not implemented by this document.

## 1. Assessment

This is a credible functional submission: it demonstrates a real Kafka batch listener, authenticated publication and queries, PostgreSQL persistence, DLQ recovery, external configuration, probes and meaningful integration tests. Its strongest evidence is the controlled multi-record batch test and the real signed-JWT HTTP tests.

The main interview challenge is the phrase **production-ready**. The code demonstrates important reliability mechanisms, but operational policies, realistic load results, deployment hardening and a current published coverage artifact still need attention. Present these limits openly instead of promising a complete banking production platform.

At the original reviewed baseline, the latest local reports contain **91 unit tests and 27 integration tests**, with no failures. Fresh coverage is **238/247 lines (96.4%) and 41/52 branches (78.8%)**. These describe that run, not a permanent guarantee; the checked-in coverage snapshot is older.

### Improvements worth considering

| Priority | Current evidence | Suggested improvement |
| --- | --- | --- |
| Before submission | The [committed coverage snapshot](../codex/reports/coverage/README.md) describes commit `88ec6d0` from 22 October and includes classes that have since been removed. | Generate coverage from the final submission and deliver the matching HTML/XML with source provenance. A reproducible command alone does not make the committed snapshot current. |
| Small correctness safeguard | [Topic properties](../src/main/java/com/example/fundorders/config/KafkaTopicProperties.java) validate nonblank names but allow identical source and DLQ names. | Reject equal names at startup. Otherwise, invalid records can be recovered back into their own input topic indefinitely. Also validate retry durations/concurrency where currently supplied through `@Value`. |
| Strong interview evidence | There is batch correctness coverage but no recorded throughput/latency measurement. | Measure realistic batches and database latency, including slow processing. Report throughput, latency, lag and poll duration before choosing bulk writes. |
| Before real deployment | [Retry handling](../src/main/java/com/example/fundorders/config/KafkaBatchConfiguration.java) retries retryable failures indefinitely; the custom periodic retry listener has been removed; Spring fatal defaults also send some programming/conversion failures straight to recovery. Only health is exposed through Actuator configuration. | Review the full exception classification policy, including whether programming errors should be recovered, stopped or escalated. Define lag/retry/DLQ alerts, processing and publication metrics, operational ownership and escalation. Keep message identifiers out of metric labels; use structured logs for those. |
| Before real deployment | Producer durability settings such as `acks` and `enable.idempotence` are not explicit in application configuration. | Make the intended settings explicit and verify the effective configuration. Choose broker replication, minimum in-sync replicas, retention and Kafka authentication/TLS for the actual environment. |
| Before real deployment | The raw endpoint reads unrestricted text into memory; Kubernetes has no supplied ingress limits. | Set and verify ingress body-size limits, request quotas and timeouts. A Kafka size limit rejects too late to bound HTTP body allocation. |
| Before real deployment | The [deployment example](../deploy/kubernetes/deployment.yaml) uses external ConfigMap/Secret references and a placeholder image; there is no supplied nonroot security context or network policy. [Kafka UI](../compose.yaml) uses `latest`. | Pin an approved UI image, provide real deployment resources and harden the runtime. Decide on TLS, nonroot execution, secret delivery and access to DLQ data. Compose Kafka has no declared persistent data volume, so it is a local demo, not durable infrastructure. |
| Additional reliability evidence | Replay and failure classification are tested, but there is no explicit concurrent duplicate-insert, real database-outage recovery, rebalance or crash-window test. | Pick tests that target the most consequential remaining failure windows. Do not expand every boundary case merely to raise coverage. |
| Contract decision | Reused `eventId` with different content is silently ignored; the API scopes are broad, without customer/partner ownership filtering. | Document first-write-wins and the intended internal API audience. Confirm whether production needs conflict visibility or finer data authorization. Those are requirements decisions, not automatic reasons to add payload hashing or customer models. |

If there is little time, prioritize a current coverage artifact, configuration safeguards and a measured explanation of batching. Avoid a large architectural rewrite immediately before presenting.

## 2. A 60-second introduction

> This service accepts fictional investment-fund order status events. A typed endpoint validates JSON before publishing, while a raw-text endpoint lets the consumer demonstrate malformed-message handling. Both publish to the same configurable Kafka topic.
>
> The listener receives batches but processes each record in its own database transaction. Permanent content failures go to the DLQ; infrastructure failures retain delayed retries. PostgreSQL enforces idempotency by event ID because Kafka offsets and database commits are not atomic together.
>
> Local access uses Basic authentication, and the deployment JWT profile validates tokens and separates read/write scopes. Tests exercise real Kafka and PostgreSQL, actual multi-record delivery, offset recovery and signed bearer tokens. I would describe this as a production-oriented reference implementation with explicit operational limits.

## 3. Architecture and batching

### 1. Walk me through one valid request

The [publish controller](../src/main/java/com/example/fundorders/controller/EventPublishController.java) validates the typed event and calls [EventPublisher](../src/main/java/com/example/fundorders/service/EventPublisher.java). After Kafka acknowledges publication, the API returns 202 and a receipt. The [listener](../src/main/java/com/example/fundorders/kafka/FundOrderBatchListener.java) later decodes the record and calls the [transactional service](../src/main/java/com/example/fundorders/service/ProcessedEventService.java). A GET reads the PostgreSQL model; it may initially return 404 because consumption is asynchronous.

### 2. Is this really batch processing?

Yes: the container enables batch delivery, and the listener receives `List<ConsumerRecord<String, String>>`. The application limits poll delivery using `max.poll.records`, but a delivery can contain fewer records. It is **not bulk database processing**: the loop invokes a separate service transaction for every record. That makes partial failures straightforward, at the cost of transaction overhead. [Kafka documents the distinction between poll limits and underlying fetches](https://kafka.apache.org/42/configuration/consumer-configs/).

### 3. Why not persist the whole list in one transaction?

A transaction per batch could reduce overhead, but a failure would roll back earlier writes in that transaction. I could not then let the existing handler commit those earlier records as successful without redesigning recovery. Options include replaying the whole transaction, validating first and bulk-writing valid records, or processing smaller transactional chunks. Each changes the meaning of partial success. I would benchmark first; setting a Hibernate batching property alone does not combine this loop's native queries into a bulk operation.

### 4. How do your tests prove a real batch?

[KafkaBatchProcessingIT](../src/test/java/com/example/fundorders/kafka/KafkaBatchProcessingIT.java) stops the listener, preloads three records into one partition, and then starts it. A test interceptor records the actual delivered offsets. The test asserts one delivery includes all three, then checks stored rows, DLQ metadata and broker-committed offsets. Sending three REST requests close together would not prove that they arrived in one poll.

### 5. Why receive ConsumerRecord<String, String> instead of the event model?

The record provides topic, partition, offset, key, value and headers. Keeping the value as text lets the application identify a specific failing record and preserve malformed JSON in the DLQ. The codec then separates parsing and validation from persistence. A typed Kafka deserializer is possible, but errors occurring before listener invocation require an appropriate deserialization error-handling path.

## 4. Offsets, delivery and recovery

### 6. Explain a valid–invalid–valid batch, including offsets

For one partition, assume A is at offset 10, invalid B at 11, and C at 12:

| Step | Result | Committed next offset |
| --- | --- | --- |
| A completes | Its database transaction commits. | The error handler can commit 11 when B fails. |
| B fails permanently | Listener throws `BatchListenerFailedException` identifying B; recovery publishes B to the DLQ. | After successful recovery, 12. |
| C is redelivered and completes | Its database transaction commits. | 13 after successful processing/commit. |

The listener stops at B, so C is not processed on the initial invocation. Offsets are per partition, and a committed offset identifies the next record to read. This example assumes successful offset commits; crashes can cause replay. [Spring Kafka documents this failed-record batch recovery sequence](https://docs.spring.io/spring-kafka/reference/kafka/annotation-error-handling.html).

### 7. What belongs in the DLQ?

A permanent message defect: malformed JSON, unsupported schema version, or invalid required fields/status. The current code maps those cases to `InvalidEventException`. Adding it to the nonretryable list preserves Spring Kafka's built-in fatal classifications: a `ClassCastException`, for example, also goes straight to recovery even if it indicates a code bug. That broader policy deserves review before production. `REJECTED` is a legitimate business status and is stored. An ordinary database outage should retain retries, rather than turn all valid events into DLQ entries. Typed HTTP validation failures return 400 before publication and never reach the DLQ.

### 8. What happens if the database or DLQ is unavailable?

A database infrastructure failure retains the failed source record for delayed retry. If DLQ publication fails, the recoverer throws because send-result checking is enabled; the failed record and later records remain pending. The [failed-DLQ-send integration test](../src/test/java/com/example/fundorders/kafka/KafkaBatchProcessingIT.java) proves that publication boundary using controlled failure injection, not a real broker outage. Other partitions may also experience delay if they share the same blocked consumer thread.

### 9. Are infinite retries safe?

Indefinite retries apply to exceptions classified as retryable. They avoid discarding valid data during an outage, but an unclassified permanent bug can block progress indefinitely. Production needs alerts and an operator decision after prolonged failure, not blind retries without ownership. For long retry delays I would evaluate container-pausing backoff; adding `@RetryableTopic` directly is not a supported solution for a batch listener. [Spring Kafka describes the relevant backoff and batch restrictions](https://docs.spring.io/spring-kafka/reference/kafka/annotation-error-handling.html).

### 10. How are failed DLQ messages recovered later?

There is no automatic DLQ consumer. An operator inspects source-topic/partition/offset and error headers, identifies the cause, and fixes code or corrects an invalid message under a deliberate policy. Replay must preserve provenance and appropriate event/order identity, choose the correct key, and monitor the result. It must not loop unchanged poison messages automatically. A replay tool could be added later with authorization, audit and rate limits.

### 11. Could the DLQ itself contain duplicates?

Yes. A crash after DLQ publication but before committing the recovered source offset can cause the same source record to be recovered again. DLQ publication and source-offset commit are not configured as one Kafka transaction here. Operators should identify a failed delivery by original topic, partition and offset, rather than assume one DLQ entry per source record.

### 12. Is delivery exactly once?

No: Kafka-to-PostgreSQL delivery is at least once. If the process dies after a database commit but before the Kafka offset commit, the event is delivered again. The primary key and atomic insert-on-conflict prevent another stored row. This makes that database effect idempotent; it would not automatically protect a newly added payment call, notification or other external side effect.

### 13. What if the same event ID is sent with different content?

The first stored event wins. [The native insert](../src/main/java/com/example/fundorders/repository/ProcessedEventRepository.java) uses `ON CONFLICT (event_id) DO NOTHING`, and there is no payload comparison. That is deliberate simplification, not proof that the contents match. Producers must preserve event identity across retries and assign a new event ID to a distinct event. Conflict detection would be a separate contract change; it is not implemented.

### 14. How does producer idempotence differ from repository idempotency?

Producer idempotence handles eligible Kafka producer retries, while the repository handles repeated event deliveries by business event ID. Two HTTP calls that create separate sends can still publish duplicate events. The application does not explicitly set `acks` or `enable.idempotence`; current Kafka defaults support idempotent production when compatible settings apply, but I would make the production intent explicit. Neither mechanism makes Kafka and PostgreSQL atomic together. [Kafka producer configuration documents these defaults and constraints](https://kafka.apache.org/42/configuration/producer-configs/).

### 15. What does 202 mean, and can a 503 still result in a stored event?

202 means the producer received Kafka's acknowledgment under the effective producer configuration. It does not mean the consumer stored the event. A timeout can return 503 even though the outstanding send later succeeds: failure to observe acknowledgment is not proof of non-delivery. Clients should retry the same logical typed event with the same event ID, rather than generate a fresh ID. The read API provides eventual visibility, not a synchronous completion guarantee.

### 16. Why wait synchronously for Kafka in a REST request?

It gives the caller an acknowledgment-backed receipt and a simple error contract. The cost is a blocked request thread and limited throughput under slow Kafka. `EventPublisher` accounts for time spent in synchronous send work before waiting on the future, but its application deadline cannot preempt a send call already blocking; Kafka's own blocking settings also matter. Under load I would measure and consider asynchronous HTTP handling or an outbox if durable request acceptance became a requirement.

### 17. Does orderId guarantee business ordering?

Typed events use `orderId` as the Kafka key, grouping an order's records into a partition while the partitioning scheme remains stable. This preserves partition log order, not chronological `occurredAt` order or valid business transitions across independent publishers. Raw events use random keys and do not offer the same per-order grouping. A DLQ replay can arrive after later events, so an actual order-state projection would need a version/sequence or a reconciliation policy.

## 5. Persistence and modelling

### 18. Why is @Transactional on the service?

The Spring-managed listener calls a different Spring bean, so the service's transactional proxy wraps each `process` call. A repository failure rolls back that event's transaction, while earlier successful event transactions remain committed. Moving the listener loop into the same service and calling its own transactional method would require care: ordinary self-invocation does not cross the proxy. Repository tests using `@DataJpaTest` also have a surrounding test transaction; those alone do not prove service proxy behavior.

### 19. Why combine JpaRepository with native SQL?

JPA supports the entity/read model and paging. The PostgreSQL native statement gives an atomic insert-if-absent without a race between checking existence and inserting. The primary key is essential; an application-level check alone is insufficient under concurrent consumers. The tradeoff is database-specific SQL. The tests use real PostgreSQL rather than pretending an in-memory database has identical semantics.

### 20. Is pagination stable and efficient?

The query sorts by `occurred_at DESC, event_id DESC`, backed by a matching [index](../src/main/resources/db/migration/V1__create_fund_order_events.sql). Event ID breaks timestamp ties. However, offset pagination can shift between requests as new rows arrive, and large offsets plus total-count queries become expensive. For a large history API I would evaluate keyset pagination on those two fields, with an explicit snapshot/consistency contract.

### 21. Why two UUIDs and a schema version?

`eventId` identifies a particular event and drives deduplication. `orderId` groups several status updates for one order and supplies the typed Kafka key. `schemaVersion` identifies the event contract; the codec currently accepts only version 1. The event is a status notification, not an investment instruction containing an instrument, amount or settlement calculation.

### 22. How would you evolve the schema and database?

I would distinguish compatible additive fields from breaking event changes, define unknown-field handling, and add explicit version conversion where needed. For a deployed database, existing Flyway migrations must remain immutable and changes need new migrations. The current V1 was simplified during this unreleased exercise; that is not a production upgrade strategy. Schema Registry or Avro could be justified by multiple independently deployed producers, but the task does not require them.

## 6. Security and deployment

### 23. How do Basic and JWT differ here?

Basic is the default local mode, with one configured in-memory user and BCrypt password matching. Its role is named `READER`, but the rules only require authentication, so that user can publish too. The `jwt` profile excludes the Basic beans and requires bearer authentication with read/write scopes. The shipped Kubernetes manifest selects `jwt` explicitly; there is no automatic inference that an environment is production.

### 24. Why validate issuer, audience and scope separately?

Issuer identifies the trusted token issuer; the signature proves authenticity using its trusted keys. Audience checks that the token was issued for this API, rather than another API using the same issuer. Scope decides whether the caller can read or publish. This is still coarse authorization: no rule limits a caller to one customer's or partner's rows, because this demo models an internal event API.

### 25. What happens when signing keys rotate or the provider is unavailable?

The decoder trusts the configured public JWK endpoint, RS256, issuer and audience, and requires an expiration claim with timestamp validation. New key IDs can trigger a key refresh; cached usable keys can validate tokens during an outage until cache/key conditions require refresh. Unavailable required keys fail authentication, with explicit 401 handling. This is local JWT validation, not immediate token revocation or introspection. Never promise that an identity-provider outage is invisible indefinitely.

### 26. Is disabling CSRF justified because the app is stateless?

Statelessness alone is not enough. A bearer token explicitly supplied in an Authorization header differs from credentials automatically attached by a browser. Browser-managed Basic credentials can still create CSRF exposure, so the Basic mode should remain a local demonstration unless its browser threat model and protections are addressed. Public Swagger documentation does not grant public API access; Try it out still requires credentials. [Spring Security explicitly discusses Basic authentication in stateless CSRF scenarios](https://docs.spring.io/spring-security/reference/features/exploits/csrf.html).

### 27. What do readiness and liveness actually prove?

Liveness is process/application availability and does not depend on PostgreSQL. Readiness includes the database, but currently does not verify Kafka connectivity, listener assignment or consumer progress. Kubernetes readiness affects Service traffic; it does not automatically stop the Kafka listener. Whether Kafka should affect HTTP readiness is a product decision because stored-event reads can remain useful during a Kafka outage. External outages should not trigger mass liveness restarts. [Spring Boot documents these probe distinctions](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html).

### 28. How would you scale the consumer safely?

With three source partitions, at most three consumers in the same group can actively consume those partitions. Two replicas with concurrency one provide two consumers; raising concurrency can help only within the partition limit and database capacity. Bound processing time, database waits, retries and recovery work so polling remains healthy; `max.poll.interval.ms` is distinct from heartbeats. Start with a budget such as batch size × slow-record processing time, then measure the real tail latency and rebalance behavior. [Kafka documents the poll interval and record limits](https://kafka.apache.org/42/configuration/consumer-configs/).

### 29. Who creates the topics, and what should configuration validate?

Spring Boot supplies KafkaAdmin, and [KafkaTopicConfiguration](../src/main/java/com/example/fundorders/config/KafkaTopicConfiguration.java) supplies source/DLQ declarations. Local Compose enables creation; Kubernetes disables application administration and expects pre-provisioned topics. Disabling app topic creation does not disable broker-side automatic creation. DLQ publication preserves the source partition, so the destination needs at least as many partitions. Equal source/DLQ names are currently allowed and should be rejected; externally managed topic layouts are not validated at startup.

## 7. Tests, evidence and project scope

### 30. What do your tests prove, and what do they not prove?

Unit/MVC tests cover validation, producer behavior, failure classification, API behavior and authorization. Integration tests cover real HTTP, Kafka and PostgreSQL, controlled batches, DLQ recovery and real signed JWTs. Mocked JWT request helpers test authorization rules, while the separate cryptographic and HTTP tests exercise real token validation. The suite does not establish performance capacity, production broker failover, a real database outage, crash recovery or concurrent duplicate insertion by itself.

### 31. What additional test would you choose first?

I would choose based on a concrete risk: for example, concurrent inserts of the same event ID to reinforce the deduplication contract, or a controlled database outage showing offsets remain pending and recovery resumes. A multiple-partition failure test would strengthen confidence beyond the current single-partition batch scenario. I would avoid copying the implementation into assertions or adding permutations just to raise test counts.

### 32. Is 96% line coverage enough, and where is the published report?

Coverage tells me which code executed; it does not prove correct outcomes or concurrency behavior. The baseline run has lower branch coverage than line coverage, so I would inspect uncovered decisions and risks instead of targeting a percentage alone. `verify` generates current HTML/XML under `target/site/jacoco/`; the committed report is a historical snapshot. Refreshing that delivered artifact is a concrete improvement because the task asks for a generated coverage report, not just a reporting plugin. No JaCoCo minimum threshold is currently enforced.

### 33. Why repeat container declarations in integration classes?

The tests favor clear class-level Kafka/PostgreSQL lifetimes and isolation. Shared fixtures can reduce duplication and startup time, but they must align container lifetime with Spring context caching and avoid shared topic/database state leaking between tests. I would only centralize this when the lifetime contract remains obvious and the gain is measured. A generic base class is not automatically a correctness improvement.

### 34. What makes this relevant to investment-fund trading?

It models receiving order-status updates from a trading partner, preserving event history and handling unreliable delivery. `REJECTED` is an expected business result, not a technical processing failure. It does not execute trades, calculate tax, validate settlement or enforce an order-state machine. I would ask domain experts about transition rules, partner sequencing, event identity, reconciliation and audit requirements before adding those capabilities.

### 35. What would you improve with another day, and what would you leave out?

First I would refresh the submitted coverage artifact, close the configuration gaps, and collect a small benchmark plus a slow-consumer result. For a real deployment I would agree on monitoring, retry escalation, request limits, Kafka security and replay ownership. I would not add a tax engine, microservices, an automated DLQ replay platform, event sourcing or distributed transactions merely to make the exercise look larger. An outbox becomes relevant if request acceptance also needs a durable database write, not simply because Kafka is present.

## 8. Five scenarios to rehearse aloud

1. Draw A/B/C at offsets 10/11/12 and explain both successful and failed DLQ publication. Include the next-offset convention.
2. Describe a crash after PostgreSQL commit but before Kafka offset commit. Identify the deduplication boundary.
3. Explain a producer timeout where a later send succeeds. Show why a client must preserve event identity.
4. Compare a slow database with malformed content. Explain which condition retries and which reaches the DLQ.
5. Explain what public Swagger, successful readiness and a valid JWT each do and do not prove.

For each answer, be ready to open the relevant class or test rather than rely only on terminology.

## 9. Claims to avoid

- “A batch means one database transaction.”
- “HTTP 202 means processing completed.”
- “HTTP 503 proves the message was never published.”
- “Kafka producer idempotence means exactly-once database processing.”
- “A duplicate event ID proves identical payloads.”
- “DLQ recovery is automatic and preserves business order.”
- “Readiness proves Kafka consumption is healthy.”
- “Stateless Basic authentication cannot have CSRF risk.”
- “High coverage proves the application is production-ready.”
- “The checked-in coverage report is from the latest code.”
