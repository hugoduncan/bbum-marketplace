# Steps

## Phase 1 — Registry scaffold + CI

- [ ] Create `registry/libraries/hugoduncan-bbum.edn` seed entry
- [ ] Create `registry/libraries/hugoduncan-bbum-marketplace.edn` seed entry
- [ ] Create `registry/stars/hugoduncan-bbum/.gitkeep`
- [ ] Create `registry/stars/hugoduncan-bbum-marketplace/.gitkeep`
- [ ] Write `.github/workflows/validate-pr.yml`:
  - trigger on PR affecting `registry/**`
  - parse each changed library `.edn`: required keys, slug↔lib match, `:git/url` reachable
  - parse each changed star `.edn`: required keys, parent slug has a library entry
- [ ] Write `.github/workflows/count-stars.yml`:
  - trigger on push to master
  - for each library: count star files, update `:stars` if changed, commit + push
- [ ] Write `.github/workflows/auto-merge-stars.yml`:
  - trigger on PR open/sync
  - guard: all changed paths under `registry/stars/` only
  - if guard passes: approve + squash-merge
- [ ] Write `bb.edn` dev tasks: `test`, `validate-registry`
- [ ] Write `src/bbum_marketplace/validate.clj` — local registry validator (same logic as CI)
- [ ] Run `validate-registry` locally against seed entries; confirm passes

## Phase 2 — Publisher

- [ ] Write `src/bbum_marketplace/util.clj`:
  - `lib->slug`, `project->slug`
  - `http-get` (babashka.http-client wrapper)
  - `read-edn-str`
- [ ] Write `src/bbum_marketplace/publish.clj`:
  - read local `bbum.edn` (walk up from cwd)
  - derive slug; build entry map; prompt for description + tags if absent
  - check if entry already exists (GitHub API HEAD); offer update path
  - write entry to temp dir; `gh pr create` → branch `publish/<slug>-<date>`
  - print PR URL
- [ ] Write `test/bbum_marketplace/publish_test.clj`
- [ ] Add `marketplace:publish` task to `bbum.edn`
- [ ] Smoke test: run `bb marketplace:publish` in this repo, verify PR opened against itself, CI passes, human merges

## Phase 3 — Consumer CLI

- [ ] Write `src/bbum_marketplace/catalogue.clj`:
  - `fetch-catalogue`: GitHub contents API → fetch each `.edn` → parse → vec of maps
  - `catalogue`: read cache (1hr TTL at `~/.bbum/marketplace-cache.edn`); fetch + write if miss
  - respect `GITHUB_TOKEN` env for auth header; warn on 403/429
- [ ] Write `test/bbum_marketplace/catalogue_test.clj` (mock HTTP responses)
- [ ] Write `src/bbum_marketplace/list.clj`:
  - parse `*command-line-args*` for `--sort stars|name|added` and `--tag TAG`
  - fetch catalogue, sort + filter, print table (lib, description, tags, stars, url)
- [ ] Write `src/bbum_marketplace/search.clj`:
  - first positional arg = query string
  - case-insensitive substring match against `:lib`, `:description`, `:tags`
  - print same table as list
- [ ] Write `src/bbum_marketplace/info.clj`:
  - find entry by lib name or slug
  - print full detail
  - attempt live fetch of library's `bbum.edn` via GitHub raw API to list tasks
  - print install hint: `bbum source add` + `bbum add` commands
- [ ] Add `marketplace:catalogue`, `marketplace:list`, `marketplace:search`,
  `marketplace:info` tasks to `bbum.edn`
- [ ] Smoke test: `bb marketplace:list --sort stars`, `bb marketplace:search bbum`,
  `bb marketplace:info hugoduncan/bbum`

## Phase 4 — Auto-star

- [ ] Write `src/bbum_marketplace/star.clj`:
  - `run`: resolve lib by name/slug from catalogue; derive project slug from local git remote
  - check if star file exists (GitHub API HEAD) — no-op + message if already starred
  - write star file to temp; `gh pr create` → branch `star/<lib-slug>-<project-slug>`
  - print PR URL
  - `star-if-known [git-url]`: look up URL in catalogue; if found + not opted-out, call star
    logic silently; print one-line notice
- [ ] Write `test/bbum_marketplace/star_test.clj`
- [ ] Add `marketplace:star` task to `bbum.edn`
- [ ] Document opt-out in README: `{:marketplace {:auto-star false}}` in project `.bbum.edn`
- [ ] Verify `auto-merge-stars.yml` guard: open a test PR touching a star file only → auto-merges;
  open a PR touching a library entry → does not auto-merge
- [ ] Verify `count-stars.yml`: merge a star PR → `:stars` updated in library entry

## Phase 5 — Docs + skill

- [ ] Write `README.md`:
  - what it is, how publishers submit, how consumers install + use, how stars work
  - opt-out instructions, GITHUB_TOKEN note, bbin install hint
- [ ] Write `CONTRIBUTING.md`:
  - PR guidelines, CI requirements, auto-merge policy, manual review triggers
- [ ] Write `skills/bbum-marketplace/SKILL.md`:
  - λ publish: reads bbum.edn → marketplace:publish → PR URL
  - λ discover: marketplace:list / marketplace:search → source add → add
  - λ star: marketplace:star → PR URL
  - prerequisites: gh CLI, GITHUB_TOKEN (optional)
- [ ] Final smoke test: full end-to-end from a fresh consumer project
