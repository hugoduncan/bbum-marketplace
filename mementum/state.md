# bbum-marketplace — Working Memory

## What this project is

A central git-based registry and discovery layer for bbum task libraries. No server,
no database — GitHub Actions and PRs are the only infrastructure.

Related project: `~/projects/hugoduncan/bbum/bbum-master/` — the bbum package manager
that this marketplace extends.

## Current Status

Project just created. One task open, nothing implemented yet.

## Active Task

`munera/open/001-marketplace-for-bbum-task-libraries/` — full scope task covering:
- Registry data model (EDN per library, stars as files)
- Publisher agent workflow (`bbum marketplace publish` → opens PR here)
- Consumer CLI (`bbum marketplace list/search/info`)
- Auto-star mechanic (triggered by `bbum add` when URL matches registry entry)
- The extensions ship as a bbum task library installable from this repo

## Key Architecture Decisions (captured early)

- Registry is git-native: `registry/libraries/<slug>.edn` + `registry/stars/<slug>/<project-id>.edn`
- GitHub raw API for catalogue fetches; local cache with 1hr TTL
- Extensions dogfood bbum — installable via `bbum add bbum-marketplace marketplace:list` etc.
- Auto-star is opt-out via `.bbum.edn` `{:marketplace {:auto-star false}}`
- Star PRs can be auto-merged by CI (stars-only PRs require no human review)

## Next Action

Start Phase 1: scaffold `registry/` directory tree, define EDN schemas, seed entries,
wire GitHub Actions validation. See steps.md for the full checklist.
