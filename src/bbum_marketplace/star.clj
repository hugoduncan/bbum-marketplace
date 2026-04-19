(ns bbum-marketplace.star
  "Implementation of `bb marketplace:star <lib>` and the auto-star hook."
  (:require [babashka.fs           :as fs]
            [babashka.process      :as proc]
            [bbum-marketplace.catalogue :as cat]
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

;; ── Catalogue lookup ──────────────────────────────────────────────────────────

(defn- normalise-url [url]
  (when url
    (-> url
        (str/replace #"^git@([^:]+):" "https://$1/")
        (str/replace #"\.git$" ""))))

(defn- find-entry-by-query
  "Find catalogue entry by lib name (e.g. hugoduncan/bbum) or slug."
  [query entries]
  (let [q (str/lower-case query)]
    (first (filter (fn [e]
                     (or (= q (str/lower-case (str (:lib e))))
                         (= q (str/lower-case (util/lib->slug (:lib e))))))
                   entries))))

(defn- find-entry-by-url
  "Find catalogue entry by matching normalised :git/url."
  [git-url entries]
  (let [target (normalise-url git-url)]
    (first (filter #(= target (normalise-url (:git/url %))) entries))))

;; ── Local project introspection ───────────────────────────────────────────────

(defn- local-git-url []
  (try
    (let [{:keys [exit out]} (proc/sh "git" "remote" "get-url" "origin")]
      (when (zero? exit) (str/trim out)))
    (catch Exception _ nil)))

(defn- local-project-info []
  (let [git-url (normalise-url (local-git-url))
        slug    (when git-url (util/git-url->slug git-url))
        owner   (when git-url
                  (second (re-find #"github\.com/([^/]+)/" git-url)))
        repo    (when git-url
                  (last (str/split git-url #"/")))]
    {:git-url git-url
     :slug    (or slug "unknown-project")
     :project (when (and owner repo) (str owner "/" repo))}))

;; ── Auto-star opt-out check ───────────────────────────────────────────────────

(defn- auto-star-enabled?
  "Return false only when .bbum.edn explicitly sets {:marketplace {:auto-star false}}."
  []
  (let [path (util/find-file-upward ".bbum.edn")]
    (if path
      (try
        (let [config (edn/read-string (slurp path))]
          (not (false? (get-in config [:marketplace :auto-star]))))
        (catch Exception _ true))
      true)))

;; ── Star file existence check ─────────────────────────────────────────────────

(defn- star-exists?
  "Check via GitHub API if a star file already exists. Returns true/false/:unknown."
  [lib-slug project-slug]
  (let [url (str "https://api.github.com/repos/" marketplace-repo
                 "/contents/registry/stars/" lib-slug "/" project-slug ".edn")]
    (try
      (let [resp (babashka.http-client/get
                  url {:headers {"Accept"     "application/vnd.github+json"
                                 "User-Agent" "bbum-marketplace/1"}})]
        (= 200 (:status resp)))
      (catch Exception _ :unknown))))

;; ── Star file content ─────────────────────────────────────────────────────────

(defn- today []
  (str (java.time.LocalDate/now)))

(defn- build-star-entry [{:keys [project git-url]}]
  (cond-> {:starred-at (today)}
    project (assoc :project project)
    git-url (assoc :git/url git-url)))

;; ── PR workflow ───────────────────────────────────────────────────────────────

(defn- open-star-pr!
  "Clone the marketplace repo, write the star file, and open a PR.
   Handles both the owner (direct clone) and contributor (fork) cases.
   Returns the PR URL."
  [lib-slug project-slug star-entry lib]
  (println (str "\nCloning marketplace repo (this may take a moment)..."))
  (fs/with-temp-dir [tmpdir {:prefix "bbum-mp-star-"}]
    (let [repo-dir  (util/clone-for-pr! (str tmpdir) marketplace-repo)
          branch    (str "star/" lib-slug "-" project-slug)
          star-path (str "registry/stars/" lib-slug "/" project-slug ".edn")]

      (fs/create-dirs (fs/path repo-dir "registry" "stars" lib-slug))

      (spit (str repo-dir "/" star-path)
            (with-out-str (pprint/pprint star-entry)))

      (proc/shell {:dir repo-dir} "git" "checkout" "-b" branch)
      (proc/shell {:dir repo-dir} "git" "add" star-path)
      (proc/shell {:dir repo-dir}
                  "git" "commit" "-m" (str "⭐ star: " lib))

      (proc/shell {:dir repo-dir} "git" "push" "origin" branch)

      (let [{:keys [exit out err]}
            (proc/sh {:dir repo-dir}
                     "gh" "pr" "create"
                     "--title" (str "⭐ star: " lib)
                     "--body"  (str "Starring `" lib "` from `"
                                    (or (:project star-entry) "unknown") "`")
                     "--repo"  marketplace-repo)]
        (if (zero? exit)
          (str/trim out)
          (throw (ex-info (str "Failed to open PR: " err) {})))))))

;; ── Entry points ─────────────────────────────────────────────────────────────

(defn run
  "Manual star: `bb marketplace:star <lib>`"
  []
  (check-gh!)
  (let [[query] *command-line-args*]
    (when-not query
      (println "Usage: bb marketplace:star <lib>")
      (System/exit 0))
    (let [entries     (cat/catalogue {})
          entry       (find-entry-by-query query entries)]
      (when-not entry
        (do (println (str "Library not found in catalogue: " query))
            (println "Run 'bb marketplace:list' or 'bb marketplace:search <query>' to find libraries.")
            (System/exit 1)))
      (let [lib-slug                    (util/lib->slug (:lib entry))
            {:keys [slug git-url project]} (local-project-info)
            project-slug                slug]
        (println (str "Starring: " (:lib entry)))
        (println (str "From:     " (or project git-url "unknown")))

        ;; Check already starred
        (let [exists (star-exists? lib-slug project-slug)]
          (when (true? exists)
            (println "\nAlready starred — no action needed.")
            (System/exit 0))
          (when (= :unknown exists)
            (println "WARNING: Could not verify existing star (proceeding anyway).")))

        (let [star-entry (build-star-entry {:project project :git-url git-url})
              pr-url     (open-star-pr! lib-slug project-slug star-entry (:lib entry))]
          (println (str "\n✓ Star PR opened: " pr-url)))))))

(defn star-if-known
  "Auto-star hook: called with the git URL of a just-installed library.
   Looks up the URL in the catalogue; if found and auto-star is not opted out,
   opens a star PR silently. Prints a one-line notice on success."
  [git-url]
  (when-not (auto-star-enabled?)
    (println "Auto-star disabled via .bbum.edn {:marketplace {:auto-star false}}")
    (System/exit 0))
  (try
    (let [entries (cat/catalogue {})
          entry   (find-entry-by-url git-url entries)]
      (when entry
        (let [lib-slug                        (util/lib->slug (:lib entry))
              {:keys [slug git-url project]}  (local-project-info)
              project-slug                    slug
              exists                          (star-exists? lib-slug project-slug)]
          (when-not (true? exists)
            (let [star-entry (build-star-entry {:project project :git-url git-url})
                  pr-url     (open-star-pr! lib-slug project-slug
                                            star-entry (:lib entry))]
              (println (str "⭐ Starred " (:lib entry) " — PR: " pr-url)))))))
    (catch Exception e
      ;; Auto-star is best-effort; never crash the caller
      (binding [*out* *err*]
        (println (str "auto-star: " (.getMessage e)))))))
