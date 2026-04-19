# Steps

## Phase 1 — Registry scaffold + CI ✓

- [x] Create `registry/libraries/hugoduncan-bbum.edn` seed entry
- [x] Create `registry/libraries/hugoduncan-bbum-marketplace.edn` seed entry
- [x] Create `registry/stars/hugoduncan-bbum/.gitkeep`
- [x] Create `registry/stars/hugoduncan-bbum-marketplace/.gitkeep`
- [x] Write `.github/workflows/registry-pr.yml`:
  - trigger on PR affecting `registry/**`
  - job `validate`: structural checks + URL reachability for changed library entries
  - job `auto-merge-stars` (needs validate): guard checks only `registry/stars/**/*.edn`
    changed; if so squash-merges via `gh pr merge`
- [x] Write `.github/workflows/count-stars.yml`:
  - trigger on push to master affecting `registry/**`
  - runs `bb count-stars`; commits `🔄 update star counts` if anything changed
- [x] Write `bb.edn` dev tasks: `test`, `validate-registry`, `count-stars`
- [x] Write `src/bbum_marketplace/validate.clj` — structural validator + optional URL check
      (`VERIFY_URLS=true` env; used by CI)
- [x] Write `src/bbum_marketplace/count_stars.clj` — star file counter; updates `:stars`
- [x] Run `validate-registry` locally against seed entries — passes

## Phase 2 — Publisher ✓

- [x] Write `src/bbum_marketplace/util.clj`:
  - `lib->slug`, `git-url->slug`, `http-get`, `http-get-json` (cheshire),
    `read-edn-str`, `write-edn`, `find-file-upward`, `print-table`,
    `cache-path`, `read-cache`, `write-cache`
- [x] Write `src/bbum_marketplace/publish.clj`:
  - reads local `bbum.edn`; derives slug, git URL, owner; prompts for
    description + tags; checks entry existence via GitHub API HEAD;
    forks + clones marketplace repo to temp dir; `gh pr create`;
    prints PR URL
- [x] Write `test/bbum_marketplace/publish_test.clj`
- [x] Write `bbum.edn` declaring all marketplace tasks as a bbum task library
- [ ] Smoke test: run `bb marketplace:publish` in this repo → verify PR → CI → merge
  _(requires live GitHub interaction; deferred to manual QA)_

## Phase 3 — Consumer CLI ✓

- [x] Write `src/bbum_marketplace/catalogue.clj`:
  - `fetch-catalogue`: GitHub contents API → per-file raw fetch → EDN parse → sorted vec
  - `catalogue`: cache (1hr TTL) → fetch if miss or `:force true`
  - auth via `GITHUB_TOKEN` env; helpful error on API failure
- [x] Write `test/bbum_marketplace/catalogue_test.clj` (with-redefs HTTP mocking)
- [x] Write `src/bbum_marketplace/list.clj`:
  - `--sort stars|name|added`, `--tag TAG`, `--refresh`; tabular output
- [x] Write `src/bbum_marketplace/search.clj`:
  - case-insensitive substring match on `:lib`, `:description`, `:tags`
- [x] Write `src/bbum_marketplace/info.clj`:
  - lookup by lib name or slug; full detail; live bbum.edn task fetch;
    install hint
- [x] All tasks declared in `bbum.edn`
- [ ] Smoke test: live catalogue queries _(deferred to manual QA)_

## Phase 4 — Auto-star ✓

- [x] Write `src/bbum_marketplace/star.clj`:
  - `run`: find entry by name/slug; derive project slug from git remote;
    check star existence; fork + clone; write star file; `gh pr create`
  - `star-if-known [git-url]`: URL match → opt-out check → silent star;
    best-effort, never crashes caller
- [x] Write `test/bbum_marketplace/star_test.clj`
- [x] `marketplace:star` declared in `bbum.edn`
- [x] Opt-out documented in README: `{:marketplace {:auto-star false}}`
- [ ] Live CI verification: star-only PR auto-merges; library-entry PR does not
  _(deferred to manual QA)_
- [ ] Live CI verification: merge triggers `count-stars.yml` → `:stars` updated
  _(deferred to manual QA)_

## Phase 5 — Docs + skill ✓

- [x] Write `README.md`
- [x] Write `CONTRIBUTING.md`
- [x] Write `skills/bbum-marketplace/SKILL.md`
- [ ] Final end-to-end smoke test from a fresh consumer project _(deferred)_
