# Kafka batch integration tests implementation plan

> **For agentic workers:** Execute this focused checkpoint inline using the executing-plans workflow. Steps use checkbox syntax.

**Goal:** Prove real multi-record Kafka delivery, partial failure recovery, and preservation of pending offsets during DLQ publication failure.

**Architecture:** Add a separate Testcontainers integration class using the unchanged application listener, error handler, JPA service, Kafka broker, and PostgreSQL. Test-only startup control and batch observation capture real deliveries. A KafkaTemplate spy injects one failed DLQ send and gates the next attempt; successful publication uses the real broker.

**Tech Stack:** Existing Java 21, Spring Boot, Spring Kafka, JUnit, Mockito, Awaitility, Testcontainers and JaCoCo.

**Spec:** The preceding approved chat design: preload valid A / malformed B / valid C in one partition, start the listener, inspect actual batches, database rows, DLQ metadata, and broker-committed offsets.

## Global constraints

- Preserve per-record transactions and production behavior; do not implement bulk persistence or unrelated review suggestions.
- Follow the user's checkpoint branch, independent review, full local gate, push, and no-fast-forward merge workflow.
- Put planning and verification records under codex; do not add hosted CI.

## Review focus

- Assert an observed delivery contains all three source offsets, rather than inferring batching from max.poll.records.
- During failed recovery only A is stored, the committed source position is B, C is absent, and the DLQ has not advanced.
- After release B reaches the real DLQ, C is stored, and the source offset is C + 1.
- Use unique event IDs, observed producer offsets, bounded waits, and unconditional gate/container cleanup.
- Prove the tests detect disabled batch delivery and unsafe recovery acknowledgement with temporary negative controls; restore production files afterward.

### Task 1: Controlled multi-record and recovery integration tests

**Files:** Create src/test/java/com/example/fundorders/consume/KafkaBatchProcessingIT.java; update README.md only to describe verified test coverage.

**Interfaces:** Existing listener factory, KafkaTemplate, KafkaListenerEndpointRegistry, ProcessedEventRepository, and real Kafka Admin offset queries.

- [ ] Add the two integration tests and test-only observation/failure controls.
- [ ] Run focused integration tests and resolve fixture or assertion failures.
- [ ] Run negative controls to establish that the assertions reject the intended regressions.
- [ ] Restore production source; run the complete local verification gate and git diff --check.
- [ ] Request independent read-only review and resolve Important/Critical findings.
- [ ] Record evidence, refresh the generated coverage snapshot, commit, push, merge --no-ff, verify merged main, and confirm remote SHAs.
