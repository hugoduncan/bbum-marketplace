(ns bbum-marketplace.star-record
  "Pure functions for CI star-request validation and star file generation.
   No side effects — all I/O and GitHub API calls are in the workflow shell scripts.")

(defn parse-star-request-path
  "Extract lib-slug from a star-request file path.
   \"registry/star-requests/hugoduncan-bbum.edn\" → {:lib-slug \"hugoduncan-bbum\"}
   Returns nil when the path does not match the expected pattern or is nil."
  [path]
  (when path
    (when-let [[_ slug] (re-matches #"registry/star-requests/([^/]+)\.edn" path)]
      {:lib-slug slug})))

(defn star-file-path
  "Return the canonical star file path for actor within lib-slug.
   (star-file-path \"hugoduncan-bbum\" \"alice\")
   → \"registry/stars/hugoduncan-bbum/alice.edn\""
  [lib-slug actor]
  (str "registry/stars/" lib-slug "/" actor ".edn"))

(defn lib-entry-path
  "Return the library entry path for lib-slug.
   (lib-entry-path \"hugoduncan-bbum\")
   → \"registry/libraries/hugoduncan-bbum.edn\""
  [lib-slug]
  (str "registry/libraries/" lib-slug ".edn"))

(defn build-star-edn
  "Build the EDN string for a star file.
   actor — GitHub username string
   date  — ISO date string (YYYY-MM-DD)
   Returns a well-formed EDN string parseable back to a map."
  [actor date]
  (str "{:github/user " (pr-str actor) "\n"
       " :starred-at  " (pr-str date) "}\n"))

(defn validate-star-request
  "Validate a seq of changed file paths as a star-request PR.
   Checks structural constraints only — does not perform any I/O.
   Returns {:ok {:lib-slug ...}} or {:err message}."
  [changed-files]
  (cond
    (not= 1 (count changed-files))
    {:err (str "Star-request PRs must change exactly one file; got "
               (count changed-files))}

    :else
    (let [path   (first changed-files)
          parsed (parse-star-request-path path)]
      (if parsed
        {:ok parsed}
        {:err (str "File path does not match star-request pattern "
                   "(expected registry/star-requests/<slug>.edn): " path)}))))
