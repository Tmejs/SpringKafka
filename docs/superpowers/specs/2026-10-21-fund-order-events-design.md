# Fund order event processing — design

Date: 2026-10-21

## Intent

Build a polished technical assignment in Java 21 and Spring Boot. The application demonstrates batch Kafka consumption, an authenticated read API, two ways to publish to Kafka, deterministic invalid-message recovery, and reproducible tests and coverage. Its fictional domain is order updates sent by an external investment-fund trading partner. It does not execute trades or calculate financial outcomes.

## Success criteria

- A reviewer can start Kafka, PostgreSQL, and the application with Docker Compose and follow a short README demo.
- A typed REST request is validated before publication; a raw request can place malformed or semantically invalid text on the same Kafka topic.
- A batch containing valid, invalid, valid records yields two persisted events and exactly one dead-letter record without blocking later records.
- An authenticated, paginated API returns successfully consumed events from PostgreSQL.
- Unit and container-backed integration tests cover the normal path, validation boundaries, duplicate delivery, authentication, and dead-letter behavior.
- Maven verification generates a coverage report; the final submission includes the generated report and how to regenerate it.
- Health endpoints and Kubernetes probe configuration are supplied.

## Boundaries and assumptions

The events and partner are fictional. `REJECTED` is a legitimate business status, not a technical failure. Validation checks message shape and local invariants only; it does not claim to implement trading, suitability, tax, or regulatory rules. The producer REST API simulates an upstream publisher for the assignment. PostgreSQL is the queryable view of processed events. Kafka remains the transport and source of replay, and delivery across Kafka and PostgreSQL is at least once.

The repository follows the user's bootstrap, checkpoint branch, independent review, and merge workflow. The design documents establish the initial `main` for this newly created remote; application work belongs on a fresh checkpoint branch.

## Event contract

The Kafka value is a UTF-8 JSON string and the typed Java model is `FundOrderStatusEvent`:

| Field | Type | Rule |
| --- | --- | --- |
| `schemaVersion` | integer | `1` for this submission |
| `eventId` | UUID | required; unique idempotency key |
| `orderId` | UUID | required; Kafka key for typed publication |
| `partnerCode` | string | required, bounded length |
| `fundIsin` | string | required, basic 12-character format check |
| `status` | enum | `ACCEPTED`, `REJECTED`, or `EXECUTED` |
| `occurredAt` | ISO-8601 instant | required |

The ISIN check validates format only; it makes no claim that the instrument exists. No customer identifiers, account numbers, or personal data are needed. A version field makes future schema evolution an explicit interview topic.

## HTTP contract

- `POST /api/v1/fund-order-events` consumes `application/json`, maps to the typed model, validates it, and publishes a JSON string keyed by `orderId`. Invalid requests return `400` and do not enter Kafka.
- `POST /api/v1/fund-order-events/raw` consumes `text/plain`; its body is sent unchanged as a Kafka string. Authentication and a request-size limit apply. It is a diagnostic/demo publisher for testing malformed upstream messages. It uses a generated key because the body may not contain a parseable `orderId`.
- Both POST endpoints wait for broker acknowledgment with a bounded timeout, then return `202 Accepted` with publication correlation information. Broker or timeout failures return an appropriate service error. `202` means Kafka accepted the record, not that the consumer processed it.
- `GET /api/v1/fund-order-events` returns a paginated view of persisted events. `GET /api/v1/fund-order-events/{eventId}` returns one event or `404`.
- All message endpoints require Basic authentication. Actuator health probes are public. Error responses have a consistent shape and do not expose secrets or raw stack traces.

The raw endpoint intentionally bypasses HTTP JSON parsing. The consumer applies the same validation regardless of publisher, so a raw but valid JSON body is processed normally and malformed or invalid content reaches the dead-letter flow.

## Processing and recovery

Use a Spring Kafka batch listener receiving `List<ConsumerRecord<String, String>>`, with configurable topic, group, batch size, concurrency, backoff, and broker settings. Each record is parsed from its string value and validated. Valid events are saved in a transaction. A unique constraint on `eventId` makes replay of an identical event a no-op; a reused ID with conflicting content is treated as invalid data rather than silently replacing the stored event.

Failure classification is explicit:

