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

Implementation plan written. Ready to start Phase 1.

## Active Task

`munera/open/001-marketplace-for-bbum-task-libraries/` — full scope, 5 phases.

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

## Next Action

Phase 1: create seed registry entries, write GitHub Actions workflows,
write local `validate-registry` dev task. See steps.md.
