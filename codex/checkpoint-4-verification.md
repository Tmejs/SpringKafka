# Checkpoint 4 verification: Kafka topic provisioning

Date: 2026-10-22
Baseline: `268a5a09302f6d7dcf72b9c976e5892b9554fa07`
Verified implementation: `88ec6d0`

## Changes and decisions

- KafkaTopicConfiguration owns the existing sourceTopic and deadLetterTopic beans; Spring Boot still owns KafkaAdmin and its shared connection configuration.
- KafkaTopicProperties validates topic names and positive partition/replication counts. A short replication factor also rejects overflow. Shared counts preserve DLQ partition routing.
- Added APP_KAFKA_REPLICATION_FACTOR=1 and APP_KAFKA_ADMIN_AUTO_CREATE=true mappings in application.yml, Compose, and .env.example.
- Kubernetes disables application topic creation and documents required pre-provisioning. Application topic declarations do not validate externally managed topic topology. Broker-side automatic topic creation is a separate setting.
- Integration tests explicitly enable topic creation with one replica. No changes to listener, retry, producer, persistence, REST or authentication semantics.

## Verification

- Baseline: 33 unit tests passed.
- Red: the new configuration tests failed on ignored replication overrides and accepted invalid settings. The initial lightweight test context needed Boot duration conversion while exercising the old mixed configuration; that harness issue was corrected before observing the intended failures. The extracted topic configuration no longer needs that conversion setup.
- Green: 8 new configuration cases passed, covering defaults, custom topic names/counts, five invalid property inputs, and Boot admin uniqueness/shared bootstrap settings with topic creation disabled.
- Full Java 21 `./mvnw -q clean verify`: PASS, 41 unit tests and 10 Kafka/PostgreSQL integration tests; zero failures, errors or skips.
- `docker compose --env-file .env.example config --quiet`: PASS. JSON rendering confirms replication and creation values pass through for local defaults and explicit overrides.
- Kubernetes YAML parsed successfully; topic creation is explicitly false and startup/readiness/liveness probes remain present. No live cluster or server-side Kubernetes validation was performed.
- `git diff --check` and staged diff checks: PASS.
- JaCoCo committed HTML/XML: 188/208 lines (90.4%), 31/44 branches (70.5%).

## Independent review

Fresh read-only reviewer checkpoint_4_review: PASS; no Critical/Important or code findings. Reviewed tracked and new source/test/configuration files against the baseline, and inspected installed Boot 4.1.1 admin configuration behavior. The sole administrative note was to complete the plan checkboxes; these are now updated.

## Release procedure

Push checkpoint branch and compare local/tracking/remote SHAs; merge --no-ff into synchronized clean main; rerun clean verify on the merge; push main and compare all main SHAs. Preserve checkpoint branch. No hosted CI added.
