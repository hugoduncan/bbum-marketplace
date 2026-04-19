# Steps

## Phase 1 — Registry scaffold

- [ ] Define `registry/libraries/<lib-slug>.edn` schema (lib, url, description, tags, stars, submitted-by, submitted-at)
- [ ] Define `registry/stars/<lib-slug>/<project-id>.edn` schema
- [ ] Create `registry/libraries/` and `registry/stars/` directories with `.gitkeep`
- [ ] Add 2–3 seed library entries (including bbum itself) to validate the format
- [ ] Write GitHub Actions workflow: validate PR touches only `registry/` files, each `.edn` is parseable, required keys present, git URL reachable
- [ ] Commit scaffold with passing CI

## Phase 2 — Publisher workflow

- [ ] Implement `src/bbum_marketplace/publish.clj` — reads local `bbum.edn`, builds registry EDN map, writes to temp file
- [ ] Implement `bbum marketplace publish` task — calls publish.clj, uses `gh pr create` to open PR against this repo
- [ ] Handle the case where a library entry already exists (offer to update)
- [ ] Add `bbum.edn` to this repo exposing `marketplace:publish` as an installable task
- [ ] Write agent skill `skills/publish/SKILL.md` for zero-friction agent-driven publishing
- [ ] Test: publish a real library (bbum itself), verify PR structure

## Phase 3 — Consumer CLI

- [ ] Implement `src/bbum_marketplace/catalogue.clj` — fetches library listing via GitHub raw API, caches with 1hr TTL in `~/.bbum/marketplace-cache.edn`
- [ ] Implement `bbum marketplace list` — tabular output, supports `--sort stars|name|added` and `--tag <tag>`
- [ ] Implement `bbum marketplace search <query>` — substring match on lib name, description, tags
- [ ] Implement `bbum marketplace info <lib>` — full entry detail + install hint
- [ ] Expose all three as installable bbum tasks in `bbum.edn`
- [ ] Test against live registry

## Phase 4 — Auto-star

- [ ] Implement `src/bbum_marketplace/star.clj` — given a git URL, finds matching registry entry, creates star file, opens PR
- [ ] Implement `bbum marketplace star <lib>` task (manual starring)
- [ ] Document opt-out: `{:marketplace {:auto-star false}}` in `.bbum.edn`
- [ ] Hook: add post-install note to `bbum add` output pointing at `bbum marketplace star`
  (full auto-hook into `bbum add` deferred — requires upstream bbum cooperation)
- [ ] CI in marketplace repo: auto-merge PRs that only add new star files (no library entry modifications)
- [ ] Test: star a library, verify PR, verify CI auto-merge, verify star count updates

## Docs

- [ ] Write `README.md` for this repo: what it is, how to publish, how to consume, how stars work
- [ ] Write `CONTRIBUTING.md`: PR guidelines, CI requirements, maintainer review policy
