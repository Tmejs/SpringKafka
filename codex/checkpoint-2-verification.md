# Checkpoint 2 verification

Source tested: `4a27f2a`, based on main `1003bb4`. This checkpoint adds integration tests and documentation; production application code is unchanged.

## Results

- Java 21 `./mvnw -q clean verify` passed on 2026-10-22: 33 unit tests and 10 integration tests, with no failures, errors, or skips.
- `KafkaBatchProcessingIT` proves an actual delivery contains all three producer-confirmed source offsets before asserting valid A / invalid B / valid C outcomes.
- During an injected failed DLQ send and gated redelivery, A is stored, C is absent, the source committed position is B, and the DLQ end offset is unchanged.
- After the gate is released, the configured recoverer publishes B to the real Kafka DLQ, C is stored, and the source committed position becomes C + 1. The DLQ value and original topic, partition, and offset headers are verified.
- The fault is injected at the KafkaTemplate DLQ publication boundary. Kafka and PostgreSQL remain real; this is not a broker-outage simulation.
- Temporary negative control: limiting max.poll.records to 1 made the batch test fail on its actual three-record delivery assertion.
- Temporary negative control: ignoring DLQ send errors made the recovery test fail because the failed record was not redelivered. Both changes were restored before full verification.
- Independent read-only review found no Critical or Important findings.
- `git diff --check` passed, and production source has no diff from the base.
- Refreshed JaCoCo snapshot: 184/204 lines (90.2%) and 31/44 branches (70.5%).

The tests use one isolated broker/database pair for the class, unique event IDs, captured producer offsets, per-scenario database/DLQ baselines, sequential test execution, bounded waits, and unconditional release-before-stop cleanup. Existing HTTP flow tests continue to verify publication through REST.
