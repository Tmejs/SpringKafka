# Interview demonstration

## Suggested presentation: 10–12 minutes

| Time | Show | Explain |
| --- | --- | --- |
| 1 minute | Problem and architecture in README | Investment-fund order status events arrive through HTTP, travel through Kafka, and become a PostgreSQL read model. |
| 2 minutes | Compose, readiness and authenticated GET | Kafka and PostgreSQL are real dependencies. Health is public; business APIs require Basic authentication. |
| 2 minutes | Typed publication, receipt, then GET | HTTP 202 means Kafka acknowledged publication. The read model is updated asynchronously. `eventId` identifies a delivery; `orderId` groups order updates under the same Kafka key. `REJECTED` is valid business data. |
| 2 minutes | Typed invalid status, then malformed raw text and DLQ | Typed validation rejects with 400 before publication. Raw text gets 202; the consumer identifies the permanent defect and publishes the original message to the DLQ. |
| 2 minutes | Controlled batch integration tests and coverage | Show actual multi-record delivery, valid–invalid–valid recovery, offset progression, failed DLQ publication and extreme timestamp protection. |
| 1–3 minutes | Decisions and production considerations | At-least-once delivery, first-write-wins deduplication, per-record database transactions, infrastructure retry behavior, Basic-to-JWT extension point, and topic ownership. |

Open the README architecture diagram, this guide, and the generated coverage report before the call. Pre-build images and run the tests beforehand. Use individual script commands during the presentation; `all` is useful for rehearsal.

## Start locally

Requirements: Docker with Compose. The scripts additionally need Bash, curl, and Python 3. Host Java is only needed to run Maven locally; the Docker build supplies Java 21 and Maven.

From the repository root:

```bash
# Keep an existing .env; create it only if missing.
[ -f .env ] || cp .env.example .env
# Review the local credentials in .env before starting.
docker compose up --build -d --wait --wait-timeout 180
docker compose ps
curl -fsS http://localhost:8080/actuator/health/readiness
```

The first build may take longer because it downloads images and dependencies. If startup fails, inspect `docker compose logs --tail=100 app kafka postgres kafka-ui`. Ports 8080 (application), 8081 (Kafka UI), and 9092 (Kafka) must be available; `docker ps` shows existing containers using them. Stop an older stack deliberately before starting this one. Do not delete an existing database volume as a routine startup step.

Flyway initializes the database, and the application creates the configured source/DLQ topics by default. An older database created before the V1 schema simplifications requires a deliberate fresh development database; the application does not include an upgrade migration for that old schema.

Load the supplied, shell-compatible environment file into your demo terminal:

```bash
set -a
. ./.env
set +a
./scripts/demo.sh health
./scripts/demo.sh auth
```

`health` expects public readiness HTTP 200. `auth` expects HTTP 401 without credentials. Authenticated commands read `APP_BASIC_AUTH_USERNAME` and `APP_BASIC_AUTH_PASSWORD`; the script does not print their values. `BASE_URL` optionally changes the target URL.

## Demonstrate successful processing

```bash
./scripts/demo.sh publish
# Copy the EVENT_ID printed by publish:
./scripts/demo.sh read <EVENT_ID>
./scripts/demo.sh list
./scripts/demo.sh raw-valid
```

`publish` sends a typed JSON event with status `REJECTED` and prints the Kafka topic, partition, offset and key from its receipt. `read` polls for up to 30 seconds because consumption is asynchronous. `raw-valid` publishes proper JSON as text and waits until it is readable. IDs are generated afresh for each command by default.

To choose IDs yourself, export `EVENT_ID` and `ORDER_ID` as valid UUIDs. The invalid typed scenario always generates a fresh event ID so its 404 check remains meaningful. Unset them afterwards to return to fresh IDs. Records from previous runs remain in the list.

## Call the APIs from Swagger UI

Open [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html). Log in with the Basic credentials from `.env` when the browser asks. Use **Authorize** in Swagger UI to set the same username/password for **Try it out**.

The UI shows the current controller routes and models, including publication HTTP 202, validation/error responses, pagination and Basic authentication. Send a typed event with a fresh UUID, then retrieve that event. The raw endpoint accepts text; malformed input is rejected later by the consumer. The curl script remains useful for repeatable rehearsals.

`./mvnw clean verify` exports the checked runtime document to `target/openapi/openapi.json`. For a running application, download it with credentials:

```bash
curl -fsS -u "$APP_BASIC_AUTH_USERNAME:$APP_BASIC_AUTH_PASSWORD" \
  http://localhost:8080/v3/api-docs -o /tmp/fund-orders-openapi.json
```

The document, UI and UI assets require authentication. There is no checked-in static OpenAPI file. A package-only build or a build with integration tests skipped does not export the artifact; its running app still exposes the document.

## Inspect Kafka in the browser

