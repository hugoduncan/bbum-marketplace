# 002 — One Star Per GitHub User

## Goal

Ensure a single GitHub user can star a given library at most once, and that the
enforcement is done on the server side (CI) so it cannot be bypassed by the
client-side star tool or by direct PRs.

## Context

Currently a star file is keyed by *project slug* — derived from the starring
project's git remote URL.  A determined user could create multiple throw-away
repos (or rotate their project's remote URL) to inflate a library's star count.
The PR opener's GitHub identity (`github.actor`) is the only trustworthy,
unforgeable signal available in a GitHub Actions context.

### Current star path

```
registry/stars/<lib-slug>/<project-slug>.edn
```

`project-slug` is the slugified git URL of the *starring project*, not the
GitHub username of the person running the tool.  Nothing in the current flow
binds a star to a human identity.

## Constraints

- No server/database.  Enforcement must be expressible in GitHub Actions.
- Must not break the happy path: a user with one legitimate project stars a
  library they actually use.
- `github.actor` is the unforgeable identity anchor.

## Design

### Key insight: CI is the sole writer of star files

Rather than having the client write the canonical star file and CI validate that
it matches the actor, CI *generates* the file.  This eliminates an entire
validation class — a filename/content mismatch is impossible if CI is the only
writer.

### Flow

```
Client                            Marketplace repo CI
──────                            ─────────────────
1. Open PR with stub file         2. Trigger: pull_request opened
   registry/star-requests/           a. All changed files under
   <lib-slug>.edn                       registry/star-requests/?
   (content: minimal EDN)           b. Duplicate check:
                                       registry/stars/<lib-slug>/
                                       <actor>.edn exists on main?
                                       → close PR with "already starred"
                                    c. Write registry/stars/<lib-slug>/
                                       <actor>.edn directly to main
                                    d. Close stub PR with "⭐ starred"
```

The stub PR is a **signal**, not a delivery vehicle.  It is always closed (not
merged) — the real star file is committed directly to `main` by the workflow.

### Stub file

```
registry/star-requests/<lib-slug>.edn
```

Minimal content (informational only — CI never reads it for enforcement):

```edn
{:starred-at "2026-04-19"}
```

The lib-slug is parsed from the file path.  The actor comes from `github.actor`.
No `:github/user` field required; no client-side identity resolution needed.

### Real star file (written by CI)

```
registry/stars/<lib-slug>/<github-actor>.edn
```

```edn
{:github/user  "alice"
 :starred-at   "2026-04-19"}
```

`:project` and `:git/url` are omitted — they are not trustworthy from the client
and not needed for the identity guarantee.

### CI workflow changes (`registry-pr.yml`)

Replace the current `auto-merge-stars` job with a `record-star` job:

1. **Guard** — all changed files are under `registry/star-requests/`; exactly
   one file; filename matches a valid lib slug in `registry/libraries/`.
   Otherwise: do nothing (let the validate + human-review path handle it).
2. **Duplicate check** — `registry/stars/<lib-slug>/<actor>.edn` must not
   exist on `main`.  If it does: close the PR with a comment "already starred".
3. **Write star** — commit `registry/stars/<lib-slug>/<actor>.edn` directly to
   `main` using the existing `contents: write` permission.
4. **Close stub PR** — close (do not merge) the request PR with a success
   comment.

`count-stars.yml` already fires on every push to master → star counts stay
accurate automatically.

### Client-side (`star.clj`) changes

- Change stub file path to `registry/star-requests/<lib-slug>.edn`.
- Remove `gh api user --jq .login` call (no longer needed).
- Remove `:github/user` from the stub EDN.
- `star-exists?` still checks the GitHub API for
  `registry/stars/<lib-slug>/<actor>.edn` as a best-effort pre-flight; it needs
  `gh api user --jq .login` only for this check — or it can be skipped entirely
  (CI enforces it regardless).
- Update messaging: "Star request submitted — CI will record your star shortly."

### Simplification delta vs. previous design

| | Previous design | This design |
|---|---|---|
| Client resolves own GitHub username | Required | Not required |
| Client constructs star filename | Required | Not required |
| CI validates filename == actor | Required | Eliminated |
| CI validates EDN field == actor | Required | Eliminated |
| CI checks duplicate | Required | Required |
| CI writes star file | No | Yes (direct push to main) |

The validation that disappears (filename/field matching actor) was only needed
because the client was writing the file.  Removing client authorship removes the
attack surface entirely.

## Acceptance Criteria

1. A star PR opened by `alice` results in `registry/stars/<lib-slug>/alice.edn`
   on `main`, written by CI, containing `{:github/user "alice" …}`.
2. A second star PR from `alice` for the same library is rejected by CI with a
   clear comment; no duplicate file is written.
3. A user cannot game the system by:
   - Changing their project's git URL.
   - Creating extra repos.
   - Manually crafting a PR with a different stub path.
   (All result in at most one star file keyed to their `github.actor` identity.)
4. The client sends only a stub — it never writes to `registry/stars/`.
5. Existing star files (project-slug scheme) are unaffected.
6. All existing tests pass; new tests cover the CI record-star logic extracted
   into a testable Clojure namespace.
