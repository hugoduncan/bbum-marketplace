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

## 2026-04-19 — CI URL check fix

`bb -e "(-> ... :git/url)"` in a bash `$()` captures the Clojure print-repr of a
string — e.g. `"https://..."` with surrounding quotes — so `git ls-remote` received
a URL with literal quote chars and failed.

Fix: replaced the entire bash URL-extraction step with `VERIFY_URLS=true bb
validate-registry`. The Clojure validator handles URL extraction internally with no
shell interpolation.

`gh run rerun` does NOT pick up workflow changes pushed to master after the run was
created — it replays the exact same workflow snapshot. To get the new workflow to run,
push a commit to the PR branch (triggers a fresh `pull_request` event that reads the
workflow from the current base branch).

## 2026-04-19 — End-to-end complete

PR #1 (`publish: hugoduncan/bb-task-lib`) merged successfully:
- `validate` job: passed (structural check + URL reachability via `VERIFY_URLS=true`)
- `count-stars` job: ran on push to master, found 3 libraries at 0 stars, no commit needed
- Registry now contains: hugoduncan/bbum, hugoduncan/bbum-marketplace, hugoduncan/bb-task-lib
