(ns bbum-marketplace.publish
  "Implementation of `bb marketplace:publish`.
   Reads the local bbum.edn, builds a registry entry, and opens a PR against
   hugoduncan/bbum-marketplace."
  (:require [babashka.fs           :as fs]
            [babashka.http-client  :as http]
            [babashka.process      :as proc]
            [bbum-marketplace.util :as util]
            [clojure.edn           :as edn]
            [clojure.pprint        :as pprint]
            [clojure.set           :as set]
            [clojure.string        :as str]))

(def ^:private marketplace-repo "hugoduncan/bbum-marketplace")

;; ── Prerequisites ─────────────────────────────────────────────────────────────

(defn- check-gh! []
  (let [{:keys [exit]} (proc/sh "gh" "auth" "status")]
    (when-not (zero? exit)
      (throw (ex-info
              "gh CLI is not installed or not authenticated.\nRun: gh auth login"
              {})))))

;; ── Read local library ────────────────────────────────────────────────────────

(defn- read-local-bbum-edn! []
  (let [path (util/find-file-upward "bbum.edn")]
    (when-not path
      (throw (ex-info "No bbum.edn found in current or any parent directory." {})))
    (let [data (edn/read-string (slurp path))]
      (when-not (:lib data)
        (throw (ex-info (str "bbum.edn at " path " has no :lib field.") {:path path})))
      {:manifest data :path path})))

;; ── Git remote introspection ──────────────────────────────────────────────────

(defn- git-remote-url
  "Return the URL for the 'origin' remote in the current directory, or nil."
  []
  (try
    (let [{:keys [exit out]} (proc/sh "git" "remote" "get-url" "origin")]
      (when (zero? exit) (str/trim out)))
    (catch Exception _ nil)))

