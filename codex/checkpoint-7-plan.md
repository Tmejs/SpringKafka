# Remove ISIN from the event contract

Goal: remove fundIsin from messages, persistence, read responses, examples and contracts, simplifying the fictional order-status model.

User approved removal and previously explicitly requested schema simplifications be made directly in V1 with no extra migrations. Keep partnerCode validation, IDs, status, timestamp limits, Kafka routing, deduplication and retry policies.

- [x] Change typed publishing and codec fixtures to omit ISIN; observe failure against the existing required-field validation.
- [x] Remove the field from event/read models, entity, native insert and V1. Adjust all constructor/JSON fixtures. Replace the obsolete ISIN-format test with partner-code validation coverage and verify read responses omit the removed field.
- [x] Update OpenAPI/AsyncAPI properties and required fields, README example and schema description. Existing development databases need recreation; retain the warning, add no upgrade migration.
- [x] Run clean verify, YAML parsing and reference/diff checks; obtain independent review and generate fresh coverage under target/site/jacoco.
- [x] Record evidence, push checkpoint, merge --no-ff into synchronized clean main, verify merged main, push and compare SHAs. Preserve checkpoint branch.

Baseline: clean synchronized main/origin/main 3cf364ea937c8466f0d87e3cb45a353556765e6e; 49 baseline unit tests passed in the isolated checkpoint worktree.

Review focus: requests without ISIN publish successfully, source/response/schema shape agrees, entity/native insert/V1 align, typed and raw end-to-end flow still works, no changes to partner validation or poison/replay handling. Historical plans/reports remain historical.

## Verification results

- Implementation 08cf648 removes ISIN from the current contract, persistence and V1, without an extra migration.
- Red typed-publish test: expected 202, actual 400 without ISIN.
- Initial full verification caught one obsolete repository test accessor; fixed and independently re-reviewed before final verification.
- Complete clean verify: 49 unit tests and 14 integration tests, zero failures/errors/skips.
- Independent final review: PASS, no remaining Critical or Important findings.
- Current source has only intentional fundIsin response-absence assertions; API YAML parsing and diff checks pass.
- Fresh coverage target/site/jacoco: 187/205 lines (91.2%), 31/44 branches (70.5%). Historical committed snapshot unchanged per directory preference.
- Release follows the established checkpoint workflow.

## Concurrent edit preservation

User authorized preserving repository formatting separately: commit 62b7016 contains only two mapping-call line breaks. Independent review PASS; combined clean verify again passed all 49 unit and 14 integration tests.

Two later unrelated main edits (timestamp validation removal and a JSON fixture change) were explicitly excluded from integration at the user's direction. They are preserved locally in commit a7a698f on codex/checkpoint-7-saved-local-edits. That work-in-progress branch is not merged or published. Main was returned to a clean state through the saved branch commit, without discarding these edits. The tested ISIN checkpoint retains timestamp validation.
