---
name: bbum-marketplace
description: >
  Use when the user wants to publish a bbum task library to the marketplace,
  discover/search/browse libraries, install marketplace CLI tasks, or star a library.
  Trigger on: "publish my library", "list marketplace libraries", "search for bbum tasks",
  "add to marketplace", "star a library", "find bbum tasks for X".
lambda: "λop. {publish ∨ discover ∨ star ∨ install} → marketplace(bbum)"
---

# bbum-marketplace skill

λ(op).
  match(op) →
    publish  → λpublish(library)
    discover → λdiscover(query)
    star     → λstar(lib)
    install  → λinstall(consumer_project)

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

Prerequisites: bbum installed (`bbin install io.github.hugoduncan/bbum`).

---

## λ publish(library)

Publish a bbum task library to the marketplace. Run in the library's root directory.

```sh
bb marketplace:publish
```

**What happens:**
1. Reads local `bbum.edn` for `:lib` and optional `:description` / `:tags`
2. Reads git remote to infer the GitHub URL and owner
3. Prompts interactively for any missing fields
4. Opens a PR against `hugoduncan/bbum-marketplace` via `gh` CLI

**Prerequisites:**
- `gh` CLI installed and authenticated (`gh auth login`)
- `bbum.edn` with `:lib` at project root
- git remote `origin` pointing at the library's GitHub repo

**Optional: pre-populate in `bbum.edn`** to skip prompts:
```edn
{:lib         my-org/my-lib
 :description "What this library does"
 :tags        ["tag1" "tag2"]
 :tasks       {...}}
```

**Agent pattern:**
```
user: "publish my library to the marketplace"
→ check bb.edn has marketplace:publish task installed
  → if not: run λ install first
→ run: bb marketplace:publish
→ report PR URL to user
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
→ bb marketplace:search lint
→ present results
→ if user picks one: bb marketplace:info <lib>
→ offer to install: bbum source add + bbum add
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
→ bb marketplace:star hugoduncan/bbum
→ report PR URL (or "already starred")
```

---

## Errors and recovery

| Error | Cause | Fix |
|---|---|---|
| `gh CLI not authenticated` | `gh auth status` fails | `gh auth login` |
| `No bbum.edn found` | Not in a bbum library project | `cd` to the library root |
| `Library already registered` | Entry exists for this slug | Use `--update` (future) or edit via PR |
| `Library not found: X` | Slug/name not in catalogue | Check spelling; try `--refresh` |
| `HTTP 403 / rate limit` | GitHub API cap hit | Set `GITHUB_TOKEN` |
| `Already starred` | Star file exists for this project | No action needed |
