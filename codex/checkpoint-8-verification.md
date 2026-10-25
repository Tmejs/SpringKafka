# Checkpoint 8 verification: remove raw request-size filter

Date: 2026-10-25
Baseline: 679fdb08c993449346ea52a746b87578133a2429
Verified implementation: 0a9508c

## Changes

Removed RawRequestSizeFilter and its three tests. The controller retains a 64 KiB UTF-8 message publication limit and now raises a ResponseStatusException with HTTP 413. ApiExceptionHandler maps that status to the existing PAYLOAD_TOO_LARGE code. Existing 400/404/503 handling remains unchanged.

README removes the filter package and explicitly distinguishes the after-read publication limit from early request-body protection. OpenAPI describes UTF-8 size semantics. Production ingress/reverse-proxy limits remain a deployment responsibility; no ingress configuration is claimed or added.

## Verification

- Clean synchronized main at checkpoint start. Baseline unit suite: 49 tests passed.
- Test-first controller run: oversized ASCII and multibyte cases failed with expected 413 versus actual 400, proving the response contract would change without the adjustment.
- Full Java 21 ./mvnw -q clean verify after implementation: PASS, 49 unit tests and 14 integration tests, zero failures/errors/skips.
- New controller coverage: 65,537 ASCII characters and 32,769 two-byte UTF-8 characters reject with 413/PAYLOAD_TOO_LARGE and do not invoke publication. Exactly 32,768 two-byte UTF-8 characters are accepted and passed unchanged to the publisher.
- Existing HTTP/Kafka integration confirms oversized raw messages return 413; publishing, reading, DLQ, poison timestamps, batch offsets and ID-only replay tests remain green without the filter.
- OpenAPI YAML parsing: PASS.
- Source/reference scan: no removed filter class or registration remains in current implementation. Historical records are preserved.
- git diff --check and staged checks: PASS.
- Current coverage target/site/jacoco/index.html and jacoco.xml: 169/178 lines (94.9%), 25/33 branches (75.8%). No new generated report copied under codex.

## Independent review

checkpoint_8_review: PASS, no Critical or Important findings. Reviewed filter deletion, 413 error mapping, UTF-8 boundary tests, after-read limitations, current docs and scope. Reviewer performed no builds or modifications.

## Release procedure

Push checkpoint and verify local/tracking/remote SHAs; merge --no-ff into clean synchronized main; rerun clean verify on merged main; push main and compare all main SHAs. Preserve checkpoint branch. No hosted CI added.
