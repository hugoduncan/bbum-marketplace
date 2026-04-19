# 002 — One Star Per GitHub User

## Goal

Ensure a single GitHub user can star a given library at most once, and that the
enforcement is done on the server side (CI) so it cannot be bypassed by the
client-side star tool or by direct PRs.

## Context

Currently a star file is keyed by *project slug* — derived from the starring
project's git remote URL.  A determined user could create multiple throw-away
repos (or rotate their project's remote URL) to inflate a library's star count.
The PR opener's GitHub identity is the only trustworthy, unforgeable signal
available in a GitHub Actions context.

### Current star path

```
registry/stars/<lib-slug>/<project-slug>.edn
```

`project-slug` is the slugified git URL of the *starrring project*, not the
GitHub username of the person running the tool.  Nothing in the current flow
binds a star to a human identity.

## Constraints

- No server/database.  Enforcement must be expressible in a GitHub Actions
  workflow reading the repo tree plus the PR actor.
- Must not break the happy path: a user with one legitimate project stars a
  library they actually use.
- The auto-merge workflow (`auto-merge-stars.yml`) is the enforcement gate.
  Client-side checks remain best-effort UX only.
- `gh` CLI is always available in CI; `github.actor` is the PR opener's login.

## Design

### Identity anchor: GitHub username

Replace (or supplement) the project-slug key with the **GitHub username** of
the PR opener.  The star file path becomes:

```
registry/stars/<lib-slug>/<github-username>.edn
```

A user can only ever have one file per library slug.  Any second PR that would
add another file from the same actor is rejected by CI.

### Star file content (extended)

```edn
{:github/user   "alice"
 :project       "alice/my-task-lib"   ; optional, informational
 :git/url       "https://github.com/alice/my-task-lib"
 :starred-at    "2026-04-19"}
```

`:github/user` is written by the client (from `gh api user --jq .login`) and
**verified** by CI against `github.actor`.  Mismatch → PR rejected.

### CI enforcement (`auto-merge-stars.yml`)

Added validation step before auto-merge:

1. **Identify changed files** — ensure all are under `registry/stars/`.
2. **For each changed file**:
   a. Parse the filename to extract `<github-username>`.
   b. Assert `<github-username> == github.actor`.  Mismatch → fail.
   c. Assert the file is a pure addition (not overwriting another user's star).
   d. Assert `:github/user` field in the EDN matches `github.actor`.
3. **Duplicate check** — after the addition, `registry/stars/<lib-slug>/` must
   contain at most one file named `<github-username>.edn`.  If a file already
   exists on `main` with that name, reject the PR (already starred).

Steps 2b and 2d together mean a user cannot forge another user's star even by
sending a manual PR — the PR actor is the ground truth.

### Client-side (star.clj) changes

- Determine the local GitHub username: `gh api user --jq .login`.
- Use `<github-username>` as the star filename (instead of project-slug).
- Write `:github/user` into the star EDN.
- `star-exists?` checks `registry/stars/<lib-slug>/<github-username>.edn`.
- Friendly message if already starred: "You (alice) have already starred …".

## Acceptance Criteria

1. Star files are named `<github-username>.edn`; `:github/user` is present.
2. A PR that adds a star file whose name does not match `github.actor` is
   rejected by CI with a clear error message.
3. A PR opened by a user who already has a merged star file for that library
   is rejected by CI.
4. A user who has already starred cannot game the system by:
   - Changing their project's git URL.
   - Creating a second project.
   - Manually crafting a PR with a different filename.
5. Existing star files (project-slug scheme) remain valid; migration is not
   required for the initial implementation (old files are ignored by the
   duplicate check).
6. All existing tests continue to pass; new tests cover the CI validation logic.
