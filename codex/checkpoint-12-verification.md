# Checkpoint 12 verification: Swagger UI and generated OpenAPI

Date:2026-10-25
Baseline:8beaa9fccef4abed63a69576e4d8c6eb27118963
Preserved staged validator deletion:319cc2b
Verified implementation:d5dc536710e18e15276f176e487f7007b80718fe

## Implementation

Pinned springdoc3.2.0 for the Boot4.1 line; tested with this application's Boot4.1.1. Swagger UI, bundled assets, /v3/api-docs and swagger-config remain covered by existing Basic authentication. Controller/model annotations and OpenApiConfiguration supply response/error metadata, validation details, API info, relative server URL and Basic security scheme.

The real FundOrderFlowIT HTTP context validates and exports target/openapi/openapi.json during Maven integration testing/verify. Failsafe config supplies the build-directory output path. No second application process, documentation-only stub infrastructure or extra generation plugin is needed. Package-only builds stop before the export test; all packaged apps still generate runtime docs. This lifecycle boundary was stated before implementation and is documented explicitly.

Removed handwritten docs/api/openapi.yaml. User-staged scripts/validate-contracts.cjs removal preserved separately; active README/demo references updated. AsyncAPI remains the independent Kafka contract. Historical checkpoint/design records remain unchanged.

## Verification

- Clean synchronized main/origin/main/remote before preserving the staged deletion on the checkpoint branch and using an isolated managed worktree.
- RED:two new documentation integration tests failed with expected200/actual404 before the starter/configuration was added.
- Focused initial GREEN:both tests passed, authenticated Swagger page/config and runtime document worked and artifact exported.
- Review found two Important generated-schema mismatches:pagination string schemas/missing defaults and unanchored partner-code pattern. Strengthened regression assertions reproduced expectedinteger/actualstring before the correction.
- Corrected schemas now declare integer/int32 pagination defaults0/20 with bounds and the full-match partner regex ^[A-Z0-9_]{2,32}$. Java validation retains its existing full-string behavior.
- Full final Java21 ./mvnw -q clean verify:PASS,52 unit tests and18 integration tests, zero failures/errors/skips.
- Documentation tests verify unauthenticated401 and authenticated200 for UI HTML, bundled Swagger JavaScript, document and Swagger config. Contract assertions cover3business paths,202publication, JSON/text request media, error/receipt/page schemas, enum/required fields, pagination names/types/defaults/bounds, Basic scheme and relative server URL before export.
- Generated OpenAPI3.0.1 passed @apidevtools/swagger-parser13.1.0 schema/reference validation. No exported credentials or test-only server port; generated files remain ignored under target/.
- git diff --check:PASS. Current fresh JaCoCo:168/177 lines (94.9%),24/32 branches (75.0%).

## Independent review

checkpoint_12_review:PASS after correcting and re-reviewing both Important findings. No remaining Critical/Important findings. Reviewer inspected source and generated document without builds or modifications.

## Merge resumed on 2026-10-26

User explicitly authorized merging and including unstaged edits. The only edit removed an obsolete fundIsin assertion from FundOrderQueryControllerTest. Preserved separately as6c10efa, focused test passed, and independent reviewer confirmed no Critical/Important findings. Main was restored clean only after exact patch preservation. The complete branch cleanverify was rerun:52unit/18integration tests, zero failures/errors/skips; regenerated OpenAPI schema/reference validation passed again. The previously executed Docker image package build also passed.

## Release procedure

Push checkpoint and compare local/tracking/remote SHAs; merge --no-ff into clean synchronized main; rerun full gate and generated-schema validation; push main and compare SHAs. Rebuild/recreate only the app service in the existing local Compose stack and verify authenticated Swagger/docs runtime access. Preserve checkpoint branch. No hosted CI added.