Open [http://localhost:8081](http://localhost:8081). If the stack was started before Kafka UI was added, run `docker compose up -d kafka-ui` first.

- **Topics → fund-order-events → Messages:** browse source events.
- **Topics → fund-order-events.DLQ → Messages:** find the malformed message marker and inspect the original-source/error headers.
- **Consumer Groups → fund-order-events-processor:** inspect committed offsets and lag.

Use your configured topic/group names if changed from the defaults. Select messages from the beginning or an earlier offset when inspecting previously consumed records. Consumption does not remove records from Kafka. The UI is exposed only on localhost and has no application Basic-auth login.

## Demonstrate validation and the DLQ

In a second terminal, watch the DLQ. Load `.env` there too when using a custom DLQ topic:

```bash
docker compose exec -T kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server kafka:19092 \
  --topic "${APP_KAFKA_DLQ_TOPIC:-fund-order-events.DLQ}" \
  --from-beginning \
  --property print.key=true \
  --property print.partition=true \
  --property print.offset=true
```

This prints historical messages and keeps watching until Ctrl+C. The displayed partition/offset are the DLQ record's coordinates. The integration tests separately inspect the original source metadata carried in Kafka headers. There is no REST endpoint for reading or replaying the DLQ.

In the demo terminal:

```bash
./scripts/demo.sh invalid-typed
./scripts/demo.sh invalid-raw
```

Expected results:

- `invalid-typed`: unknown status gets HTTP 400, and the generated event ID is not readable (404). No Kafka publication occurs; the controller test verifies this boundary.
- `invalid-raw`: malformed text gets HTTP 202. Find the script's unique marker in the DLQ terminal to demonstrate asynchronous rejection and preservation of the original value. The HTTP script alone does not assert DLQ delivery.

For a short replay illustration:

```bash
./scripts/demo.sh duplicate
```

It publishes exactly the same event twice and shows the stored event. The second 202 acknowledges publication. Use the idempotency integration test to prove both deliveries were consumed without creating another row; an immediate GET alone cannot prove consumption of the second record. The current policy ignores any valid replay of an existing `eventId`, including changed content: first stored version wins.

For a complete rehearsal:

```bash
./scripts/demo.sh all
```

## Show real batch processing and tests

With Java 21 and Docker running:

```bash
./mvnw clean verify
# macOS; otherwise open this HTML file in your browser:
open target/site/jacoco/index.html
```

Show these tests in the editor:

- `KafkaBatchProcessingIT`: listener is stopped, several records are preloaded into one partition, then consumption starts. The test asserts an actual multi-record batch, successful rows, the exact DLQ record and committed offsets.
- Its failed-DLQ-send case: the source failure and batch tail remain pending until DLQ publication succeeds.
- Its timestamp cases: values PostgreSQL cannot store reach the DLQ and later records continue.
- `FundOrderFlowIT`: real HTTP → Kafka → PostgreSQL → authenticated GET, raw failures and replay behavior.

A burst of REST requests does not guarantee a single Kafka poll batch. Actual batch size depends on timing and available records. Explain that this implementation batches Kafka delivery and offset handling while using one database transaction per event. Bulk inserts are a separate throughput optimization with different failure semantics.

The complete `verify` lifecycle runs unit tests, Failsafe integration tests and JaCoCo reporting. The fresh generated report is `target/site/jacoco/index.html`; the committed historical coverage snapshot is not the current run.

## Be ready to discuss

- A record may be stored before an offset commit fails; replay is therefore expected. The event ID primary key and `ON CONFLICT DO NOTHING` make storage idempotent.
- A permanent message defect goes to the DLQ. Database outages and unexpected failures retain delayed retries; a failed DLQ send leaves the source record pending. Infinite retries require operational alerting and investigation.
- DLQ replay is an operational process: inspect the error and source metadata, correct the cause, republish deliberately, and monitor recovery. No automatic DLQ replay is implemented.
- Basic authentication is suitable for this local demonstration. `SecurityConfiguration` is the documented JWT resource-server extension point; JWT is not implemented.
- In production, provision topics through infrastructure tooling, configure replication/retention and TLS, manage secrets, enforce ingress body limits, and monitor consumer lag. The Kubernetes readiness check includes PostgreSQL; it does not establish Kafka connectivity.

## Stop

```bash
docker compose down
```

This stops the stack and retains PostgreSQL data. Use `down -v` only when you intentionally want to remove its development data.

## API contract verification

The Maven integration tests request `/v3/api-docs` from the real application, verify Basic authentication and Swagger resources, assert the business routes, request/response schemas and important constraints, then export `target/openapi/openapi.json`. Generate it with `./mvnw clean verify`; OpenAPI is not maintained as a separate YAML file.

`docs/api/asyncapi.yaml` remains the Kafka contract. It is independent of HTTP controller generation. Optional Node.js/npm tooling can validate it with the official AsyncAPI CLI:

```bash
npx --yes @asyncapi/cli@5.0.7 validate docs/api/asyncapi.yaml
```

Optional metadata recommendations are distinct from document-validity errors. The old static-contract validator script has been removed.
