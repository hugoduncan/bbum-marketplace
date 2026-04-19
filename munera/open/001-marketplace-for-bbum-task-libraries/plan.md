# Plan

## Context: how bbum CLI extensions work

bbum extensions are Babashka namespaces shipped as a bbum task library from this
repo. A consumer installs them once:

```sh
bbum source add bbum-marketplace git/url=https://github.com/hugoduncan/bbum-marketplace git/branch=master
bbum add bbum-marketplace marketplace:list marketplace:search marketplace:info marketplace:publish marketplace:star
```

bbum copies the implementation files into `.bbum/lib/` in the consumer project and
splices the task entries into their `bb.edn`. After that, `bb marketplace:list` etc.
work like any other Babashka task. The marketplace dogfoods bbum — no separate
install step.

The task entry format in `bbum.edn` mirrors all other bbum task libraries:

```edn
{:lib hugoduncan/bbum-marketplace
 :tasks
 {:marketplace:list
  {:doc   "List available bbum task libraries"
   :files ["src/bbum_marketplace/list.clj"
           "src/bbum_marketplace/catalogue.clj"
           "src/bbum_marketplace/util.clj"]
   :depends [:marketplace:catalogue]
   :task  {:doc      "List available bbum task libraries"
           :requires ([bbum-marketplace.list :as mp-list])
           :task     (mp-list/run)}}
  :marketplace:catalogue
  {:doc   "Internal: fetch and cache the registry catalogue"
   :files ["src/bbum_marketplace/catalogue.clj"
           "src/bbum_marketplace/util.clj"]}}}
```

---

## Repo file tree

```
bbum-marketplace/
├── bbum.edn                          ; library manifest — exposes marketplace tasks
├── bb.edn                            ; dev tasks: test, validate-registry, etc.
├── deps.edn                          ; Clojure deps for dev/test
├── README.md
├── CONTRIBUTING.md
│
├── registry/
│   ├── libraries/
│   │   ├── hugoduncan-bbum.edn       ; seed entry
│   │   └── hugoduncan-bbum-marketplace.edn
│   └── stars/
│       └── hugoduncan-bbum/
│           └── .gitkeep
│
├── src/
│   └── bbum_marketplace/
│       ├── util.clj                  ; shared: HTTP, cache, slug utils
│       ├── catalogue.clj             ; fetch + cache registry catalogue
│       ├── list.clj                  ; `bb marketplace:list`
│       ├── search.clj                ; `bb marketplace:search`
│       ├── info.clj                  ; `bb marketplace:info`
│       ├── publish.clj               ; `bb marketplace:publish`
│       └── star.clj                  ; `bb marketplace:star`
│
├── test/
│   └── bbum_marketplace/
│       ├── util_test.clj
│       ├── catalogue_test.clj
│       ├── publish_test.clj
│       └── star_test.clj
│
├── skills/
│   └── bbum-marketplace/
│       └── SKILL.md                  ; agent skill for publish + discover
│
└── .github/
    └── workflows/
        ├── validate-pr.yml           ; validate registry entries on every PR
        ├── auto-merge-stars.yml      ; auto-merge PRs that only add star files
        └── count-stars.yml           ; recount + update :stars on merge to master
```

---

## Data schemas

### Library entry — `registry/libraries/<lib-slug>.edn`

Slug is the lib name with `/` replaced by `-` (e.g. `hugoduncan/bbum` →
`hugoduncan-bbum`).

```edn
{:lib          hugoduncan/bbum
 :git/url      "https://github.com/hugoduncan/bbum"
 :description  "A task package manager for babashka"
 :tags         ["package-manager" "babashka" "tasks"]
 :stars        0
 :submitted-by "hugoduncan"
 :submitted-at "2026-04-19"}
```

Required keys: `:lib`, `:git/url`, `:description`, `:submitted-by`, `:submitted-at`.
Optional: `:tags`, `:stars` (managed by CI — defaulted to 0 on submission, never
edited by humans after that).

