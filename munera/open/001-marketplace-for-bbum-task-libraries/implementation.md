# Implementation Notes

_Append-only. Decisions and discoveries recorded as work proceeds._

## 2026-04-19 — Publish smoke test (PR #1)

`bb marketplace:publish` from `hugoduncan/bb-task-lib` successfully opened
https://github.com/hugoduncan/bbum-marketplace/pull/1.

**Bug hit:** `gh repo fork` fails with "A single user account cannot own both a
parent and fork" when the publisher is the repo owner. Fixed by adding
`clone-for-pr!` to `util.clj` — detects push access via `gh api
repos/<repo> --jq '.permissions.push'` and clones directly when true.

**Secondary bug:** `bbum update marketplace:publish` re-copied `publish.clj` but
not `util.clj` (owned by separate implicit `marketplace:util` task). Fixed by
listing `util.clj` directly in the `:files` of every user-facing task in
`bbum.edn`. Backward-compat stubs for `marketplace:util` and
`marketplace:catalogue` kept in manifest so existing installs don't error on
`bbum update`.

**Pattern learned:** don't use a shared internal task just to hold a shared file.
List the shared file directly in the `:files` of every task that needs it. bbum
deduplicates on install; the benefit is that updating any one task refreshes the
shared file too.
