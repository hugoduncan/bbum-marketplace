🔁 In bbum task libraries, list shared utility files directly in the :files of
every task that needs them. Do NOT use a separate internal task just to hold
the shared file and reference it via :depends.

Reason: `bbum update <task>` only re-copies files declared in that specific
task's :files. If the shared file lives only in an implicit dependency task,
updating the user-facing task leaves the shared file stale — causing
"Unable to resolve symbol: util/new-fn" errors.

bbum deduplicates on install so listing the same file in multiple tasks is
safe — it's copied once. But each declaring task can trigger a re-copy on
update, which is exactly what you want.

Backward-compat note: keep old implicit tasks as stubs in bbum.edn so that
existing installs don't error on `bbum update`. Just remove :depends from
the user-facing tasks.
