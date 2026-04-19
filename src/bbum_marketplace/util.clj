(ns bbum-marketplace.util
  "Shared utilities: slugs, HTTP, EDN, cache, table output, GitHub PR helpers."
  (:require [babashka.fs          :as fs]
            [babashka.http-client :as http]
            [babashka.process     :as proc]
            [cheshire.core        :as json]
            [clojure.edn          :as edn]
            [clojure.pprint       :as pprint]
            [clojure.string       :as str]))

;; ── Slug helpers ─────────────────────────────────────────────────────────────

(defn lib->slug
  "Convert a qualified lib symbol to a registry slug.
   hugoduncan/bbum → \"hugoduncan-bbum\""
  [lib-sym]
  (str/replace (str lib-sym) "/" "-"))

(defn git-url->slug
  "Derive a project slug from a git URL.
   https://github.com/acme/my-lib(.git) → \"acme-my-lib\"
   git@github.com:acme/my-lib.git       → \"acme-my-lib\"
   Falls back to first 12 chars of SHA-256 hex of the URL for non-standard hosts."
  [git-url]
  (let [cleaned (-> git-url
                    (str/replace #"\.git$" "")
                    (str/replace #"^git@([^:]+):" "https://$1/"))
        matcher (re-find #"github\.com[:/]([^/]+)/([^/]+)$" cleaned)]
    (if matcher
      (str/replace (str (second matcher) "-" (nth matcher 2)) #"\.git$" "")
      ;; Fallback: hex digest prefix
      (let [digest (java.security.MessageDigest/getInstance "SHA-256")
            bytes  (.digest digest (.getBytes git-url "UTF-8"))
            hex    (apply str (map #(format "%02x" (bit-and % 0xff)) bytes))]
        (subs hex 0 12)))))

;; ── EDN helpers ──────────────────────────────────────────────────────────────

(defn read-edn-str
  "Parse an EDN string. Supports symbols, keywords, and all standard EDN types."
  [s]
  (edn/read-string s))

(defn write-edn
  "Pretty-print data as EDN to path. Creates parent directories."
  [path data]
  (fs/create-dirs (fs/parent (fs/path path)))
  (spit (str path) (with-out-str (pprint/pprint data))))

;; ── File location helpers ─────────────────────────────────────────────────────

(defn find-file-upward
  "Walk up from cwd looking for filename. Returns absolute path string or nil."
  [filename]
  (loop [dir (fs/file (System/getProperty "user.dir"))]
    (cond
      (nil? dir)
      nil

      (fs/exists? (fs/path (str dir) filename))
      (str (fs/path (str dir) filename))

      :else
      (recur (fs/parent dir)))))

;; ── HTTP ─────────────────────────────────────────────────────────────────────

(defn- auth-headers
  "Return Authorization header map when GITHUB_TOKEN is set."
  []
  (when-let [tok (System/getenv "GITHUB_TOKEN")]
    {"Authorization" (str "Bearer " tok)}))

(defn http-get
  "GET url with optional extra headers map. Returns response body string.
   Throws ex-info on non-2xx responses."
  ([url] (http-get url {}))
  ([url extra-headers]
   (let [resp (http/get url {:headers (merge {"Accept" "application/vnd.github+json"
                                              "User-Agent" "bbum-marketplace/1"}
                                             (auth-headers)
                                             extra-headers)})]
     (if (<= 200 (:status resp) 299)
       (:body resp)
       (throw (ex-info (str "HTTP " (:status resp) " from " url)
                       {:url url :status (:status resp) :body (:body resp)}))))))

(defn http-get-json
  "GET url and parse response body as JSON. Returns keywordized map/vec."
  ([url] (http-get-json url {}))
  ([url extra-headers]
   (let [body (http-get url (merge {"Accept" "application/vnd.github+json"}
                                   extra-headers))]
     (json/parse-string body keyword))))

;; ── Table output ─────────────────────────────────────────────────────────────

(defn print-table
  "Print a fixed-width table to stdout.
   headers — vector of string column headers.
   rows    — seq of vectors matching headers arity."
  [headers rows]
  (let [widths   (reduce (fn [ws row]
                           (mapv (fn [w cell] (max w (count (str cell))))
                                 ws row))
                         (mapv count headers)
                         rows)
        fmt-cell (fn [w cell] (format (str "%-" w "s") (str cell)))
        sep      (fn [w] (apply str (repeat w \─)))]
    (println (str/join "  " (mapv fmt-cell widths headers)))
    (println (str/join "  " (mapv sep widths)))
    (doseq [row rows]
      (println (str/join "  " (mapv fmt-cell widths row))))))

;; ── Cache ─────────────────────────────────────────────────────────────────────

(def ^:private cache-ttl-ms (* 60 60 1000)) ; 1 hour

(defn cache-path
  "Path to the local catalogue cache file."
  []
  (str (System/getProperty "user.home") "/.bbum/marketplace-cache.edn"))

(defn read-cache
  "Read the cached catalogue. Returns nil if the file does not exist,
   cannot be parsed, or is older than 1 hour."
  []
  (let [p (cache-path)]
    (when (fs/exists? p)
      (try
        (let [{:keys [cached-at data]} (edn/read-string (slurp p))
              age (- (System/currentTimeMillis) (or cached-at 0))]
          (when (< age cache-ttl-ms)
            data))
        (catch Exception _ nil)))))

(defn write-cache
  "Write data to the catalogue cache with the current timestamp."
  [data]
  (let [p (cache-path)]
    (fs/create-dirs (fs/parent (fs/path p)))
    (spit p (with-out-str
              (pprint/pprint {:cached-at (System/currentTimeMillis)
                              :data      data})))))

;; ── GitHub PR helpers ─────────────────────────────────────────────────────────

(defn has-push-access?
  "Return true if the authenticated gh user has push access to repo
   (e.g. \"hugoduncan/bbum-marketplace\"). Returns false on any error."
  [repo]
  (try
    (let [{:keys [exit out]}
          (proc/sh "gh" "api" (str "repos/" repo) "--jq" ".permissions.push")]
      (and (zero? exit) (= "true" (str/trim out))))
    (catch Exception _ false)))

(defn clone-for-pr!
  "Clone repo into tmpdir in a way that allows opening a PR.

   - If the user has push access (owner / org member with write): clones the
     upstream directly. Branches pushed here open same-repo PRs.
   - Otherwise: forks then clones. Branches pushed here open cross-fork PRs.

   Returns the absolute path to the cloned repo directory.
   Throws ex-info on clone/fork failure."
  [tmpdir repo]
  (let [repo-name (last (str/split repo #"/"))
        repo-dir  (str tmpdir "/" repo-name)]
    (if (has-push-access? repo)
      ;; ── Direct clone (owner / write access) ───────────────────────────────
      (let [{:keys [exit err]}
            (proc/sh {:dir tmpdir} "gh" "repo" "clone" repo)]
        (when-not (zero? exit)
          (throw (ex-info (str "Failed to clone " repo ": " err) {:repo repo}))))
      ;; ── Fork then clone (external contributor) ────────────────────────────
      (let [{:keys [exit err]}
            (proc/sh {:dir tmpdir} "gh" "repo" "fork" repo "--clone")]
        (when-not (zero? exit)
          (throw (ex-info (str "Failed to fork/clone " repo ": " err) {:repo repo})))))
    repo-dir))
