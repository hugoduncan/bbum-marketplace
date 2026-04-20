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

;; ── Star-request stub ─────────────────────────────────────────────────────────

(defn- today []
  (str (java.time.LocalDate/now)))

(defn- build-star-request []
  {:starred-at (today)})

;; ── PR workflow ───────────────────────────────────────────────────────────────

(defn- open-star-pr!
  "Clone the marketplace repo, write a star-request stub, and open a PR.
   CI resolves the opener's GitHub identity and records the real star file.
   Returns the PR URL."
  [lib-slug lib]
  (println "\nCloning marketplace repo (this may take a moment)...")
  (fs/with-temp-dir [tmpdir {:prefix "bbum-mp-star-"}]
    (let [repo-dir     (util/clone-for-pr! (str tmpdir) marketplace-repo)
          branch       (str "star-request/" lib-slug)
          request-path (str "registry/star-requests/" lib-slug ".edn")]

      (fs/create-dirs (fs/path repo-dir "registry" "star-requests"))

      (spit (str repo-dir "/" request-path)
            (with-out-str (pprint/pprint (build-star-request))))

      (proc/shell {:dir repo-dir} "git" "checkout" "-b" branch)
      (proc/shell {:dir repo-dir} "git" "add" request-path)
      (proc/shell {:dir repo-dir}
                  "git" "commit" "-m" (str "⭐ star-request: " lib))

      (proc/shell {:dir repo-dir} "git" "push" "origin" branch)

      (let [{:keys [exit out err]}
            (proc/sh {:dir repo-dir}
                     "gh" "pr" "create"
                     "--title" (str "⭐ star-request: " lib)
                     "--body"  (str "Requesting a star for `" lib "`.\n\n"
                                    "CI will verify your identity and record the star.")
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
    (let [entries (cat/catalogue {})
          entry   (find-entry-by-query query entries)]
      (when-not entry
        (println (str "Library not found in catalogue: " query))
        (println "Run 'bb marketplace:list' or 'bb marketplace:search <query>' to find libraries.")
        (System/exit 1))
      (let [lib-slug (util/lib->slug (:lib entry))]
        (println (str "Starring: " (:lib entry)))
        (let [pr-url (open-star-pr! lib-slug (:lib entry))]
          (println (str "\n✓ Star request PR opened: " pr-url))
          (println "CI will verify your identity and record the star — check the PR for status."))))))

(defn star-if-known
  "Auto-star hook: called with the git URL of a just-installed library.
   Looks up the URL in the catalogue; if found and auto-star is not opted out,
   opens a star-request PR. CI records the actual star. Prints a notice on success."
  [git-url]
  (when-not (auto-star-enabled?)
    (println "Auto-star disabled via .bbum.edn {:marketplace {:auto-star false}}")
    (System/exit 0))
  (try
    (let [entries (cat/catalogue {})
          entry   (find-entry-by-url git-url entries)]
      (when entry
        (let [lib-slug (util/lib->slug (:lib entry))
              pr-url   (open-star-pr! lib-slug (:lib entry))]
          (println (str "⭐ Star requested for " (:lib entry) " — PR: " pr-url)))))
    (catch Exception e
      ;; Auto-star is best-effort; never crash the caller
      (binding [*out* *err*]
        (println (str "auto-star: " (.getMessage e)))))))
