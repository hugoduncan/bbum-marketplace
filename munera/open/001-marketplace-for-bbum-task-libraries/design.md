# 001 — Marketplace for bbum Task Libraries

## Goal

Build a central marketplace that makes bbum task libraries discoverable, installable,
and organically ranked — without requiring consumers to know a library's git URL in
advance.

## Context

bbum lets consumers install tasks from any git repo or local path via named sources.
The missing layer is **discoverability**: right now you must already know a URL.
The marketplace fills that gap.

bbum itself is at `~/projects/hugoduncan/bbum/bbum-master/`. The marketplace is a
**separate project** (this repo) that acts as the central registry and also ships the
bbum CLI extensions that consumers use to query it.

## Entities

**Registry repo** (`bbum-marketplace`)
A git repository containing a catalogue of library entries and a star ledger. It is
the single source of truth for what libraries exist and how many stars each has.

**Library entry**
One file per library in `registry/libraries/<lib-slug>.edn`. Captures:
- Qualified lib name (e.g. `my-org/my-task-lib`) — mirrors `bbum.edn :lib`
- Git URL and default branch/tag
- Short description and tags
- Star count (maintained by CI)
- Submitter and submission date

**Star**
An endorsement. Stored in `registry/stars/<lib-slug>/` as one file per staring
project (filename = hashed project identity or GitHub repo path). Stars are counted
by CI and written back into the library entry on merge.

**Publisher workflow**
A library author asks the bbum agent to publish. The agent reads the library's
`bbum.edn`, synthesises a `registry/libraries/<lib-slug>.edn` entry, and opens a PR
against the marketplace repo. CI validates the entry (manifest reachable, fields
complete). A human maintainer merges.

**Consumer workflow**
bbum gains a `marketplace` subcommand group:

```
bbum marketplace list [--sort stars|name|added] [--tag <tag>]
bbum marketplace search <query>
bbum marketplace info <lib>
```

These fetch (or cache) the registry catalogue from the marketplace repo and display
results. `bbum source add` is unchanged — the marketplace only helps with discovery,
not with installation mechanics.

**Auto-star**
When a consumer runs `bbum add <source> <task>` and the source coord resolves to a
git URL that matches a known marketplace library, bbum silently opens a PR (or appends
to an existing PR) adding a star file for that project. Stars accumulate via merged
PRs, keeping the ledger auditable and tamper-evident.

## Constraints

- The registry is **git-native** — no database, no server. GitHub Actions is the only
  required infrastructure.
- bbum CLI extensions are written in Babashka (consistent with bbum itself).
- The marketplace extensions ship as a bbum task library installable from this repo,
  so `bbum add bbum-marketplace marketplace:list` etc. works naturally.
- Auto-star PRs are best-effort; consumers can opt out via `.bbum.edn` config
  (`{:marketplace {:auto-star false}}`).
- The registry is the canonical source; GitHub stars on library repos are not used
  (we have no control over them).

## Acceptance Criteria

1. A publisher can run `bbum marketplace publish` (or ask the agent) and have a
   correctly formed PR opened against this repo with no manual JSON/EDN editing.
2. A consumer can run `bbum marketplace list --sort stars` and see a ranked table of
   available libraries fetched from the live registry.
3. A consumer can run `bbum marketplace search lint` and get filtered results.
4. Installing any library whose URL matches a registry entry automatically opens a
   star PR (unless opted out).
5. CI in this repo validates all registry entries on every PR: manifest URL reachable,
   required fields present, slug matches lib name.
6. The marketplace extensions are themselves installable via bbum from this repo.
