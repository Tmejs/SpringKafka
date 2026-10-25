# Checkpoint 11: Kafka browser UI

Add the agreed Kafbat UI service to Compose, bound to localhost:8081 and connected to kafka:19092 after broker health. Update README and interview guide with topic/DLQ/consumer-group navigation and local authentication scope.

Validate Compose and the full Maven gate. Exercise the UI and Kafka connectivity in a disposable two-service Compose project on unused ports, preserving the running application stack. Obtain independent review, record evidence, push checkpoint, merge --no-ff, verify merged main, push and compare SHAs. Then start only Kafka UI from the primary checkout alongside the existing stack and verify localhost:8081. No application behavior changes or CI additions.
