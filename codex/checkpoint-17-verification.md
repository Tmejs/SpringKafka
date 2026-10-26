# Checkpoint 17: concise authentication documentation

Date: 2026-10-26
Baseline: f40babab6633bcb66b564666e5ebb84176284d92

Plan and result: add exactly three sentences under Authentication in README describing default Basic credentials, explicit JWT profile/trust configuration, disabled Basic fallback and API scopes, with a link to the existing JWT guide. Preserve all other README content.

Verification: exact three-sentence count and otherwise-identical content confirmed programmatically; all local links resolve; environment names/profile guards/scopes checked against application.yml, application-jwt.yml and both security configurations; git diff --check passes. Independent checkpoint_17_review found no Critical or Important issues. No source, configuration or tests changed; applicable verification is documentation validation, so runtime tests were not repeated (last complete gate: checkpoint16,90unit/33integration passing).

Workflow: clean synchronized baseline, isolated checkpoint branch, commit/push and compare SHAs, no-ff merge into clean synchronized main, repeat document/whitespace checks, push/compare main. Preserve branch.
