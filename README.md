# Fund order event processing

Java 21 / Spring Boot application for a fictional investment-fund trading partner. It accepts order status events over HTTP, publishes their JSON text to Kafka, consumes Kafka records in batches, and exposes successfully processed events from PostgreSQL. Invalid Kafka content goes to a dead-letter topic (DLQ).

For a requirement-by-requirement implementation checklist, see [the reviewer guide](readme-reviewer.md).

## Run locally

Prerequisites: Docker with Compose. The first build downloads Java images and Maven dependencies.

```bash
cp .env.example .env
docker compose up --build -d
docker compose ps
curl -fsS http://localhost:8080/actuator/health/readiness
```

The sample credentials in `.env.example` are for local use only. Change both passwords before sharing the stack. Compose exposes the app on `localhost:8080`, Kafka on `localhost:9092`, and Kafka UI on `localhost:8081`; PostgreSQL stays on the Compose network. The app creates the source and DLQ topics with three partitions each and Flyway creates the database table. To stop the stack, run `docker compose down`. `docker compose down -v` also removes PostgreSQL data.

For a guided interview presentation and runnable curl scenarios, see [the demo guide](docs/demo.md) and run `./scripts/demo.sh --help`.

## Swagger UI

Available at [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html).

## Kafka browser UI

Compose deploys Kafka UI at [http://localhost:8081](http://localhost:8081).

## Try the two publication paths

The typed endpoint validates JSON before publication:

```bash
curl -i -u analyst:change-this-local-password \
  -H 'Content-Type: application/json' \
  -d '{"schemaVersion":1,"eventId":"f46d8666-dd70-439d-a8f8-9c797ed3cc4d","orderId":"7d9b4d48-8967-4b6b-ad60-a15104115757","partnerCode":"AGENCY_A","status":"REJECTED","occurredAt":"2026-10-21T12:00:00Z"}' \
  http://localhost:8080/api/v1/fund-order-events
```

The raw endpoint forwards text unchanged; malformed JSON is sent to the DLQ by the consumer:

```bash
curl -i -u analyst:change-this-local-password \
  -H 'Content-Type: text/plain' --data-binary 'not-json' \
  http://localhost:8080/api/v1/fund-order-events/raw
```

Both return `202 Accepted` after Kafka acknowledges publication. Valid events become available asynchronously:

```bash
curl -u analyst:change-this-local-password \
  http://localhost:8080/api/v1/fund-order-events/f46d8666-dd70-439d-a8f8-9c797ed3cc4d
```

## Test and coverage

With Java 21 and Docker running:

```bash
./mvnw verify
```

JWT tests use real RSA-signed tokens and a local JWK server to check signatures, issuer, audience, expiry, algorithm restrictions, key rotation, outages, scope enforcement and absence of a Basic fallback. Both authentication profiles have HTTP integration coverage. The test suite also includes unit tests for conversion, producer behavior, batch failure identification, security, pagination, and database idempotency. Testcontainers integration tests use real Kafka and PostgreSQL to exercise HTTP publish → Kafka batch consume → authenticated read or DLQ. A controlled batch integration test preloads three records before listener startup and asserts the actual multi-record delivery, database results, DLQ metadata, and broker-committed offsets. A second scenario injects a failed DLQ send, verifies that the invalid record and following record remain pending, then allows a real DLQ publication and verifies recovery. This fault injection tests the publication boundary; it does not simulate a broker outage. Maven Failsafe runs `*IT` classes during `verify`. JaCoCo produces `target/site/jacoco/index.html` and `target/site/jacoco/jacoco.xml`; a generated snapshot is included at [codex/reports/coverage/index.html](codex/reports/coverage/index.html). No hosted CI is required for the local verification gate.
