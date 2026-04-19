🔁 In Clojure ->> threading, the accumulated value is passed as the LAST argument.
Helper functions designed for ->> pipelines must have "config" params first
and the collection/data param last.

Wrong:  (defn filter-by-tag [entries tag] ...)
        (->> entries (filter-by-tag tag))  ; calls (filter-by-tag tag entries)
        ; entries=tag, tag=entries — SILENT if tag=nil (returns nil)

Right:  (defn filter-by-tag [tag entries] ...)
        (->> entries (filter-by-tag tag))  ; calls (filter-by-tag tag entries) ✓

Same for sort-entries, transform-x, filter-x, etc.

The failure mode when tag=nil is especially insidious: (nil? nil)=true returns nil,
which propagates as "empty collection" rather than throwing — hard to notice.

Also: with-redefs on cross-namespace vars doesn't reliably work in Babashka SCI.
SCI may resolve alias→fn at namespace load time, bypassing the var. Fix: inject
the dependency as a function argument instead of relying on var indirection.
