# Checkpoint 4: Kafka topic configuration

Approved design: retain Spring Boot's KafkaAdmin, extract NewTopic beans into KafkaTopicConfiguration, bind topic settings through validated KafkaTopicProperties, externalize replication and topic creation, and document local versus production ownership.

## Tasks

- [x] Add configuration tests for local defaults, overridden topic names/partition/replication settings, invalid topic settings, and Boot-provided admin configuration. Observe a failure for the currently hardcoded replication factor.
- [x] Extract topic beans and add validated `app.kafka` topic properties. Preserve equal source/DLQ partition counts and existing topic bean names. Keep listener and retry behavior unchanged.
- [x] Add replication factor (default 1) and `spring.kafka.admin.auto-create` (default true) environment mappings. Pass them through Compose and .env.example. Disable application topic creation in the Kubernetes example. Pin integration tests to creation enabled and one replica.
- [x] Update README ownership and operational limitations: pre-provision production topics, matching partitions, partition increases and replica reassignment limitations, and distinction from broker automatic topic creation.
- [x] Run clean verify, Compose/Kubernetes configuration checks, diff checks, independent review, and refresh committed coverage/evidence. Push checkpoint, merge --no-ff, verify merged main and synchronize remote SHAs under the user's workflow.

## Review focus

- Spring Boot still creates exactly one KafkaAdmin and inherits bootstrap settings.
- Topic overrides affect both beans; one broker remains supported by default.
- Blank names, nonpositive counts and unrepresentable replication factors fail startup.
- Kubernetes requires pre-provisioning; disabling admin creation does not disable broker-side automatic creation.
- No producer, consumer, retry, offset, database, REST, or authentication behavior changes.

## Progress

Baseline main `268a5a0` synchronized with origin; clean worktree; 33 unit tests pass.

Implementation `88ec6d0`: topic configuration tests first failed on ignored replication and accepted invalid values; after implementation 41 unit tests and 10 integration tests pass. Compose defaults/overrides and Kubernetes YAML/probes checked. Independent review PASS; no Critical/Important or code findings. Verification evidence and coverage refreshed before release.
