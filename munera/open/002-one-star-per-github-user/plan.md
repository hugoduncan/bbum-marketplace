# Plan — 002 One Star Per GitHub User

## Approach

CI is the sole writer of star files.  The client sends a stub PR; the workflow
resolves identity from `github.actor`, checks for duplicates, and commits the
real star file directly to `main`.  This eliminates all client-side identity
resolution and all filename/content validation in CI.

Three coordinated changes:

1. **`star.clj`** — stub file path, remove `gh api user` call, update messaging.
2. **`registry-pr.yml`** — replace `auto-merge-stars` with `record-star` job
   that writes the canonical star file directly to `main` and closes the stub PR.
3. **Tests** — new `star-validate` namespace for pure CI logic; update existing
   star tests.

## Phase Sequence

### Phase 1 — Client (`star.clj`)

- Change stub file path to `registry/star-requests/<lib-slug>.edn`.
- Remove `gh api user --jq .login` (no longer needed for file generation).
- Simplify `build-star-entry` — stub content is minimal.
- Simplify `star-exists?` — can check `registry/stars/<lib-slug>/` via GitHub
  API listing (no username needed) or drop the client-side check entirely
  (CI enforces; best-effort only).
- Update PR title/body/messaging.

### Phase 2 — CI workflow (`registry-pr.yml`)

Replace `auto-merge-stars` job with `record-star`:

```
steps:
  guard:   all files under registry/star-requests/; lib slug valid
  dedup:   registry/stars/<lib-slug>/<actor>.edn absent on main
  write:   commit star file directly to main (contents: write already present)
  close:   close stub PR with success comment
```

On duplicate: close PR with "already starred" comment, no write.
On invalid path: fall through to validate + human review.

### Phase 3 — Tests

Extract the guard/dedup/write logic into `bbum-marketplace.star-record` (pure
Clojure functions exercisable by `bb test`).  Test cases:

- valid request → star file written
- duplicate actor → rejected, no write
- invalid path → falls through
- lib slug not in registry → rejected

### Cleanup

- Remove `auto-merge-stars` job from `registry-pr.yml` (superseded).
- The `validate` job continues to handle library-entry PRs unchanged.

## Risks

- Direct push to `main` requires `contents: write` — already present in the
  workflow.  Branch protection rules (if any) must permit bot pushes; currently
  not enabled.
- Stub PRs are closed, not merged — conventional but not the usual GitHub UX.
  The success comment makes the outcome clear to the user.
- `count-stars.yml` fires on every push to `main` already — no change needed.