`:lib` must be a symbol matching the `:lib` field in the library's `bbum.edn`.
The slug in the filename must equal `(str/replace (str lib) "/" "-")`.

### Star file — `registry/stars/<lib-slug>/<project-slug>.edn`

Project slug: the GitHub repo path with `/` replaced by `-` (or a SHA256 of the
git URL for non-GitHub hosts, truncated to 12 chars).

```edn
{:project    "hugoduncan/bbum-marketplace"
 :git/url    "https://github.com/hugoduncan/bbum-marketplace"
 :starred-at "2026-04-19"}
```

Required: `:project`, `:starred-at`. `:git/url` encouraged but not required.

---

## Namespace decomposition

### `bbum-marketplace.util`

```clojure
(defn lib->slug [lib-sym])        ; hugoduncan/bbum → "hugoduncan-bbum"
(defn project->slug [git-url])    ; github.com/a/b  → "a-b" (or sha12 fallback)
(defn http-get [url opts])        ; babashka.http-client wrapper, throws on non-2xx
(defn read-edn-str [s])           ; edn/read-string with symbol-key support
(defn cache-path [])              ; ~/.bbum/marketplace-cache.edn
(defn read-cache [])              ; returns nil if absent or expired (>1hr)
(defn write-cache [data])
```

### `bbum-marketplace.catalogue`

```clojure
(defn fetch-catalogue [])
;; GETs https://api.github.com/repos/hugoduncan/bbum-marketplace/contents/registry/libraries
;; parses the listing, fetches each .edn file (raw GitHub), parses, returns vec of entry maps
;; respects Authorization header if GITHUB_TOKEN env is set

(defn catalogue [])
;; reads cache; if nil, calls fetch-catalogue, writes cache, returns result
;; sorted by :lib for stable output
```

### `bbum-marketplace.list`

```clojure
(defn run [])
;; parses *command-line-args* for --sort and --tag flags
;; calls (catalogue/catalogue), applies sort + filter, prints table via print-table
;; columns: lib, description, tags, stars, url
```

Sorting: `:stars` descending (default), `:name` ascending, `:added` (`:submitted-at`
descending).

### `bbum-marketplace.search`

```clojure
(defn run [])
;; first positional arg is the query string
;; filters catalogue: substring match (case-insensitive) against :lib, :description, :tags
;; prints same table as list
```

### `bbum-marketplace.info`

```clojure
(defn run [])
;; first positional arg is lib name or slug
;; finds entry in catalogue, prints full detail + install hint:
;;   bbum source add <slug> git/url=<url> git/branch=master
;;   bbum add <slug> <task>   (lists available tasks if bbum.edn is fetchable)
```

The info command does an additional live fetch of the library's `bbum.edn` to list
available tasks. It uses `bbum.source/with-source-dir` logic reimplemented for bb
(sparse git fetch of bbum.edn only via GitHub raw API when the URL is GitHub).

### `bbum-marketplace.publish`

```clojure
(defn run [])
;; 1. Read local bbum.edn (walks up from cwd, same logic as bbum.config/project-root)
;; 2. Read .bbum.edn for submitted-by (use git remote URL's owner as fallback)
;; 3. Derive: slug, description (prompt if absent), tags (prompt if absent)
;; 4. Check if registry/libraries/<slug>.edn already exists in remote (GitHub API HEAD)
;;    - if yes: offer to update (re-fetch, merge fields, preserve :stars)
;;    - if no: create new entry
;; 5. Write entry to a temp file
;; 6. Use `gh pr create` to open a PR against hugoduncan/bbum-marketplace:
;;    - creates a branch named publish/<slug>-<date>
;;    - commits the entry file
;;    - opens PR with title "publish: <lib>" and body from entry
;; 7. Print the PR URL
```

Prerequisites checked at startup: `gh` CLI present and authenticated.

