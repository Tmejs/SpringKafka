# Checkpoint 1 verification

Tested source: `2b42d80` (the same source tree before the commit was recorded).

| Check | Result |
| --- | --- |
| Java 21 `./mvnw -q clean verify` | Passed on 2026-10-22: 33 unit tests and 8 Testcontainers integration tests; no failures or errors. |
| JaCoCo | Generated HTML and XML under `codex/reports/coverage/`: 184/204 lines (90.2%) and 31/44 branches (70.5%). |
| PostgreSQL/JPA | Repository integration tests passed for idempotent replay, conflicting event IDs, and stable pagination. Hibernate schema validation passed against the Flyway migration. |
| Kafka flow | Integration tests passed for typed and raw publication, valid events, malformed/invalid events to DLQ, replay behavior, raw body limit, and UTF-8 raw text preservation. |
| Docker Compose | Rebuilt stack healthy; app and Kafka published on `127.0.0.1` only. Live requests returned readiness `200`, oversized raw `413`, typed publish `202`, and authenticated read `200`. |
| `git diff --check` | Passed before independent re-review. |

The Kubernetes manifest was inspected and the YAML parsed, but a live Kubernetes probe test was unavailable because no cluster was connected. The local Compose health check and HTTP readiness endpoint were exercised instead.
