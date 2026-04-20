# bbum-marketplace — Working Memory

## What this project is

A central git-based registry and discovery layer for bbum task libraries. No server,
no database — GitHub Actions and PRs are the only infrastructure.

GitHub: https://github.com/hugoduncan/bbum-marketplace
Remote: git@github.com:hugoduncan/bbum-marketplace.git

Related project: `~/projects/hugoduncan/bbum/bbum-master/` — the bbum package manager
that this marketplace extends.

**Note:** the repo uses a git worktree layout — the bare `.git` dir lives at
`~/projects/hugoduncan/bbum-marketplace/.git` and the working tree is at
`~/projects/hugoduncan/bbum-marketplace/bbum-marketplace-master/`. Push from the
parent dir (`gh` and raw `git push` both work from either once the remote is set).

## Current Status

Core marketplace complete. Star integrity hardening implemented.

- 34 tests, 150 assertions — all passing
- PR #1 merged: `hugoduncan/bb-task-lib` now in registry
- CI: validate (structure + URL reachability), count-stars, record-star (replaces auto-merge-stars)
- 3 libraries in registry: hugoduncan/bbum, hugoduncan/bbum-marketplace, hugoduncan/bb-task-lib
- Star flow: client sends stub to `registry/star-requests/<lib>.edn`; CI writes
  `registry/stars/<lib>/<github-actor>.edn` directly to master — one star per GitHub user, enforced server-side

## Open Tasks

- `munera/open/001-marketplace-for-bbum-task-libraries/` — all code phases done; pending live smoke tests before close
- `munera/open/002-one-star-per-github-user/` — **implemented**; pending close

**plan.md** is now detailed — covers: exact file tree, concrete EDN schemas,
namespace/function decompositions for all 6 Clojure namespaces, GitHub Actions
workflow logic (validate-pr, count-stars, auto-merge-stars), bbum.edn task
declarations, bb.edn dev tasks, phase sequencing.

## Key Architecture Decisions

- Extensions are Babashka namespaces, installed via `bbum add` from this repo
  (dogfoods bbum; no separate install step for consumers)
- Registry: `registry/libraries/<slug>.edn` (one per lib) + `registry/stars/<slug>/<project-slug>.edn`
- Slug = lib name with `/` → `-` (e.g. `hugoduncan/bbum` → `hugoduncan-bbum`)
- GitHub raw API for catalogue fetches; 1hr TTL cache at `~/.bbum/marketplace-cache.edn`
- Stars auto-merged by CI if PR only touches `registry/stars/` (no human review needed)
- `count-stars.yml` fires on every push to master, keeps `:stars` accurate
- Auto-star is opt-out: `{:marketplace {:auto-star false}}` in project `.bbum.edn`
- Manual `marketplace:star` ships before any auto-hook into `bbum add`

## bbum codebase notes (for extending)

- `bbum.main` dispatches via `case` on first arg — `marketplace` subcommand
  would need to be added there (Phase 4 / future upstream PR)
- `bbum.config` handles all EDN I/O + rewrite-clj for `bb.edn` edits
- `bbum.source` handles coord resolution + git cloning
- `bbum.print/print-table` is the standard table renderer
- Tests use `bbum.test-helpers` and private var access via `@#'`

## Source files

```
src/bbum_marketplace/
  util.clj       — slugs, HTTP, cache, print-table
  validate.clj   — registry entry validator (local + CI)
  count_stars.clj — star recounting (run by CI on push to master)
  catalogue.clj  — GitHub API fetch + cache
  list.clj       — bb marketplace:list
  search.clj     — bb marketplace:search
  info.clj       — bb marketplace:info
  publish.clj    — bb marketplace:publish (opens PR to this repo)
  star.clj       — bb marketplace:star + star-if-known auto-hook
```

## Next Action

Run live smoke tests:
1. `bb marketplace:publish` from a real library project → verify PR structure + CI pass
2. `bb marketplace:list` → verify live catalogue fetch
3. `bb marketplace:star hugoduncan/bbum` → verify star PR auto-merged by CI
4. Verify `count-stars.yml` fires and updates `:stars` in library entry

Then close the munera task.