### `bbum-marketplace.star`

```clojure
(defn run [])
;; first positional arg is lib name or slug
;; 1. Resolve to a catalogue entry (fetch catalogue, find by name/slug)
;; 2. Derive project-slug from current project's git remote URL
;; 3. Check if star file already exists (GitHub API HEAD) — no-op if so
;; 4. Write star file to temp dir
;; 5. Use `gh pr create` to open a PR:
;;    - branch: star/<lib-slug>-<project-slug>
;;    - commit: the star file only
;;    - PR title: "⭐ star: <lib> from <project>"
;; 6. Print PR URL (or "already starred" message)

(defn star-if-known [git-url])
;; called from a post-add hook (future integration point with bbum)
;; looks up git-url in catalogue; if found, calls run logic silently
;; respects {:marketplace {:auto-star false}} in .bbum.edn
;; prints one-line notice if it opens a PR; fully silent on no-match
```

---

## GitHub Actions workflows

### `validate-pr.yml`

Trigger: `pull_request` affecting `registry/**`.

Steps:
1. Checkout PR branch.
2. Find all changed `.edn` files under `registry/libraries/`.
3. For each: parse EDN (bb one-liner), check required keys present, check
   `:lib` sym matches filename slug, check `:git/url` is reachable (shallow
   `git ls-remote`), check `:stars` is not present or is a non-negative integer.
4. For star files: check required keys, check parent dir matches a known library
   slug (i.e. `registry/libraries/<slug>.edn` exists).
5. Fail on any error with a clear message. Pass otherwise.

### `auto-merge-stars.yml`

Trigger: `pull_request` — `labeled` or `opened` — targeting master.

Guard: all changed files must be under `registry/stars/` — no other paths touched.

Steps:
1. Verify the guard (fail with message if library entries were also touched).
2. If guard passes: approve and merge via `gh pr merge --squash --auto`.

This keeps star intake zero-maintenance for the maintainer.

### `count-stars.yml`

Trigger: `push` to master (i.e. after any PR merges).

Steps:
1. For each `registry/libraries/<slug>.edn`:
   a. Count files in `registry/stars/<slug>/` (excluding `.gitkeep`).
   b. Read current `:stars` value.
   c. If count differs: update the file with new `:stars` value.
2. If any files changed: commit as `🔄 update star counts` and push.

This keeps `:stars` accurate without requiring PR authors to set it.

---

## bbum.edn — task declarations

```edn
{:lib hugoduncan/bbum-marketplace
 :tasks
 {:marketplace:catalogue
  {:doc   "Internal: shared catalogue fetch/cache (not directly invocable)"
   :files ["src/bbum_marketplace/util.clj"
           "src/bbum_marketplace/catalogue.clj"]}

  :marketplace:list
  {:doc     "List available bbum task libraries"
   :files   ["src/bbum_marketplace/list.clj"]
   :depends [:marketplace:catalogue]
   :task    {:doc      "List available bbum task libraries [--sort stars|name|added] [--tag TAG]"
             :requires ([bbum-marketplace.list :as mp-list])
             :task     (mp-list/run)}}

  :marketplace:search
  {:doc     "Search bbum task libraries by name, description, or tag"
   :files   ["src/bbum_marketplace/search.clj"]
   :depends [:marketplace:catalogue]
   :task    {:doc      "Search bbum task libraries: bb marketplace:search <query>"
             :requires ([bbum-marketplace.search :as mp-search])
             :task     (mp-search/run)}}

  :marketplace:info
  {:doc     "Show full detail for a bbum task library"
   :files   ["src/bbum_marketplace/info.clj"]
   :depends [:marketplace:catalogue]
   :task    {:doc      "Show library detail: bb marketplace:info <lib>"
             :requires ([bbum-marketplace.info :as mp-info])
             :task     (mp-info/run)}}

  :marketplace:publish
  {:doc   "Publish this library to the bbum marketplace (opens a PR)"
   :files ["src/bbum_marketplace/publish.clj"]
   :depends [:marketplace:catalogue]
   :task  {:doc      "Publish this library to the marketplace"
           :requires ([bbum-marketplace.publish :as mp-pub])
           :task     (mp-pub/run)}}

  :marketplace:star
  {:doc   "Star a library in the bbum marketplace (opens a PR)"
   :files ["src/bbum_marketplace/star.clj"]
   :depends [:marketplace:catalogue]
   :task  {:doc      "Star a library: bb marketplace:star <lib>"
           :requires ([bbum-marketplace.star :as mp-star])
           :task     (mp-star/run)}}}}
```

