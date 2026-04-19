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

;; ── Entry existence check ─────────────────────────────────────────────────────

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

(defn- open-pr!
  "Clone the marketplace repo to a temp dir, write the entry, and open a PR.
   Handles both the owner (direct clone) and contributor (fork) cases.
   Returns the PR URL."
  [entry slug]
  (println (str "\nCloning marketplace repo (this may take a moment)..."))
  (fs/with-temp-dir [tmpdir {:prefix "bbum-mp-pub-"}]
    (let [repo-dir   (util/clone-for-pr! (str tmpdir) marketplace-repo)
          branch     (str "publish/" slug "-" (today))
          entry-file (str "registry/libraries/" slug ".edn")]

      (proc/shell {:dir repo-dir} "git" "checkout" "-b" branch)

      (spit (str repo-dir "/" entry-file)
            (with-out-str (pprint/pprint entry)))

      (proc/shell {:dir repo-dir} "git" "add" entry-file)
      (proc/shell {:dir repo-dir}
                  "git" "commit" "-m" (str "publish: " (:lib entry)))

      (proc/shell {:dir repo-dir} "git" "push" "origin" branch)

      (let [{:keys [exit out err]}
            (proc/sh {:dir repo-dir}
                     "gh" "pr" "create"
                     "--title" (str "publish: " (:lib entry))
                     "--body"  (pr-body entry)
                     "--repo"  marketplace-repo)]
        (if (zero? exit)
          (str/trim out)
          (throw (ex-info (str "Failed to open PR: " err) {})))))))

;; ── Entry point ───────────────────────────────────────────────────────────────

(defn run []
  (check-gh!)

  (let [{:keys [manifest]} (read-local-bbum-edn!)
        lib-sym     (:lib manifest)
        slug        (util/lib->slug lib-sym)
        raw-url     (git-remote-url)
        git-url     (normalise-git-url raw-url)
        owner       (extract-github-owner git-url)]

    (println (str "Publishing library: " lib-sym))
    (println (str "Registry slug:      " slug))

    ;; Check if entry already exists
    (let [existence (entry-exists? slug)]
      (when (= :exists existence)
        (throw (ex-info
                (str "Library \"" slug "\" is already registered in the marketplace.\n"
                     "To update an existing entry, use: bb marketplace:publish --update")
                {:slug slug})))
      (when (= :unknown existence)
        (println "WARNING: Could not verify entry existence (GitHub API unavailable).")))

    ;; Resolve git URL
    (let [git-url (or git-url
                      (let [u (prompt (str "Git URL of this library's repo"
                                           " (e.g. https://github.com/org/lib): "))]
                        (when (str/blank? u)
                          (throw (ex-info "Git URL is required." {})))
                        u))
          ;; Resolve description
          description (or (when-not (str/blank? (:description manifest))
                            (:description manifest))
                          (let [d (prompt "Short description of this library: ")]
                            (when (str/blank? d)
                              (throw (ex-info "Description is required." {})))
                            d))
          ;; Resolve tags
          tags (or (seq (:tags manifest)) (prompt-tags))

          ;; Resolve submitted-by
          submitted-by (or owner
                           (prompt "Your GitHub username: "))

          entry (build-entry manifest git-url submitted-by description tags)]

      (println "\nEntry to be submitted:")
      (pprint/pprint entry)

      (let [confirm (prompt "\nOpen a pull request? [Y/n]: ")]
        (when (and (not (str/blank? confirm))
                   (not (#{"y" "Y" ""} (str/trim confirm))))
          (println "Aborted.")
          (System/exit 0)))

      (let [pr-url (open-pr! entry slug)]
        (println (str "\n✓ PR opened: " pr-url))))))
