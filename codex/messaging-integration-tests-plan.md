# Messaging integration tests implementation plan

**Goal:** Prove the producer and consumer boundaries independently with real HTTP, Kafka and PostgreSQL.

**Architecture:** Add EventPublishControllerIT under controller and FundOrderConsumerIT under kafka. Both inherit a small support class with Spring-managed Testcontainers and service connections; one cached application context owns container lifetime. Existing user commits and removed files remain untouched. Work and commit directly on main as requested.

**Files:** pom.xml (test-scoped Spring Boot Testcontainers support); src/test/java/com/example/fundorders/support/MessagingIntegrationTestSupport.java; controller/EventPublishControllerIT.java; kafka/FundOrderConsumerIT.java; readme-reviewer.md (test locations).

1. Add the common container configuration, isolated source/DLQ/group settings, independent Kafka clients and wire-message fixture.
2. Add real authenticated HTTP tests: typed JSON produces a semantically matching Kafka value and order key; valid raw JSON and malformed Unicode text are preserved exactly. Check Kafka topic/partition/offset against the HTTP receipt.
3. Add direct Kafka tests: valid business events reach PostgreSQL with all fields; malformed JSON and invalid schema events reach the DLQ with original value/key/source metadata and are absent from the database. A valid following record in the same partition must persist.
4. Run focused integration tests, then the full applicable Maven gate. Validate test isolation and container lifecycle, inspect report counts and diff whitespace, and obtain independent review.
5. Update the requirements map, record verification, commit directly to main, fetch and preserve remote changes before a normal push, and compare local/tracking/remote commits. No changes to application behavior.
