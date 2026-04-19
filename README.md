# bbum-marketplace

A central git-native registry and discovery layer for [bbum](https://github.com/hugoduncan/bbum) task libraries.

No server. No database. GitHub Actions and PRs are the only infrastructure.

---

## For library publishers

### Install the marketplace tasks

```sh
bbum source add bbum-marketplace git/url=https://github.com/hugoduncan/bbum-marketplace git/branch=master
bbum add bbum-marketplace marketplace:publish
```

### Publish your library

Run this in your library's root directory (where `bbum.edn` lives):

```sh
bb marketplace:publish
```

The task reads your local `bbum.edn`, prompts for a description and tags if not
already present, and opens a pull request against this repo. A maintainer reviews
and merges it.

**Prerequisites:**
- [`gh` CLI](https://cli.github.com/) installed and authenticated (`gh auth login`)
- A valid `bbum.edn` at your project root with at least `:lib`
- A git remote named `origin` pointing at your library's GitHub repo

**Optional: pre-populate description and tags in `bbum.edn`** to skip interactive
prompts:

```edn
{:lib       my-org/my-task-lib
 :description "A set of linting tasks for Babashka projects"
 :tags        ["lint" "babashka" "ci"]
 :tasks       {...}}
```

---

## For consumers

### Install the marketplace tasks

```sh
bbum source add bbum-marketplace git/url=https://github.com/hugoduncan/bbum-marketplace git/branch=master
bbum add bbum-marketplace marketplace:list marketplace:search marketplace:info marketplace:star
```

### Browse the registry

```sh
# List all libraries, sorted by stars
bb marketplace:list

# Sort by most recently added
bb marketplace:list --sort added

# Filter by tag
bb marketplace:list --tag lint

# Search by keyword
bb marketplace:search lint

# Full detail for a library
bb marketplace:info hugoduncan/bbum
bb marketplace:info hugoduncan-bbum   # slug works too
```

### Force-refresh the local cache

The catalogue is cached for 1 hour at `~/.bbum/marketplace-cache.edn`.
To force a refresh:

```sh
bb marketplace:list --refresh
bb marketplace:search lint --refresh
```

### GitHub API rate limits

Unauthenticated GitHub API requests are limited to 60/hour. If you hit rate
limits, set the `GITHUB_TOKEN` environment variable to a personal access token:

```sh
export GITHUB_TOKEN=ghp_yourtoken
bb marketplace:list
```

---

## Starring libraries

Stars are the primary ranking signal. They live in this repo as small EDN files
under `registry/stars/`. Every star is an auditable git commit — no black boxes.

### Star a library manually

```sh
bb marketplace:star hugoduncan/bbum
```

This opens a PR adding a star file from your project. The PR is auto-merged by CI
once `validate` passes.

### Auto-star on install (coming in a future bbum release)

When bbum gains a post-install hook, libraries will be automatically starred when
you run `bbum add`. You can opt out in your project's `.bbum.edn`:

```edn
{:marketplace {:auto-star false}}
```

---

## How the registry works

```
registry/
├── libraries/
│   └── <org>-<name>.edn   ← one file per library
└── stars/
    └── <org>-<name>/
        └── <project>.edn  ← one file per star, per library
```

**Library entry schema** (`registry/libraries/<slug>.edn`):

```edn
{:lib          my-org/my-task-lib
 :git/url      "https://github.com/my-org/my-task-lib"
 :description  "Short description"
 :tags         ["lint" "babashka"]
 :stars        0                     ; maintained by CI — do not edit manually
 :submitted-by "github-username"
 :submitted-at "2026-04-19"}
```

**Star file schema** (`registry/stars/<lib-slug>/<project-slug>.edn`):

```edn
{:project    "my-org/my-project"
 :git/url    "https://github.com/my-org/my-project"
 :starred-at "2026-04-19"}
```

### CI workflows

| Workflow | Trigger | Purpose |
|---|---|---|
| `registry-pr.yml` | PR touching `registry/**` | Validate entries; auto-merge star-only PRs |
| `count-stars.yml` | Push to master | Recount star files; update `:stars` in library entries |

Star-only PRs (touching only `registry/stars/**/*.edn`) are merged automatically
after CI validation passes — no human review required.

---

## This repo is itself a bbum task library

The marketplace CLI tasks (`marketplace:list`, `marketplace:search`, etc.) are
installed via bbum from this repo. Install them once and they live in your project
alongside your other bbum tasks:

```sh
bbum source add bbum-marketplace git/url=https://github.com/hugoduncan/bbum-marketplace git/branch=master
bbum add bbum-marketplace marketplace:list marketplace:search marketplace:info marketplace:publish marketplace:star
```

---

## Related

- [bbum](https://github.com/hugoduncan/bbum) — the task package manager that this
  marketplace extends
