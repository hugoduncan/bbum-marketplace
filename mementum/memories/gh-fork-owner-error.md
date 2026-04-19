❌ `gh repo fork <repo> --clone` fails with "A single user account cannot own
both a parent and fork" when the user is the repo owner.

Fix: check push access first via `gh api repos/<repo> --jq '.permissions.push'`.
If true → `gh repo clone <repo>` (direct). If false → `gh repo fork <repo> --clone`.

This pattern is encapsulated in `util/clone-for-pr!` in bbum-marketplace.
Use it anywhere a bbum task needs to open a PR against a repo the user may own.
