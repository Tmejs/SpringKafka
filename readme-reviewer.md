# Reviewer guide

Java 21 and Spring Boot are configured in [pom.xml](pom.xml).

## Requirements

| Requirement | Implementation |
| --- | --- |
| Kafka consumer with configurable topic and batch processing | [FundOrderBatchListener.onMessages](src/main/java/com/example/fundorders/kafka/FundOrderBatchListener.java) receives a list of Kafka records. [KafkaBatchConfiguration](src/main/java/com/example/fundorders/config/KafkaBatchConfiguration.java) enables batch delivery and batch offset handling. Topic: `app.kafka.topic`. Each record is processed in a separate database transaction. |
| REST API publishing to the same Kafka topic | [EventPublishController](src/main/java/com/example/fundorders/controller/EventPublishController.java) exposes typed JSON and raw-text POST endpoints. [EventPublisher](src/main/java/com/example/fundorders/service/EventPublisher.java) publishes to `app.kafka.topic`. |
| Appropriate message models | [FundOrderStatusEvent](src/main/java/com/example/fundorders/model/FundOrderStatusEvent.java) defines the event and validation constraints; [OrderStatus](src/main/java/com/example/fundorders/model/OrderStatus.java) defines allowed statuses. |
| Error handling and DLQ | [FundOrderEventCodec](src/main/java/com/example/fundorders/service/FundOrderEventCodec.java) validates consumed content. [KafkaBatchConfiguration](src/main/java/com/example/fundorders/config/KafkaBatchConfiguration.java) configures retries and DLQ recovery. [ApiExceptionHandler](src/main/java/com/example/fundorders/exception/ApiExceptionHandler.java) handles HTTP errors. |
| Authenticated API exposing consumed data | [FundOrderQueryController](src/main/java/com/example/fundorders/controller/FundOrderQueryController.java) provides paginated and individual-event GET endpoints backed by [ProcessedEventRepository](src/main/java/com/example/fundorders/repository/ProcessedEventRepository.java). [SecurityConfiguration](src/main/java/com/example/fundorders/config/SecurityConfiguration.java) enables Basic authentication; [JwtSecurityConfiguration](src/main/java/com/example/fundorders/config/JwtSecurityConfiguration.java) enables JWT with read/write scopes in the `jwt` profile. |
| External Kafka configuration | [application.yml](src/main/resources/application.yml) contains Kafka settings and environment overrides; [.env.example](.env.example) provides local values. [KafkaTopicConfiguration](src/main/java/com/example/fundorders/config/KafkaTopicConfiguration.java) declares configurable source and DLQ topics. |
| Health endpoint and Kubernetes probes | Actuator is included in [pom.xml](pom.xml). [application.yml](src/main/resources/application.yml) enables `/actuator/health`, `/actuator/health/readiness` and `/actuator/health/liveness`. [Kubernetes deployment](deploy/kubernetes/deployment.yaml) configures startup, readiness and liveness probes. |
| Unit tests for consumer, producer, service and API | [Producer test](src/test/java/com/example/fundorders/service/EventPublisherTest.java), [service tests](src/test/java/com/example/fundorders/service/) and [API tests](src/test/java/com/example/fundorders/controller/). Consumer coverage is provided by the integration tests below. |
| End-to-end integration tests | [FundOrderFlowIT](src/test/java/com/example/fundorders/FundOrderFlowIT.java) verifies authenticated typed and raw MockMvc requests → Kafka publication → consumption → PostgreSQL, checking stored fields through the repository. [EventPublishControllerIT](src/test/java/com/example/fundorders/controller/EventPublishControllerIT.java) verifies requests against actual Kafka records. [FundOrderConsumerIT](src/test/java/com/example/fundorders/kafka/FundOrderConsumerIT.java) verifies Kafka → PostgreSQL and Kafka → DLQ, including continued processing after an invalid message. |
| Test coverage reports | JaCoCo is configured in [pom.xml](pom.xml). The verification command below generates HTML and XML in `target/site/jacoco/`. An [included report snapshot](codex/reports/coverage/index.html) is available; its [generation record](codex/reports/coverage/README.md) identifies the tested source commit and test results. |

## Deliverables

1. Application source: [src/main/java](src/main/java/).
2. Unit and integration tests: [src/test/java](src/test/java/).
3. Build and reporting plugins: [pom.xml](pom.xml) — Surefire, Failsafe and JaCoCo.
4. Application and Kafka configuration: [application.yml](src/main/resources/application.yml), [application-jwt.yml](src/main/resources/application-jwt.yml), [.env.example](.env.example) and [compose.yaml](compose.yaml).
5. Current generated coverage report after verification: `target/site/jacoco/index.html` and `target/site/jacoco/jacoco.xml`.

With Java 21 and Docker running, execute:

```bash
./mvnw clean verify
```