---

## bb.edn — dev tasks

```edn
{:paths ["src" "test"]
 :deps  {org.babashka/http-client {:mvn/version "0.4.19"}}
 :tasks
 {test
  {:doc  "Run all tests"
   :task (do (require '[clojure.test :as t]
                       'bbum-marketplace.util-test
                       'bbum-marketplace.catalogue-test
                       'bbum-marketplace.publish-test
                       'bbum-marketplace.star-test)
             (let [r (t/run-all-tests #"bbum-marketplace\..*-test")]
               (System/exit (if (pos? (+ (:fail r) (:error r))) 1 0))))}

  validate-registry
  {:doc  "Validate all registry entries locally (same logic as CI)"
   :task (do (require '[bbum-marketplace.validate :as v])
             (v/run))}}}
```

---

## Sequencing and dependencies

```
Phase 1: registry scaffold + CI
  → registry data schema (concrete .edn examples)
  → seed entries
  → validate-pr.yml + count-stars.yml + auto-merge-stars.yml
  → local validate-registry dev task

Phase 2: bbum.edn + publisher
  depends on: Phase 1 (slug format, entry schema)
  → util.clj (slug, HTTP, cache stubs)
  → publish.clj (reads local bbum.edn, gh pr create)
  → bbum.edn (marketplace:publish task)
  → smoke test: publish this repo to itself

Phase 3: consumer CLI
  depends on: Phase 1 (live registry entries to query)
  → catalogue.clj (GitHub API fetch, local cache)
  → list.clj, search.clj, info.clj
  → bbum.edn (remaining tasks)
  → smoke test: list/search/info against live registry

Phase 4: auto-star
  depends on: Phase 3 (catalogue.clj for URL lookup)
  → star.clj
  → auto-merge-stars.yml (CI)
  → count-stars.yml (CI)
  → document opt-out
  → smoke test: star this repo, verify auto-merge, verify count update

Phase 5: docs + skill
  → README.md
  → CONTRIBUTING.md
  → skills/bbum-marketplace/SKILL.md
```

---

## Key risks and mitigations

**GitHub API rate limits (60 req/hr unauthenticated)**
Cache aggressively (1hr TTL). Check for `GITHUB_TOKEN` / `GITHUB_MARKETPLACE_TOKEN`
env var and pass as `Authorization: Bearer` header when present. Print a warning
if a 403/429 is returned pointing at the token env var.

**Star PR noise for heavy users**
`star-if-known` is post-`bbum add` and best-effort. The auto-star feature is
not implemented in Phase 1–3 at all; the manual `marketplace:star` command
ships first. The opt-out config key is documented from day one.

**auto-merge-stars.yml security**
Auto-merge must only trigger for PRs that exclusively add files under
`registry/stars/`. The guard checks `git diff --name-only origin/master` and
aborts if any path outside `registry/stars/` is present. PRs that touch a
library entry require human review regardless of other contents.

**Slug collisions**
Two libraries with different orgs but the same repo name (e.g.
`acme/lint` and `widgets/lint`) would both slug to `acme-lint` / `widgets-lint` —
no collision. The slug is always `<org>-<name>`. The validation CI enforces
that the `:lib` symbol matches the slug, preventing misfiles.
