# Checkpoint 16 verification: shortened README

Date: 2026-10-26
Baseline: 545f2c8b2d94b8ee1c6219034f8955bbe5f7acc7

README reduced from154 to64lines. Only requested sections remain: title/introduction, Run locally, Swagger UI, Kafka browser UI, Try the two publication paths, Test and coverage. Added user-requested reviewer-guide link in introduction. Local setup and test sections preserved exactly. Both UI sections are one sentence with a URL; publication section retains typed/raw POST and asynchronous GET examples. No runtime/configuration/test changes.

- Main clean and synchronized with origin/remote before checkpoint.
- Section-preservation and heading checks: PASS.
- Three local links resolve; all Bash examples pass bash -n.
- git diff --check: PASS.
- Java21 ./mvnw -q clean verify: PASS;90unit/33integration tests, zero failures/errors/skips. Local log: /tmp/nordea-checkpoint-16-verify.log.
- Independent checkpoint_16_review: no Critical or Important findings.

Push/compare checkpoint, merge no-ff into clean synchronized main, verify merged README links/headings/whitespace, push/compare main. Documentation-only merged verification does not require repeating runtime tests. Preserve branch; local services unchanged.
