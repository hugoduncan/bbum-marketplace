# Plan

## Approach

Build in four phases, each independently usable:

### Phase 1 — Registry data model and repo scaffold
Define the EDN schema for library entries and the star ledger layout. Scaffold the
`registry/` directory tree with a few seed entries to validate the format. Wire up
GitHub Actions to validate PRs.

### Phase 2 — Publisher workflow (agent + CLI)
Implement `bbum marketplace publish` as a bbum task. It reads the local `bbum.edn`,
generates a registry entry EDN file, and opens a PR via `gh` CLI. An agent-facing
skill wraps this for zero-friction publishing ("ask the agent to publish this library").

### Phase 3 — Consumer CLI (`bbum marketplace list/search/info`)
Fetch the registry catalogue (sparse git clone or raw GitHub API). Display results.
Cache locally with a TTL. Ship as bbum tasks so the extensions are themselves
installable via bbum.

### Phase 4 — Auto-star mechanic
Hook into `bbum add` (monkey-patch or wrapper task) to detect when an installed
library URL matches a registry entry, then open a star PR. Respect the opt-out flag
in `.bbum.edn`. Provide `bbum marketplace star <lib>` for manual starring.

## Key Decisions

- **Registry format: EDN per library file** — one `.edn` per library rather than a
  single monolithic file. Keeps PRs clean (one file touched per publish) and avoids
  merge conflicts in the ledger.
- **Stars as files in a tree** — `registry/stars/<lib-slug>/<project-sha>.edn` —
  each star is a separate file, one PR per star. CI counts and denormalises into the
  library entry on merge. Auditable and conflict-free.
- **Fetch strategy: GitHub raw API** — consumer CLI fetches the directory listing via
  GitHub API and pulls individual files. No server, no cloning required for listing.
- **Extensions ship as a bbum task library** — dogfoods bbum, lowers the install
  friction for consumers to zero (one `bbum add` invocation).

## Risks

- GitHub API rate limits for unauthenticated requests (60/hr). Mitigate by caching
  aggressively and prompting for a token if needed.
- Auto-star PRs could be noisy for high-volume users. The opt-out mechanism must be
  prominent in docs.
- Registry maintainer bottleneck on PR reviews. Mitigate with CI auto-merge for
  purely additive PRs (new star files only, no library entry changes).