| Outcome | Example | Action |
| --- | --- | --- |
| Valid business event | `status: REJECTED` | Persist and expose it; rejection is a business outcome. |
| Permanent message defect | Malformed JSON; missing `orderId`; unknown status; unsupported `schemaVersion`; invalid ISIN format; conflicting reuse of `eventId` | Publish the original Kafka record to the DLQ without retrying unchanged content. |
| Temporary processing failure | PostgreSQL unavailable or timed out | Retry with a delay, keep the offset pending, and alert after repeated attempts. |
| Recovery-path failure | DLQ broker/send failure | Keep the source offset pending and retry recovery; never claim the record was handled. |
| Unexpected application failure | Unclassified runtime exception | Retry and alert for investigation; do not silently classify it as bad partner data. |

An invalid typed HTTP request receives `400` and never reaches Kafka, so it does not appear in the DLQ. The raw endpoint allows the consumer-side malformed/invalid scenarios to be demonstrated.

On JSON parsing or event validation failure, the listener throws `BatchListenerFailedException` identifying the record. Configure `DefaultErrorHandler` and `DeadLetterPublishingRecoverer` so permanent input errors skip futile retries and publish only the failed record to `<topic>.DLQ` (name configurable). Preserve the original value and Kafka metadata/error headers. Confirm dead-letter publication before advancing the failed record's offset. Records after the failed record are redelivered and processed. The DLQ is provisioned with at least as many partitions as the source topic if the recoverer preserves the source partition.

Treat PostgreSQL or other infrastructure failures as transient. Retry with a configured delay until recovery; do not classify the record as poison or commit past it. Emit an operational alert after repeated attempts so a prolonged outage is visible. This intentionally blocks progress on affected partitions rather than discarding good records. Record idempotency protects against records replayed after a crash between database commit and Kafka offset commit.

Do not use `@RetryableTopic` for the batch listener; Spring Kafka does not support nonblocking retry topics for batch listeners. Do not promise exactly-once processing across Kafka and PostgreSQL.

## Persistence, security, and operations

Use PostgreSQL with an explicit schema migration and a unique `eventId` constraint. A repository query supports stable pagination. Store the event's business fields and processing timestamp; retain raw payload only if needed for audit/demo and avoid logging payloads indiscriminately.

Basic credentials come from external configuration, are never hard-coded in production settings, and require HTTPS at the ingress outside local development. The security boundary is isolated in configuration so a future JWT resource-server implementation can replace Basic without changing controllers or processing code. JWT is documented as an extension, not implemented or advertised as supported.

Compose starts a single-node development Kafka broker, PostgreSQL, and the application. Supply `.env.example`, a Dockerfile, a Kubernetes Deployment example with readiness/liveness probes, and externalized environment mappings. Use Spring Boot Actuator readiness/liveness endpoints. Liveness must not depend on Kafka or PostgreSQL availability. Topic creation in local Compose is explicit; production provisioning and credentials remain deployment concerns. Use structured logs with event IDs and metrics for processed, rejected, retried, and dead-lettered records where feasible.

## Verification and deliverables

Maven provides Java 21 compilation, unit tests, integration tests, and JaCoCo HTML/XML reporting. Unit tests cover publisher responses and failures, parse/validation/storage logic, batch failure identification, duplicate behavior, API pagination, authentication, and HTTP validation. Container-backed integration tests use isolated Kafka and PostgreSQL to prove typed publish-to-read flow; raw valid JSON flow; malformed JSON and invalid field-to-DLQ flows; valid-invalid-valid continuation; and idempotent replay. Tests must assert DLQ content/metadata and absence of invalid records from the read API, not merely observe logs.

The submission includes application source, tests, build configuration, application/Kafka settings, Compose and Kubernetes files, OpenAPI and AsyncAPI contracts, a README with runnable demo commands and tradeoffs, and the generated coverage report. The final report is copied into a documented repository location after the verification run so it can be inspected without running the build. No GitHub Actions are required.

## Implementation stages

1. Establish the repository and build, event/HTTP contracts, Compose stack, and typed publication.
2. Implement the batch consumer, PostgreSQL view, idempotency, invalid-message classification, and DLQ; write focused tests alongside each behavior.
3. Add authentication, read API, health/Kubernetes configuration, integration tests, and coverage generation.
4. Run full local verification, inspect coverage for missing behavioral cases, rehearse the demo, document tradeoffs, and complete the user's branch/checkpoint review and merge workflow when a remote is available.

## Interview explanation

Be ready to trace offset handling for a valid-invalid-valid batch; distinguish HTTP validation from asynchronous consumer validation; explain why business rejection is not a DLQ condition; describe at-least-once delivery and idempotency; justify Kafka key/partition ordering; describe what happens when PostgreSQL or DLQ publication is unavailable; and show OpenAPI versus AsyncAPI as separate contracts.
