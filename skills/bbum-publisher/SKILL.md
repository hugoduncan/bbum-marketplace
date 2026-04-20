---
name: bbum-publisher
description: >
  Use when the user wants to publish a bbum task library to the marketplace.
  Trigger on: "publish my library", "add to marketplace", "submit library",
  "register my bbum library", "list my library on the marketplace".
lambda: "λop. publish → marketplace(bbum, publisher)"
metadata:
  tags: ["bbum", "marketplace", "publisher", "publish"]
---

# bbum-publisher skill

λ(op).
  publish → λpublish(library)

---

## Prerequisites

**Install marketplace tasks first** — see the `bbum-consumer` skill (`skills/bbum-consumer/SKILL.md`)
for the `λinstall` step. The `marketplace:publish` task must be present in `bb.edn`
before running any publish command.

**Publishing also requires:**
- `gh` CLI installed and authenticated (`gh auth login`)
- `bbum.edn` with `:lib` at the project root
- git remote `origin` pointing at the library's GitHub repo

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

**Optional: pre-populate `bbum.edn`** to skip prompts:
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
  → if not: follow bbum-consumer λinstall, then return here
→ check bbum.edn has :lib
  → if not: prompt user for lib name and add to bbum.edn
→ run: bb marketplace:publish
→ report PR URL to user
```

---

## Errors and recovery

| Error | Cause | Fix |
|---|---|---|
| `gh CLI not authenticated` | `gh auth status` fails | `gh auth login` |
| `No bbum.edn found` | Not in a bbum library project | `cd` to the library root |
| `Library already registered` | Entry exists for this slug | Use `--update` (future) or edit via PR |
| `HTTP 403 / rate limit` | GitHub API cap hit | Set `GITHUB_TOKEN` |
