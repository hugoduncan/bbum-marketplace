# Steps — 002 One Star Per GitHub User

## Phase 1 — Client (star.clj)

- [ ] Change stub file path to `registry/star-requests/<lib-slug>.edn`
      (was `registry/stars/<lib-slug>/<project-slug>.edn`)
- [ ] Simplify `build-star-entry` — stub content: `{:starred-at "<date>"}`
- [ ] Remove `gh api user --jq .login` from `run` and `star-if-known`
- [ ] Update `star-exists?` — list `registry/stars/<lib-slug>/` via GitHub
      contents API; return true if any file exists for current actor
      OR simplify to always proceed (CI enforces; best-effort check optional)
- [ ] Update PR branch name: `star-request/<lib-slug>`
- [ ] Update PR title/body: "⭐ star-request: <lib>", note "CI will record star"
- [ ] Update user messaging: "Star request submitted — CI records your star"

## Phase 2 — CI workflow (registry-pr.yml)

- [ ] Add `record-star` job (parallel to or replacing `auto-merge-stars`):
      - Step: identify changed files via `gh api` pulls files endpoint
      - Step: guard — all files under `registry/star-requests/`; exactly one;
              extract lib-slug; confirm lib-slug present in `registry/libraries/`
      - Step: dedup — check `registry/stars/<lib-slug>/${{ github.actor }}.edn`
              exists on `main`; if yes, close PR with comment and exit
      - Step: write star — `git checkout main`, write EDN, commit, push
      - Step: close stub PR with success comment "⭐ star recorded for @<actor>"
- [ ] Remove (or gate-off) old `auto-merge-stars` job
- [ ] Confirm `count-stars.yml` fires on the direct-to-main push (it will)

## Phase 3 — Tests

- [ ] Extract guard/dedup/write logic into `bbum-marketplace.star-record` ns
      (pure functions; no side effects; testable without GitHub)
- [ ] Add `test/bbum_marketplace/star_record_test.clj`:
      - valid star request → produces correct star file EDN
      - duplicate (actor already in stars/) → returns :duplicate
      - invalid path (not under star-requests/) → returns :invalid
      - lib slug not in registry → returns :unknown-lib
- [ ] Update `star_test.clj` for new stub path and simplified entry shape
- [ ] Run full test suite — all green

## Done

- [ ] Update `mementum/state.md`
- [ ] Commit `🎯 one-star-per-github-user complete`
