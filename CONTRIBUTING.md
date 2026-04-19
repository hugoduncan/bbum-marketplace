# Contributing to bbum-marketplace

## PR types

### Library submissions (`registry/libraries/<slug>.edn`)

- Opened by `bb marketplace:publish` or by hand
- Require human maintainer review
- CI validates: EDN parseable, required keys present, slug matches `:lib`, `:git/url` reachable

### Star PRs (`registry/stars/<lib-slug>/<project-slug>.edn`)

- Opened by `bb marketplace:star`
- **Auto-merged** by CI when the PR touches only star files
- No human review required for star-only PRs

---

## Validation rules

### Library entry

| Field | Required | Rule |
|---|---|---|
| `:lib` | yes | Qualified symbol; filename slug must equal `(str/replace (str lib) "/" "-")` |
| `:git/url` | yes | String; reachable via `git ls-remote` in CI |
| `:description` | yes | Non-blank string |
| `:submitted-by` | yes | GitHub username of the submitter |
| `:submitted-at` | yes | ISO date string `YYYY-MM-DD` |
| `:tags` | no | Vector of strings |
| `:stars` | no | Non-negative integer; maintained by CI — omit on submission |

### Star file

| Field | Required | Rule |
|---|---|---|
| `:starred-at` | yes | ISO date string |
| `:project` | encouraged | GitHub repo path e.g. `"my-org/my-project"` |
| `:git/url` | encouraged | Full normalised https URL |

---

## Running validation locally

```sh
bb validate-registry
```

To also verify `:git/url` reachability (same as CI):

```sh
VERIFY_URLS=true bb validate-registry
```

## Running tests

```sh
bb test
```

---

## Manual submission (without `bb marketplace:publish`)

1. Fork this repo
2. Create a branch: `publish/<your-lib-slug>`
3. Add `registry/libraries/<your-lib-slug>.edn` following the schema above
4. Open a PR with title `publish: <lib>`

Do **not** set `:stars` in the submission — CI manages that field.

---

## Maintainer review checklist

For library entry PRs:
- [ ] CI validation passing (EDN valid, required keys, URL reachable)
- [ ] Description is accurate and non-trivial
- [ ] Tags are relevant
- [ ] Library has a `bbum.edn` at its root (verify via info command or direct URL check)
- [ ] Not a duplicate of an existing entry (different slug, different URL)

Star PRs are auto-merged by CI — no checklist needed.

---

## Auto-merge security

The `auto-merge-stars` CI job will only merge a PR if **all** changed files match
the pattern `registry/stars/<slug>/<file>.edn`. Any PR that also touches a library
entry or any other path requires human review. The guard runs after the `validate`
job passes, so malformed star files are rejected before merge.
