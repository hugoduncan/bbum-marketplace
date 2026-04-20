---
name: bbum-consumer
description: >
  Use when the user wants to discover, browse, search, or install bbum task libraries
  from the marketplace, or star a library to signal adoption.
  Trigger on: "find a bbum library", "search marketplace", "list marketplace libraries",
  "install from marketplace", "star a library", "find bbum tasks for X",
  "what task libraries are available".
lambda: "λop. {install ∨ discover ∨ star} → marketplace(bbum, consumer)"
metadata:
  tags: ["bbum", "marketplace", "consumer", "discover", "install", "star"]
---

# bbum-consumer skill

λ(op).
  match(op) →
    install  → λinstall(project)
    discover → λdiscover(query)
    star     → λstar(lib)

---

## λ install(project)

Install the marketplace tasks into a project that already uses bbum.

```sh
bbum source add bbum-marketplace \
  git/url=https://github.com/hugoduncan/bbum-marketplace \
  git/branch=master

# Install all marketplace commands at once
bbum add bbum-marketplace \
  marketplace:list \
  marketplace:search \
  marketplace:info \
  marketplace:publish \
  marketplace:star
```

**Prerequisites:** bbum installed (`bbin install io.github.hugoduncan/bbum`).

**Agent pattern:**
```
user: "I want to use the marketplace"
→ check bb.edn has marketplace:list task installed
  → if not: run the install commands above
→ proceed with discover or star
```

---

## λ discover(query)

Browse and find bbum task libraries.

```sh
# List all, sorted by stars (default)
bb marketplace:list

# Sort options: stars (default) | name | added
bb marketplace:list --sort name
bb marketplace:list --sort added

# Filter by tag
bb marketplace:list --tag lint

# Search by keyword (lib name, description, tags)
bb marketplace:search lint
bb marketplace:search "code quality"

# Full detail for a specific library
bb marketplace:info hugoduncan/bbum
bb marketplace:info hugoduncan-bbum  # slug also works
```

**Rate limits:** GitHub API is limited to 60 req/hr unauthenticated.
Set `GITHUB_TOKEN` env var to raise the limit.

**Cache:** Results are cached for 1 hour at `~/.bbum/marketplace-cache.edn`.
Use `--refresh` to bypass: `bb marketplace:list --refresh`

**Agent pattern:**
```
user: "find me a linting task library for babashka"
→ check marketplace tasks installed → if not: λinstall first
→ bb marketplace:search lint
→ present results
→ if user picks one: bb marketplace:info <lib>
→ offer to add: bbum source add + bbum add
```

---

## λ star(lib)

Star a library to signal adoption and improve its ranking.

```sh
bb marketplace:star hugoduncan/bbum
bb marketplace:star hugoduncan-bbum  # slug also works
```

**What happens:**
1. Finds the library in the catalogue
2. Derives a project slug from the current project's git remote
3. Checks if already starred — no-op if so
4. Opens a PR adding a star file — auto-merged by CI

**Prerequisites:** `gh` CLI installed and authenticated.

**Opt-out:** Add to project `.bbum.edn`:
```edn
{:marketplace {:auto-star false}}
```

**Agent pattern:**
```
user: "star the bbum library"
→ check marketplace tasks installed → if not: λinstall first
→ bb marketplace:star hugoduncan/bbum
→ report PR URL (or "already starred")
```

---

## Errors and recovery

| Error | Cause | Fix |
|---|---|---|
| `gh CLI not authenticated` | `gh auth status` fails | `gh auth login` |
| `Library not found: X` | Slug/name not in catalogue | Check spelling; try `--refresh` |
| `HTTP 403 / rate limit` | GitHub API cap hit | Set `GITHUB_TOKEN` |
| `Already starred` | Star file exists for this project | No action needed |
