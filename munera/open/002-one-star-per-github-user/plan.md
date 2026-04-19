# Plan — 002 One Star Per GitHub User

## Approach

Three coordinated changes:

1. **`star.clj`** — client writes username-keyed files and `:github/user` field.
2. **`auto-merge-stars.yml`** — CI gains a validation step that enforces
   actor-matches-filename and rejects duplicate stars before merging.
3. **Tests** — unit tests for the new validation logic; update existing star
   tests to use the new file-naming scheme.

Work bottom-up: tests → client → CI workflow.

## Phase Sequence

### Phase 1 — Client (`star.clj`)

- Resolve GitHub username via `gh api user --jq .login`.
- Use username as star filename.
- Embed `:github/user` in the star EDN.
- Update `star-exists?` to look up `<github-username>.edn`.
- Update friendly messaging.

### Phase 2 — CI validation step

Add a `validate-star-pr` job (or step within `auto-merge-stars.yml`):

```
- Collect files changed in the PR (using git diff against main).
- For each file:
    - Confirm path matches registry/stars/<lib-slug>/<actor>.edn
    - Confirm it is a new file (not an edit)
    - Confirm :github/user in EDN == github.actor
    - Confirm no existing registry/stars/<lib-slug>/<actor>.edn on main
- On any failure: fail with descriptive message; suppress auto-merge.
```

Uses only `gh`, `git`, and `bb` — no new dependencies.

### Phase 3 — Tests

- `test/bbum_marketplace/star_test.clj` — add cases:
  - `star-filename` uses GitHub username
  - `:github/user` present in built entry
  - `star-exists?` checks correct path
- New test namespace `test/bbum_marketplace/star_validate_test.clj` (or a
  script exercised via `bb test`) covering the CI validation logic extracted
  into a pure Clojure function in a new `bbum-marketplace.star-validate` ns.

## Risks

- `gh api user` requires an authenticated session — same requirement as the
  rest of the star flow; no new constraint.
- Old project-slug star files already merged to `main` are benign: the
  duplicate check is keyed on username, so they do not block future stars.
  (Could be cleaned up later in a separate task.)
- A user who is not the GitHub owner of the project they claim can still star —
  we only verify *who opened the PR*, not who owns the project.  This is
  intentional and sufficient to prevent multi-starring.
