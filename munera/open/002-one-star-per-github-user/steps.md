# Steps — 002 One Star Per GitHub User

## Phase 1 — Client (star.clj)

- [x] Change stub file path to `registry/star-requests/<lib-slug>.edn`
      (was `registry/stars/<lib-slug>/<project-slug>.edn`)
- [x] Simplify `build-star-entry` → `build-star-request` — stub content: `{:starred-at "<date>"}`
- [x] Remove `gh api user --jq .login` from `run` and `star-if-known`
- [x] Drop client-side `star-exists?` — CI enforces; best-effort check removed
- [x] Update PR branch name: `star-request/<lib-slug>`
- [x] Update PR title/body: "⭐ star-request: <lib>", note "CI will record star"
- [x] Update user messaging: "Star request submitted — CI records your star"

## Phase 2 — CI workflow (registry-pr.yml)

- [x] Replace `auto-merge-stars` job with `record-star` job:
      - Step: guard — all files under `registry/star-requests/`, exactly one,
              extract lib-slug, confirm lib-slug in `registry/libraries/`
      - Step: dedup — check `registry/stars/<lib-slug>/${{ github.actor }}.edn`
              on `main`; if exists, close PR with "already starred" comment
      - Step: write star — fresh clone with write token, write EDN, commit to master
      - Step: close stub PR with success comment

## Phase 3 — Tests

- [x] Add `bbum-marketplace.star-record` ns with pure functions:
      `parse-star-request-path`, `star-file-path`, `lib-entry-path`,
      `build-star-edn`, `validate-star-request`
- [x] Add `test/bbum_marketplace/star_record_test.clj` — all cases covered
- [x] Update `star_test.clj` — `build-star-request` (no args), drop `star-exists?`
- [x] Update `validate.clj` — accept new star format (`:github/user`) and legacy (`:project`)
- [x] Full test suite: 34 tests, 150 assertions — all green

## Done

- [ ] Update `mementum/state.md`
- [ ] Close task
