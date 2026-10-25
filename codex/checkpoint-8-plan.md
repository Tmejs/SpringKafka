# Remove the custom raw request-size filter

Goal: remove RawRequestSizeFilter and its dedicated tests, retaining a simple controller-level publication limit.

Approved scope: user accepted removing the filter after discussion that early body-size enforcement belongs to deployment infrastructure. Keep the existing 64 KiB UTF-8 message limit and HTTP 413/PAYLOAD_TOO_LARGE response. The controller reads the body before this check; do not claim application-level bounded reads. No ingress is currently included in the Kubernetes example.

- [x] Add controller regressions for oversized ASCII/UTF-8 and an accepted exact UTF-8 boundary. Observe the former controller returns 400 without the filter.
- [x] Delete filter and filter tests. Use ResponseStatusException(413) and map its existing API error code in ApiExceptionHandler to preserve the public contract.
- [x] Remove filter from README package map and document after-read UTF-8 publication limits versus ingress request-body protection; update OpenAPI wording.
- [x] Run clean verify, API YAML parsing and diff/reference checks; obtain independent review; generate current coverage under target/site/jacoco.
- [x] Record evidence, push checkpoint, merge --no-ff into clean synchronized main, verify merged main, push and compare SHAs. Keep completed branch.

Baseline: clean synchronized main/origin/main 679fdb08c993449346ea52a746b87578133a2429. Work in managed isolated checkpoint branch.

Review focus: no filter registration remains; no message over the UTF-8 limit is published; exact boundary is allowed; HTTP 413 and existing JSON error code preserved; no false claim of early memory protection or implemented ingress configuration; Kafka/retry/validation behavior unchanged.

## Verification results

Implementation 0a9508c removes the filter and preserves the publication contract. Red controller run: two expected failures (wanted 413, got 400). Green clean verify: 49 unit tests and 14 integration tests, no failures/errors/skips. Independent review PASS with no Critical/Important findings. OpenAPI YAML parsing and reference/diff checks passed. Coverage generated normally in target/site/jacoco: 169/178 lines (94.9%), 25/33 branches (75.8%). Historical committed report snapshot unchanged. Release follows the user's existing workflow.
