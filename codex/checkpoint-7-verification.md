# Checkpoint 7 verification: remove ISIN

Date: 2026-10-25
Baseline: 3cf364ea937c8466f0d87e3cb45a353556765e6e
Verified implementation: 08cf648

## Scope

User requested removing ISIN from the simplified recruitment project. Removed fundIsin from event and read DTOs, persistence entity, native insert columns/parameters, V1 initialization SQL, current JSON fixtures/examples and OpenAPI/AsyncAPI. Schema changes are applied directly to V1, honoring the user's existing instruction; no V2 migration or user database reset was performed. Existing development databases require recreation, as documented.

Partner code validation, event/order IDs, status, timestamp safeguards, native ID-only deduplication, Kafka routing and retry/DLQ policy are unchanged. Historical specs and generated snapshot remain historical.

## Verification

- Baseline unit suite: 49 tests passed.
- Test-first typed publish fixture without ISIN: failed as expected (wanted HTTP 202, received 400).
- Full verification initially found one remaining removed accessor in a repository test. The obsolete argument was removed; independent re-review confirms resolution.
- Full Java 21 ./mvnw -q clean verify: PASS, 49 unit tests and 14 integration tests; zero failures, errors or skips.
- Typed and raw publishing, Kafka batch persistence and authenticated reading operate with the smaller event model.
- Query controller tests assert fundIsin absent in both paginated and single-event responses; HTTP/Kafka end-to-end test also asserts absence.
- Replaced obsolete ISIN-format test with partner-code format validation; other validation and poison/replay regressions remain green.
- Fresh PostgreSQL/Flyway schema initialization and Hibernate validation pass with ISIN removed from V1.
- OpenAPI and AsyncAPI YAML parsing: PASS.
- Reference scan: no ISIN field in production code, V1, README examples or current contracts. Only three deliberate test assertions about absence remain in src.
- git diff --check and staged checks: PASS.
- Generated coverage at target/site/jacoco/index.html and jacoco.xml: 187/205 lines (91.2%), 31/44 branches (70.5%). Existing historical codex snapshot not refreshed.

## Independent review

checkpoint_7_review initially identified the obsolete repository test constructor argument as Important; it was fixed and re-reviewed. Final verdict PASS: no remaining Critical or Important findings. Review covered model/schema/query alignment, fixtures, current contracts, validation, deduplication and routing/retry preservation. Reviewer performed no builds or mutations.

## Release procedure

Push checkpoint branch and compare local/tracking/remote SHAs. Merge --no-ff into clean synchronized main, rerun full clean verify on merged main, push main and compare all main SHAs. Preserve checkpoint branch. No hosted CI added.

## Concurrent edit preservation

User authorized preserving repository formatting separately: commit 62b7016 contains only two mapping-call line breaks. Independent review PASS; combined clean verify again passed all 49 unit and 14 integration tests.

Two later unrelated main edits (timestamp validation removal and a JSON fixture change) were explicitly excluded from integration at the user's direction. They are preserved locally in commit a7a698f on codex/checkpoint-7-saved-local-edits. That work-in-progress branch is not merged or published. Main was returned to a clean state through the saved branch commit, without discarding these edits. The tested ISIN checkpoint retains timestamp validation.
