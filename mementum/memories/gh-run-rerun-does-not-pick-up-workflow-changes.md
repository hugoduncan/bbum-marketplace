❌ `gh run rerun` replays the exact workflow snapshot from when the run was
created. It does NOT pick up workflow changes pushed to master after that point.

To get an updated workflow to run on a PR, push a new commit to the PR branch —
this triggers a fresh `pull_request` event which reads the workflow from the
current base branch (master).

Also: `bb -e "(-> file slurp edn/read-string :git/url)"` in a bash $() captures
the Clojure print-repr with surrounding quotes, e.g. `"https://..."`. Avoid using
`bb -e` for string extraction in shell scripts. Prefer calling a Clojure function
that uses `println` internally, or replace the whole step with a proper bb task.