(defn- normalise-git-url
  "Convert git@ SSH URLs to https. Strip trailing .git."
  [url]
  (when url
    (-> url
        (str/replace #"^git@([^:]+):" "https://$1/")
        (str/replace #"\.git$" ""))))

(defn- extract-github-owner
  "Given a normalised GitHub https URL, return the owner (org/user) or nil."
  [url]
  (second (re-find #"github\.com/([^/]+)/" (or url ""))))

;; ── Registry entry fetch ─────────────────────────────────────────────────────

(defn- entry-exists?
  "Check via GitHub API whether a library entry for slug already exists.
   Returns :exists, :absent, or :unknown (on auth error or timeout)."
  [slug]
  (let [url (str "https://api.github.com/repos/" marketplace-repo
                 "/contents/registry/libraries/" slug ".edn")]
    (try
      (let [resp (http/get url {:headers {"Accept"     "application/vnd.github+json"
                                          "User-Agent" "bbum-marketplace/1"}})]
        (cond
          (= 200 (:status resp)) :exists
          (= 404 (:status resp)) :absent
          :else                  :unknown))
      (catch Exception _ :unknown))))

(defn- fetch-existing-entry
  "Fetch and parse the current registry entry for slug from GitHub raw content.
   Returns the entry map or nil if not found."
  [slug]
  (let [url (str "https://raw.githubusercontent.com/" marketplace-repo
                 "/master/registry/libraries/" slug ".edn")]
    (try
      (let [resp (http/get url {:headers {"User-Agent" "bbum-marketplace/1"}})]
        (when (= 200 (:status resp))
          (edn/read-string (:body resp))))
      (catch Exception _ nil))))

;; ── Prompting ─────────────────────────────────────────────────────────────────

(defn- prompt
  "Print prompt-str and read a line from stdin."
  [prompt-str]
  (print prompt-str)
  (flush)
  (str/trim (read-line)))

(defn- prompt-tags []
  (let [raw (prompt "Tags (comma-separated, or leave blank): ")]
    (if (str/blank? raw)
      []
      (mapv str/trim (str/split raw #",")))))

;; ── Entry construction ────────────────────────────────────────────────────────

(defn- today []
  (str (java.time.LocalDate/now)))

(defn- summarise-tasks
  "Extract {task-kw {:doc \"...\"}} from bbum.edn :tasks.
   Keeps only :doc so the registry entry stays compact and searchable."
  [manifest]
  (into {}
        (keep (fn [[kw task-def]]
                (when-let [doc (:doc task-def)]
                  [kw {:doc doc}]))
              (:tasks manifest {}))))

(defn- build-entry [manifest git-url submitted-by description tags]
  (let [tasks (summarise-tasks manifest)]
    (cond-> {:lib          (:lib manifest)
             :git/url      git-url
             :description  description
             :tags         tags
             :stars        0
             :submitted-by submitted-by
             :submitted-at (today)}
      (seq tasks) (assoc :tasks tasks))))

;; ── PR workflow ───────────────────────────────────────────────────────────────

;; ── Update merge ─────────────────────────────────────────────────────────────

(defn- merge-entry
  "Merge a freshly-built entry into the existing registry entry.
   Mutable fields (git/url, description, tags, tasks) come from the new entry.
   Immutable/CI-managed fields (stars, submitted-by, submitted-at) are preserved
   from the existing entry."
  [existing new-entry]
  (merge new-entry
         (select-keys existing [:stars :submitted-by :submitted-at])))

(defn- diff-lines
  "Return a human-readable list of what changed between old and new entry."
  [old new-entry]
  (let [check (fn [k label fmt-fn]
                (let [ov (get old k) nv (get new-entry k)]
                  (when (not= ov nv)
                    (str "  " label ": " (fmt-fn ov) " → " (fmt-fn nv)))))]
    (remove nil?
            [(check :description "description" pr-str)
             (check :git/url     "git/url"     identity)
             (check :tags        "tags"         pr-str)
             (when (not= (:tasks old) (:tasks new-entry))
               (let [old-ks  (set (keys (:tasks old)))
                     new-ks  (set (keys (:tasks new-entry)))
                     added   (str/join ", " (map name (sort (clojure.set/difference new-ks old-ks))))
                     removed (str/join ", " (map name (sort (clojure.set/difference old-ks new-ks))))]
                 (str/join "\n"
                           (remove str/blank?
                                   [(when (seq added)   (str "  tasks added:   " added))
                                    (when (seq removed) (str "  tasks removed: " removed))]))))])))

;; ── PR bodies ─────────────────────────────────────────────────────────────────

(defn- pr-body [entry]
  (str/join "\n"
            ["## Library submission"
             ""
             (str "**Library:** `" (:lib entry) "`")
             (str "**URL:** " (:git/url entry))
             (str "**Description:** " (:description entry))
             (when (seq (:tags entry))
               (str "**Tags:** " (str/join ", " (:tags entry))))
             ""
             "---"
             "_Submitted via `bb marketplace:publish`_"]))

(defn- update-pr-body [entry diff]
  (str/join "\n"
            (concat
             ["## Library update"
              ""
              (str "**Library:** `" (:lib entry) "`")
              ""]
             (if (seq diff)
               (cons "**Changes:**" diff)
               ["_(no visible changes — re-publishing entry)_"])
             [""
              "---"
              "_Submitted via `bb marketplace:publish --update`_"])))

(defn- open-pr!
  "Clone the marketplace repo, write entry, commit, push, open a PR.
   branch-prefix — e.g. \"publish\" or \"update\"
   commit-msg    — git commit message
   title         — PR title
   body          — PR body markdown
   Returns the PR URL."
  [entry slug branch-prefix commit-msg title body]
  (println (str "\nCloning marketplace repo (this may take a moment)..."))
  (fs/with-temp-dir [tmpdir {:prefix "bbum-mp-pub-"}]
    (let [repo-dir   (util/clone-for-pr! (str tmpdir) marketplace-repo)
          branch     (str branch-prefix "/" slug "-" (today))
          entry-file (str "registry/libraries/" slug ".edn")]

      (proc/shell {:dir repo-dir} "git" "checkout" "-b" branch)

      (spit (str repo-dir "/" entry-file)
            (with-out-str (pprint/pprint entry)))

      (proc/shell {:dir repo-dir} "git" "add" entry-file)
      (proc/shell {:dir repo-dir} "git" "commit" "-m" commit-msg)
      (proc/shell {:dir repo-dir} "git" "push" "origin" branch)

      (let [{:keys [exit out err]}
            (proc/sh {:dir repo-dir}
                     "gh" "pr" "create"
                     "--title" title
                     "--body"  body
                     "--repo"  marketplace-repo)]
        (if (zero? exit)
          (str/trim out)
          (throw (ex-info (str "Failed to open PR: " err) {})))))))

;; ── Entry point ───────────────────────────────────────────────────────────────

(defn run []
  (check-gh!)

  (let [update?     (boolean (some #{"--update"} *command-line-args*))
        {:keys [manifest]} (read-local-bbum-edn!)
        lib-sym     (:lib manifest)
        slug        (util/lib->slug lib-sym)
        raw-url     (git-remote-url)
        git-url     (normalise-git-url raw-url)
        owner       (extract-github-owner git-url)
        existence   (entry-exists? slug)]

    (println (str (if update? "Updating" "Publishing") " library: " lib-sym))
    (println (str "Registry slug: " slug))

    ;; Guard: must exist for --update, must not exist for fresh publish
    (when (and (= :exists existence) (not update?))
      (println (str "\nLibrary \"" slug "\" is already registered in the marketplace."))
      (println "To update your entry (e.g. add new tasks), run:")
      (println "  bb marketplace:publish --update")
      (System/exit 1))
    (when (and (= :absent existence) update?)
      (println (str "\nNo registry entry found for \"" slug "\"."))
      (println "To publish for the first time, run without --update:")
      (println "  bb marketplace:publish")
      (System/exit 1))
    (when (= :unknown existence)
      (println "WARNING: Could not verify entry existence (GitHub API unavailable)."))

    ;; Resolve shared fields
    (let [git-url (or git-url
                      (let [u (prompt "Git URL of this library's repo (e.g. https://github.com/org/lib): ")]
                        (when (str/blank? u)
                          (throw (ex-info "Git URL is required." {})))
                        u))
          description (or (when-not (str/blank? (:description manifest)) (:description manifest))
                          (let [d (prompt "Short description of this library: ")]
                            (when (str/blank? d) (throw (ex-info "Description is required." {})))
                            d))
          tags         (or (seq (:tags manifest)) (prompt-tags))
          submitted-by (or owner (prompt "Your GitHub username: "))
          new-entry    (build-entry manifest git-url submitted-by description tags)]

      (if update?
        ;; ── Update path ─────────────────────────────────────────────────────
        (let [existing (fetch-existing-entry slug)
              merged   (merge-entry existing new-entry)
              diff     (diff-lines existing merged)]
          (println "\nCurrent entry in registry:")
          (pprint/pprint existing)
          (println "\nUpdated entry:")
          (pprint/pprint merged)
          (if (seq diff)
            (do (println "\nChanges:")
                (doseq [line diff] (println line)))
            (println "\n(no changes detected)"))
          (let [confirm (prompt "\nOpen an update pull request? [Y/n]: ")]
            (when (and (not (str/blank? confirm))
                       (not (#{"y" "Y" ""} (str/trim confirm))))
              (println "Aborted.")
              (System/exit 0)))
          (let [pr-url (open-pr! merged slug
                                 "update"
                                 (str "update: " lib-sym)
                                 (str "update: " lib-sym)
                                 (update-pr-body merged diff))]
            (println (str "\n✓ Update PR opened: " pr-url))))

        ;; ── Publish path ─────────────────────────────────────────────────────
        (do
          (println "\nEntry to be submitted:")
          (pprint/pprint new-entry)
          (let [confirm (prompt "\nOpen a pull request? [Y/n]: ")]
            (when (and (not (str/blank? confirm))
                       (not (#{"y" "Y" ""} (str/trim confirm))))
              (println "Aborted.")
              (System/exit 0)))
          (let [pr-url (open-pr! new-entry slug
                                 "publish"
                                 (str "publish: " lib-sym)
                                 (str "publish: " lib-sym)
                                 (pr-body new-entry))]
            (println (str "\n✓ PR opened: " pr-url))))))))
