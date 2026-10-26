# Checkpoint 19 verification: recruiter assessment and study guide

Date: 2026-10-26
Reviewed source baseline: ecbb650a0a516f9157f51189a0e7196f921dc843

Created docs/interview-study-guide.md with a recruiter assessment, prioritized improvement candidates, a presentation opening,36 project-specific questions and suggested answers, rehearsal scenarios and claims to avoid. The document links source/tests and official Kafka/Spring references and explicitly separates proposals from implemented behavior. Main README, application, configuration and tests unchanged.

## Evidence

- Clean local/origin/remote main synchronized before isolated checkpoint branch.
- Read actual listener/error handler, publisher, persistence/service/schema, controllers/models, Basic/JWT chains, Compose/Kubernetes, Maven plugins and test cases.
- Baseline local reports:91unit/27integration tests, zero failures/errors/skips; fresh JaCoCo238/247lines and41/52branches. Historical checked-in coverage snapshot explicitly distinguished. These are existing baseline results, not a new runtime execution in this documentation task.
- All17 local references resolve; questions numbered1–36 continuously; git diff --check passes. Verified no runtime/README diff.
- Technical Kafka retry/offset/poll/producer and Spring security/probe claims checked against primary documentation linked beside relevant answers.
- Independent checkpoint_19_review found one Important accuracy issue: adding InvalidEventException preserves Spring Kafka fatal defaults, so not every unexpected failure retries indefinitely. Corrected recommendations and answers7–9; re-review confirms no remaining Critical or Important findings.
- Runtime tests not repeated for documentation-only changes. Local evidence from checkpoint18 remains the latest applicable execution gate.

Push and compare checkpoint SHAs; no-ff merge into clean synchronized main; repeat merged document/reference/whitespace checks; push main and compare SHAs. Preserve branch; no runtime/deployment changes or hosted CI added.
