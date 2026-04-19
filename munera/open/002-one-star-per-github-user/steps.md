# Steps — 002 One Star Per GitHub User

## Phase 1 — Client (star.clj)

- [ ] Add `(defn- github-username [] ...)` — calls `gh api user --jq .login`;
      throws with a helpful message if unauthenticated or gh unavailable
- [ ] Update `build-star-entry` to accept and embed `:github/user`
- [ ] Update `open-star-pr!` — pass username; use `<username>.edn` as filename
      (not project-slug)
- [ ] Update `star-exists?` — check `<lib-slug>/<username>.edn`
- [ ] Update `star-if-known` auto-hook to use the same username resolution
- [ ] Update user-facing messages to reference username

## Phase 2 — CI validation (`auto-merge-stars.yml`)

- [ ] Extract or add a `validate-star-pr` step that:
      - Gets list of files changed vs `main` via `git diff --name-only origin/main`
      - Confirms all files are under `registry/stars/`
      - Confirms each changed file is new (not modified)
      - Parses `<lib-slug>/<username>.edn` from path and asserts username == `${{ github.actor }}`
      - Checks that `registry/stars/<lib-slug>/${{ github.actor }}.edn` does not
        already exist on `main` (duplicate star guard)
      - Reads the EDN and asserts `:github/user` == `${{ github.actor }}`
- [ ] Gate the auto-merge step on validate passing

## Phase 3 — Tests

- [ ] Add `bbum-marketplace.star-validate` namespace with pure validation fns
      (extracts logic from the CI shell script for unit-testability)
- [ ] Add tests in `star_validate_test.clj`:
      - valid star PR passes
      - mismatched actor/filename fails
      - duplicate (already starred) fails
      - modified (not added) file fails
      - file outside `registry/stars/` fails
- [ ] Update `star_test.clj`:
      - `build-star-entry` now includes `:github/user`
      - `star-exists?` checks username path
- [ ] Run full test suite — all green

## Done

- [ ] Update `mementum/state.md`
- [ ] Commit with message `🎯 one-star-per-github-user complete`
